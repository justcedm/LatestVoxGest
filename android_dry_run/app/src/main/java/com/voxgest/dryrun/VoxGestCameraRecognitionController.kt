package com.voxgest.dryrun

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class OneHandCaptureResult(
    val valid: Boolean,
    val appended: Boolean,
    val framesCollected: Int,
    val reason: String
)

class VoxGestCameraRecognitionController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onStatus: (String) -> Unit,
    private val onRecognitionFeedback: (RecognitionFeedback) -> Unit = {},
    private val onSkeletonFrame: (LandmarkFrame?) -> Unit = {},
    private val onAcceptedResult: (RecognitionResult) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val router = RecognitionAutoRouter()
    private val alphabetGate = AlphabetAcceptanceGate()
    private val dynamicGate = DynamicWordAcceptanceGate()
    private val calibrationRecorder = OneHandCalibrationRecorder(appContext)

    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var alphabetClassifier: AlphabetClassifier? = null
    @Volatile private var oneHandRecognizer: VoxGestTfliteRecognizer? = null
    @Volatile private var fullSignRecognizer: VoxGestTfliteRecognizer? = null
    @Volatile private var oneHandProfile: RecognitionProfile? = null
    @Volatile private var fullSignProfile: RecognitionProfile? = null
    @Volatile private var oneHandBuffer: LandmarkSequenceBuffer? = null
    @Volatile private var fullSignBuffer: LandmarkSequenceBuffer? = null
    @Volatile private var lastAnalyzeAtMs: Long = 0L
    @Volatile private var lastStatus: String = ""
    @Volatile private var lastStatusAtMs: Long = 0L
    @Volatile private var lastRoute: RecognitionRoute = RecognitionRoute.NOTHING
    @Volatile private var missingPoseCount: Int = 0
    @Volatile private var missingHandCount: Int = 0
    @Volatile private var lastHandMappingLogAtMs: Long = 0L
    @Volatile private var collectionStartedAtMs: Long = 0L
    @Volatile private var signingStatusStartedAtMs: Long = 0L
    @Volatile private var collectionPausedUntilMs: Long = 0L
    @Volatile private var collectionInvalidStartedAtMs: Long = 0L
    @Volatile private var collectionLostHandFrames: Int = 0
    @Volatile private var slidingWindowFrame: Long = 0L
    @Volatile private var lastSlidingTop1Label: String = ""
    @Volatile private var consecutiveMatchCount: Int = 0
    @Volatile private var previousOneHandFrame: LandmarkFrame? = null
    @Volatile private var statusToken: Long = 0L
    @Volatile private var useBackCamera: Boolean = false
    @Volatile private var calibrationLabel: String = ""
    @Volatile private var calibrationBuffer: LandmarkSequenceBuffer? = null
    @Volatile private var calibrationMissingPoseCount: Int = 0
    @Volatile private var calibrationMissingHandCount: Int = 0

    fun start(previewView: PreviewView) {
        if (!running.compareAndSet(false, true)) return
        postStatus("Starting Camera")
        bindCamera(previewView)
        analyzerExecutor.execute {
            try {
                val loadedOneHandRecognizer = VoxGestTfliteRecognizer(appContext)
                val loadedOneHandProfile = loadedOneHandRecognizer.load(RecognitionProfile.activeRecognitionProfileId(appContext))
                val mirrorCameraFrame = mirrorCameraFrameFor(loadedOneHandProfile)
                val loadedExtractor = MediaPipeLandmarkExtractor(appContext, mirrorCameraFrame)

                fullSignRecognizer = null
                fullSignProfile = null
                fullSignBuffer = null
                oneHandRecognizer = loadedOneHandRecognizer
                oneHandProfile = loadedOneHandProfile
                val runtimeFeatureSize = AndroidLandmarkInputPolicy.runtimeFeatureSize(loadedOneHandProfile)
                oneHandBuffer = LandmarkSequenceBuffer(loadedOneHandProfile.sequenceLength, runtimeFeatureSize)
                calibrationBuffer = LandmarkSequenceBuffer(loadedOneHandProfile.sequenceLength, runtimeFeatureSize)
                extractor = loadedExtractor
                alphabetClassifier = null
                Log.i(TAG, "active_profile=${loadedOneHandProfile.id} automatic alphabet output disabled; fullsign output disabled")
                Log.i(TAG, "landmark_policy ${AndroidLandmarkInputPolicy.describe(loadedOneHandProfile)} mirrorCameraFrame=$mirrorCameraFrame")
                postStatus("Looking for hand")
            } catch (exc: Throwable) {
                Log.e(TAG, "Pipeline load failed", exc)
                running.set(false)
                postStatus(startupFailureStatus(exc))
                mainExecutor.execute {
                    imageAnalysis?.clearAnalyzer()
                    imageAnalysis = null
                    cameraProvider?.unbindAll()
                    cameraProvider = null
                }
                closePipeline()
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            postStatus("Recognition Paused")
            return
        }
        resetTemporalState()
        postFeedback(RecognitionFeedback.idle())
        postSkeletonFrame(null)
        mainExecutor.execute {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
        analyzerExecutor.execute {
            closePipeline()
            postStatus("Recognition Paused")
        }
    }

    fun release() {
        stop()
        analyzerExecutor.shutdown()
    }

    fun startCalibration(label: String) {
        if (!OneHandCalibrationConfig.ENABLE_ONEHAND_CALIBRATION_RECORDING) {
            postStatus("Calibration disabled")
            return
        }
        val clean = label.trim().uppercase(Locale.US)
        if (clean !in OneHandCalibrationConfig.CALIBRATION_LABELS) {
            postStatus("Unsupported calibration label")
            return
        }
        analyzerExecutor.execute {
            calibrationLabel = clean
            calibrationBuffer?.clear()
            calibrationMissingPoseCount = 0
            calibrationMissingHandCount = 0
            oneHandBuffer?.clear()
            clearSequenceState()
            dynamicGate.noteNoOutputState()
            Log.i(TAG, "calibration_start label=$clean profile=${oneHandProfile?.id ?: "not_loaded"}")
            postStatus("Prepare $clean")
        }
    }

    fun cancelCalibration() {
        analyzerExecutor.execute {
            calibrationLabel = ""
            calibrationBuffer?.clear()
            calibrationMissingPoseCount = 0
            calibrationMissingHandCount = 0
            postStatus("Looking for hand")
            Log.i(TAG, "calibration_cancelled")
        }
    }

    fun switchCamera(previewView: PreviewView) {
        analyzerExecutor.execute {
            useBackCamera = !useBackCamera
            resetTemporalState()
            postFeedback(RecognitionFeedback.idle())

            oneHandProfile?.let { profile ->
                val mirrorCameraFrame = mirrorCameraFrameFor(profile)
                extractor?.close()
                extractor = MediaPipeLandmarkExtractor(appContext, mirrorCameraFrame)
                Log.i(TAG, "camera_switch camera=${currentCameraLabel()} mirrorCameraFrame=$mirrorCameraFrame")
            }

            postStatus("Switching to ${currentCameraLabel()} camera")

            mainExecutor.execute {
                bindCamera(previewView)
            }
        }
    }

    private fun currentCameraLabel(): String {
        return if (useBackCamera) "Back" else "Front"
    }

    private fun mirrorCameraFrameFor(profile: RecognitionProfile): Boolean {
        return if (useBackCamera) {
            false
        } else {
            AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(profile)
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            @Suppress("DEPRECATION")
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(192, 144))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }
            imageAnalysis = analysis
            val cameraSelector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            Log.i(TAG, "camera_selector=" + (if (useBackCamera) "BACK" else "FRONT"))
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
        }, mainExecutor)
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            if (!running.get()) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzeAtMs < ANALYZE_INTERVAL_MS) return
            lastAnalyzeAtMs = now

            val frame = extractor?.processFrame(imageProxy)
            postSkeletonFrame(frame)
            val decision = router.decide(frame)
            logRoute(decision)
            handleRouteChange(decision.route)

            if (frame == null || !frame.hasAnyHand) {
                if (calibrationLabel.isBlank() && hasActiveLiveCollection()) {
                    handleLiveCollectionMissingFrame("hand_lost")
                    return
                }
                if (calibrationLabel.isBlank()) {
                    resetTemporalState()
                }
                resetFeatureQualityCounters()
                postFeedback(RecognitionFeedback.idle())
                val activeCalibration = calibrationLabel
                postStatus(if (activeCalibration.isBlank()) decision.statusText else "Prepare $activeCalibration")
                return
            }

            if (calibrationLabel.isNotBlank()) {
                processCalibration(frame)
                return
            }

            processDynamicWord(
                frame = frame,
                decision = decision,
                profile = oneHandProfile,
                recognizer = oneHandRecognizer,
                buffer = oneHandBuffer
            )
        } catch (exc: Throwable) {
            Log.e(TAG, "Frame analysis failed", exc)
            resetTemporalState()
            postFeedback(RecognitionFeedback.idle())
            postStatus("Try again")
        } finally {
            imageProxy.close()
        }
    }

    private fun processStaticAlphabet(frame: LandmarkFrame, decision: RouterDecision) {
        // TODO: Re-enable camera alphabet output only after AlphabetAcceptanceGate is live-tested
        // with an 8-12 frame stable hold and same-letter anti-spam reset.
        val classifier = alphabetClassifier ?: run {
            postStatus("Try again")
            return
        }
        dynamicGate.noteNoOutputState()
        oneHandBuffer?.clear()
        fullSignBuffer?.clear()

        val raw = classifier.predict(frame, FLIP_LANDMARKS_HORIZONTAL)
        val gateResult = alphabetGate.evaluate(raw, decision)
        logAlphabet(raw, gateResult)
        if (!gateResult.accepted) {
            postFeedback(cleanFeedback(DetectionStatus.DETECTING, gateResult.label))
            postStatus(if (gateResult.reason == "hand_not_stable") "Hold steady" else "Try again")
            return
        }

        val accepted = RecognitionResult(
            gateResult.label,
            gateResult.confidence,
            gateResult.margin,
            true,
            "alphabet_${gateResult.reason}"
        )
        postFeedback(cleanFeedback(DetectionStatus.RECOGNIZED, accepted.label))
        mainExecutor.execute { onAcceptedResult(accepted) }
        postStatus("Accepted")
    }

    private fun processDynamicWord(
        frame: LandmarkFrame,
        decision: RouterDecision,
        profile: RecognitionProfile?,
        recognizer: VoxGestTfliteRecognizer?,
        buffer: LandmarkSequenceBuffer?
    ) {
        if (profile == null || recognizer == null || buffer == null) {
            postStatus("Try again")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < collectionPausedUntilMs) return
        logHandMappingIfNeeded(profile, frame)
        val capture = appendOneHandCaptureFrame(profile, frame, buffer)
        if (!capture.valid) {
            countMissingFeatureParts(profile, frame)
            postFeedback(cleanFeedback(DetectionStatus.SEARCHING, ""))
            if (hasActiveLiveCollection()) {
                handleLiveCollectionMissingFrame(capture.reason)
                return
            }
            postStatus("Hold hand clearer")
            Log.i(
                TAG,
                "route=${decision.route} model=${profile.id} rejected reason=missing_or_wrong_landmarks missing_pose_count=$missingPoseCount missing_hand_count=$missingHandCount"
            )
            return
        }
        collectionInvalidStartedAtMs = 0L
        collectionLostHandFrames = 0
        if (!capture.appended) {
            resetCollection("Try again", resetGate = true)
            Log.i(TAG, "route=${decision.route} model=${profile.id} rejected reason=${capture.reason}")
            return
        }
        postFeedback(cleanFeedback(DetectionStatus.DETECTING, ""))
        val framesCollected = capture.framesCollected
        slidingWindowFrame += 1L
        Log.i(TAG, "collection_progress frames=$framesCollected/${profile.sequenceLength}")
        if (!buffer.isReady() && collectionStartedAtMs > 0L && now - collectionStartedAtMs > collectionTimeoutMs(profile)) {
            Log.i(TAG, "collection_reset reason=SEQUENCE_TIMEOUT elapsed_ms=${now - collectionStartedAtMs} frames=${buffer.size()}")
            resetCollection("Try again", resetGate = true)
            return
        }
        if (!buffer.isReady()) {
            postCollectionProgress(profile, framesCollected)
            return
        }

        val snapshot = buffer.snapshot()
        val handPresence = buffer.handPresenceRatio()
        if (snapshot == null) {
            postStatus("Try again")
            return
        }

        logFeatureWindow(profile, snapshot, handPresence)
        postStatus("Recognizing...")
        Log.i(TAG, "collection_progress frames=${profile.sequenceLength}/${profile.sequenceLength}")
        Log.i(TAG, "inference_started shape=[1,${snapshot.size},${snapshot.firstOrNull()?.size ?: 0}] profile=${profile.id}")
        val raw = recognizer.recognize(snapshot)
        val top1Label = top1Label(raw)
        val top1Confidence = top1Confidence(raw)
        val consecutiveMatches = updateSlidingWindowMatch(top1Label)
        Log.i(
            TAG,
            "sliding_window frame=$slidingWindowFrame top1=${top1Label.ifBlank { "NONE" }} conf=${String.format(Locale.US, "%.3f", top1Confidence)} consecutive=$consecutiveMatches"
        )
        logInferenceResult(raw)
        logGateInput(profile, raw)
        val gateResult = dynamicGate.evaluate(raw, profile, decision, handPresence, consecutiveMatches)
        logGateOutput(gateResult)
        logDynamic(decision, profile, raw, gateResult, snapshot)
        logNameDiagnostic(profile, decision, raw, gateResult, snapshot, handPresence)
        resetFeatureQualityCounters()
        if (!gateResult.accepted) {
            logEmitResult(gateResult.label, emitted = false)
            postFeedback(cleanFeedback(statusForRejection(gateResult.reason), gateResult.label))
            postStatus(statusTextForRejection(gateResult.reason))
            return
        }

        val accepted = RecognitionResult(
            gateResult.label,
            gateResult.confidence,
            gateResult.margin,
            true,
            "dynamic_${gateResult.reason}",
            raw.top3
        )
        postFeedback(cleanFeedback(DetectionStatus.RECOGNIZED, accepted.label))
        logEmitResult(accepted.label, emitted = true)
        mainExecutor.execute { onAcceptedResult(accepted) }
        postStatus("Accepted: ${accepted.label}")
    }

    private fun processCalibration(frame: LandmarkFrame) {
        val label = calibrationLabel
        val profile = oneHandProfile
        val buffer = calibrationBuffer
        if (label.isBlank() || profile == null || buffer == null) return

        logHandMappingIfNeeded(profile, frame)
        val builder = OneHand162FeatureBuilder(profile)
        val capture = appendOneHandCaptureFrame(profile, frame, buffer)
        if (!capture.valid) {
            if (!frame.hasPose) calibrationMissingPoseCount += 1
            if (builder.selectedHandLandmarks(frame)?.size != HAND_LANDMARK_COUNT) {
                calibrationMissingHandCount += 1
            }
            postFeedback(cleanFeedback(DetectionStatus.SEARCHING, ""))
            postStatus("Hold hand clearer")
            Log.i(
                TAG,
                "calibration_wait label=$label reason=missing_landmarks missing_pose_count=$calibrationMissingPoseCount missing_hand_count=$calibrationMissingHandCount selected_hand_slot=${builder.selectedMediaPipeSide()}"
            )
            return
        }

        if (!capture.appended) {
            postStatus("Calibration shape mismatch")
            Log.e(TAG, "calibration_rejected label=$label reason=${capture.reason}")
            buffer.clear()
            return
        }

        val collected = capture.framesCollected
        postFeedback(cleanFeedback(DetectionStatus.DETECTING, label))
        postStatus("Recording $label $collected/${profile.sequenceLength}")
        if (!buffer.isReady()) return

        val snapshot = buffer.snapshot() ?: run {
            postStatus("Try again")
            buffer.clear()
            return
        }
        val wristIndex = selectedPoseWristIndex(profile)
        val sample = OneHandCalibrationSample(
            label = label,
            timestamp = System.currentTimeMillis(),
            deviceModel = Build.MODEL ?: "ANDROID",
            activeProfile = profile.id,
            featureProfile = profile.featureProfile,
            inputShape = intArrayOf(profile.sequenceLength, AndroidLandmarkInputPolicy.runtimeFeatureSize(profile)),
            sequenceLengthAtExport = LandmarkSequenceBuffer.SEQUENCE_LENGTH,
            dominantHand = profile.dominantHand,
            mirroredInput = profile.mirroredInput,
            selectedHandSlot = builder.selectedMediaPipeSide(),
            handPresenceRatio = buffer.handPresenceRatio(),
            missingPoseCount = calibrationMissingPoseCount,
            missingHandCount = calibrationMissingHandCount,
            motionScore = computeMotionEnergy(snapshot, wristIndex),
            wristPath = computeWristPath(snapshot, wristIndex),
            featureArray = snapshot
        )

        try {
            val result = calibrationRecorder.save(sample)
            Log.i(
                TAG,
                "calibration_complete label=$label file=${result.relativePath}/${result.fileName} shape=[${snapshot.size},${snapshot.firstOrNull()?.size ?: 0}]"
            )
            postStatus("Saved $label")
        } catch (exc: Throwable) {
            Log.e(TAG, "calibration_save_failed label=$label", exc)
            postStatus("Calibration save failed")
        } finally {
            calibrationLabel = ""
            buffer.clear()
            calibrationMissingPoseCount = 0
            calibrationMissingHandCount = 0
            scheduleStatus("Looking for hand", STATUS_RESET_DELAY_MS)
        }
    }

    private fun appendOneHandCaptureFrame(
        profile: RecognitionProfile,
        frame: LandmarkFrame,
        buffer: LandmarkSequenceBuffer
    ): OneHandCaptureResult {
        val expectedFeatureSize = AndroidLandmarkInputPolicy.runtimeFeatureSize(profile)
        val feature = buildFeature(profile, frame, previousOneHandFrame)
            ?: return OneHandCaptureResult(valid = false, appended = false, framesCollected = buffer.size(), reason = "missing_or_wrong_landmarks")
        if (buffer.size() == 0) {
            collectionStartedAtMs = SystemClock.elapsedRealtime()
        }
        if (!buffer.add(feature, true)) {
            return OneHandCaptureResult(
                valid = true,
                appended = false,
                framesCollected = buffer.size(),
                reason = "feature_shape_${feature.size}_expected_$expectedFeatureSize"
            )
        }
        previousOneHandFrame = frame
        return OneHandCaptureResult(valid = true, appended = true, framesCollected = buffer.size(), reason = "")
    }

    private fun handleLiveCollectionMissingFrame(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (collectionInvalidStartedAtMs == 0L) collectionInvalidStartedAtMs = now
        collectionLostHandFrames += 1
        val frames = oneHandBuffer?.size() ?: 0
        val elapsedInvalid = now - collectionInvalidStartedAtMs
        if (collectionLostHandFrames >= LOST_HAND_ABORT_FRAMES || elapsedInvalid > NO_VALID_LANDMARK_TIMEOUT_MS) {
            Log.i(
                TAG,
                "collection_reset reason=${reason.uppercase(Locale.US)} elapsed_invalid_ms=$elapsedInvalid lost_frames=$collectionLostHandFrames frames=$frames"
            )
            resetCollection("Try again", resetGate = true)
            return
        }
        Log.i(
            TAG,
            "collection_wait reason=$reason elapsed_invalid_ms=$elapsedInvalid lost_frames=$collectionLostHandFrames frames=$frames/${oneHandProfile?.sequenceLength ?: 30}"
        )
        postFeedback(cleanFeedback(DetectionStatus.SEARCHING, ""))
        postCollectionProgress(oneHandProfile, frames)
    }

    private fun hasActiveLiveCollection(): Boolean {
        return (oneHandBuffer?.size() ?: 0) > 0
    }

    private fun handleRouteChange(route: RecognitionRoute) {
        if (route == lastRoute) return
        when (route) {
            RecognitionRoute.STATIC -> Log.i(TAG, "router=STATIC camera_output=ONEHAND_ONLY alphabet_disabled=true")
            RecognitionRoute.ONEHAND -> Log.i(TAG, "router=ONEHAND camera_output=onehand162_phrase_v1")
            RecognitionRoute.FULLSIGN -> Log.i(TAG, "router=FULLSIGN camera_output=ONEHAND_ONLY fullsign_disabled=true")
            RecognitionRoute.NOTHING -> Log.i(TAG, "router=NOTHING camera_output=ONEHAND_BUFFER_HELD until hand leaves frame")
        }
        lastRoute = route
    }

    private fun top1Label(raw: RecognitionResult): String {
        return (raw.top3.firstOrNull()?.label ?: raw.label).trim().uppercase(Locale.US)
    }

    private fun top1Confidence(raw: RecognitionResult): Float {
        return raw.top3.firstOrNull()?.confidence ?: raw.confidence
    }

    private fun updateSlidingWindowMatch(top1Label: String): Int {
        if (top1Label.isBlank()) {
            lastSlidingTop1Label = ""
            consecutiveMatchCount = 0
            return consecutiveMatchCount
        }
        if (top1Label == lastSlidingTop1Label) {
            consecutiveMatchCount += 1
        } else {
            lastSlidingTop1Label = top1Label
            consecutiveMatchCount = 1
        }
        return consecutiveMatchCount
    }

    private fun resetSlidingWindowState() {
        slidingWindowFrame = 0L
        lastSlidingTop1Label = ""
        consecutiveMatchCount = 0
    }

    private fun buildFeature(profile: RecognitionProfile, frame: LandmarkFrame, previous: LandmarkFrame?): FloatArray? {
        return if (profile.featureProfile == "fullsign225") {
            val builder = FullSign225FeatureBuilder(profile)
            if (builder.hasRequiredLandmarks(frame)) builder.build(frame) else null
        } else {
            val builder = OneHand162FeatureBuilder(profile)
            if (!builder.hasRequiredLandmarks(frame)) return null
            if (AndroidLandmarkInputPolicy.USE_VELOCITY_DELTA_FEATURES) {
                builder.buildWithDelta(frame, previous)
            } else {
                builder.build(frame)
            }
        }
    }

    private fun resetTemporalState() {
        router.reset()
        dynamicGate.reset()
        oneHandBuffer?.clear()
        lastRoute = RecognitionRoute.NOTHING
        resetFeatureQualityCounters()
        clearSequenceState()
        statusToken += 1L
    }

    private fun closePipeline() {
        resetTemporalState()
        extractor?.close()
        extractor = null
        alphabetClassifier?.close()
        alphabetClassifier = null
        oneHandRecognizer?.close()
        oneHandRecognizer = null
        fullSignRecognizer?.close()
        fullSignRecognizer = null
        oneHandProfile = null
        fullSignProfile = null
        oneHandBuffer = null
        fullSignBuffer = null
        calibrationLabel = ""
        calibrationBuffer = null
        calibrationMissingPoseCount = 0
        calibrationMissingHandCount = 0
    }

    private fun postStatus(status: String) {
        val now = SystemClock.elapsedRealtime()
        if (status == lastStatus && now - lastStatusAtMs < STATUS_MIN_INTERVAL_MS) return
        lastStatus = status
        lastStatusAtMs = now
        mainExecutor.execute { onStatus(status) }
    }

    private fun scheduleStatus(status: String, delayMs: Long) {
        val token = ++statusToken
        mainHandler.postDelayed({
            if (running.get() && token == statusToken) {
                postStatus(status)
            }
        }, delayMs)
    }

    private fun cleanFeedback(status: DetectionStatus, label: String): RecognitionFeedback {
        return RecognitionFeedback(
            detectionStatus = status,
            confidence = 0f,
            label = label.uppercase(Locale.US),
            handLandmarks = emptyList(),
            eventId = if (status == DetectionStatus.RECOGNIZED) SystemClock.elapsedRealtime() else 0L
        )
    }

    private fun postFeedback(feedback: RecognitionFeedback) {
        mainExecutor.execute { onRecognitionFeedback(feedback) }
    }

    private fun postSkeletonFrame(frame: LandmarkFrame?) {
        mainExecutor.execute { onSkeletonFrame(frame) }
    }

    private fun statusForRejection(reason: String): DetectionStatus {
        return if (reason == "LOW_HAND_PRESENCE" || reason == "SHAPE_MISMATCH" || reason == "BAD_SEQUENCE") {
            DetectionStatus.SEARCHING
        } else {
            DetectionStatus.DETECTING
        }
    }

    private fun logRoute(decision: RouterDecision) {
        Log.i(
            TAG,
            "router=${decision.route} status=${decision.statusText} reason=${decision.reason} hand=${decision.handPresence} both=${decision.bothHandsPresent}"
        )
    }

    private fun logAlphabet(raw: AlphabetRawPrediction?, gate: AlphabetGateResult) {
        Log.i(
            TAG,
            "route=STATIC model=models/asl_alphabet.tflite input=[1,63] predicted=${raw?.label ?: ""} conf=${raw?.confidence ?: 0f} margin=${raw?.margin ?: 0f} accepted=${gate.accepted} reason=${gate.reason}"
        )
    }

    private fun logDynamic(
        decision: RouterDecision,
        profile: RecognitionProfile,
        raw: RecognitionResult,
        gate: DynamicWordGateResult,
        snapshot: Array<FloatArray>
    ) {
        val inputShape = "[1,${snapshot.size},${snapshot.firstOrNull()?.size ?: 0}]"
        val top3 = raw.top3.joinToString(prefix = "[", postfix = "]") {
            "${it.label}:${String.format(Locale.US, "%.3f", it.confidence)}"
        }
        Log.i(
            TAG,
            "active_profile=${profile.id} route=${decision.route} model=${profile.modelAsset} labels=${profile.labels} input_shape=$inputShape top3=$top3 predicted=${raw.label} confidence=${String.format(Locale.US, "%.3f", raw.confidence)} margin=${String.format(Locale.US, "%.3f", raw.margin)} ${if (gate.accepted) "accepted" else "rejected"} reason=${gate.reason}"
        )
    }

    private fun logNameDiagnostic(
        profile: RecognitionProfile,
        decision: RouterDecision,
        raw: RecognitionResult,
        gate: DynamicWordGateResult,
        snapshot: Array<FloatArray>,
        handPresence: Float
    ) {
        val expected = DEBUG_EXPECTED_ONEHAND_LABEL.trim().uppercase(Locale.US)
        val nameInTop3 = raw.top3.any { it.label.equals("NAME", ignoreCase = true) }
        if (expected != "NAME" && raw.label != "NAME" && !nameInTop3) return

        val wristIndex = selectedPoseWristIndex(profile)
        val motionEnergy = computeMotionEnergy(snapshot, wristIndex)
        val wristPath = computeWristPath(snapshot, wristIndex)
        val lastFrame = snapshot.lastOrNull()
        val handWristOffset = 99
        val rawWrist = if (lastFrame != null && lastFrame.size >= handWristOffset + 3) {
            "x=${String.format(Locale.US, "%.4f", lastFrame[handWristOffset])},y=${String.format(Locale.US, "%.4f", lastFrame[handWristOffset + 1])},z=${String.format(Locale.US, "%.4f", lastFrame[handWristOffset + 2])}"
        } else {
            "unavailable"
        }
        val top1 = raw.top3.getOrNull(0)
        val top2 = raw.top3.getOrNull(1)
        val compare = if (expected.isNotBlank()) "expected=$expected predicted=${raw.label}" else "expected=<unset> predicted=${raw.label}"
        Log.i(
            TAG,
            "NAME_DIAGNOSTIC $compare router=${decision.route} sequence_frames=${snapshot.size} valid_frame_count=${snapshot.count { it.size == profile.featureSize }} per_frame_feature_length=${snapshot.map { it.size }.distinct()} final_input_shape=[1,${snapshot.size},${snapshot.firstOrNull()?.size ?: 0}] top1=${top1?.label}:${top1?.confidence ?: 0f} top2=${top2?.label}:${top2?.confidence ?: 0f} margin=${raw.margin} hand_presence_ratio=${String.format(Locale.US, "%.3f", handPresence)} motion_score=${String.format(Locale.US, "%.6f", motionEnergy)} wrist_path=${String.format(Locale.US, "%.6f", wristPath)} selected_hand_slot=${OneHand162FeatureBuilder(profile).selectedMediaPipeSide()} mirrored_input=${profile.mirroredInput} raw_wrist_nose_relative=[$rawWrist] wrist_normalized_origin=[0.0000,0.0000,0.0000] normalization_policy=PYTHON_NOSE_RELATIVE_NO_WRIST_SCALE gate_accepted=${gate.accepted} gate_reason=${gate.reason}"
        )
    }

    private fun logFeatureWindow(
        profile: RecognitionProfile,
        snapshot: Array<FloatArray>,
        handPresence: Float
    ) {
        val lengths = snapshot.map { it.size }.distinct().joinToString(prefix = "[", postfix = "]")
        val finalInputShape = "[1,${snapshot.size},${snapshot.firstOrNull()?.size ?: 0}]"
        Log.i(
            TAG,
            "feature_window profile=${profile.id} sequence_frames=${snapshot.size} per_frame_feature_length=$lengths final_input_shape=$finalInputShape missing_pose_count=$missingPoseCount missing_hand_count=$missingHandCount hand_presence_ratio=${String.format(Locale.US, "%.3f", handPresence)} contract=pose99+selected_hand63"
        )
    }

    private fun logHandMappingIfNeeded(profile: RecognitionProfile, frame: LandmarkFrame) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHandMappingLogAtMs < HAND_MAPPING_LOG_INTERVAL_MS) return
        lastHandMappingLogAtMs = now

        val builder = OneHand162FeatureBuilder(profile)
        val selectedSlot = builder.selectedMediaPipeSide()
        val selectedPresent = builder.selectedHandLandmarks(frame)?.size == HAND_LANDMARK_COUNT
        val observations = frame.handObservations.joinToString(prefix = "[", postfix = "]") { item ->
            "slot=${item.slot},mp=${item.mediaPipeHandedness},avgX=${String.format(Locale.US, "%.3f", item.averageX)},estimate=${item.physicalSideEstimate}"
        }
        val mirroringStatus = "MIRRORING_UNVERIFIED"
        Log.i(
            TAG,
            "hand_mapping status=$mirroringStatus mediaPipeHandedness=$observations selected_hand_slot=$selectedSlot selected_present=$selectedPresent selected_mapping=${builder.selectedMappingText()} mirrored_input=${profile.mirroredInput}"
        )
    }

    private fun countMissingFeatureParts(profile: RecognitionProfile, frame: LandmarkFrame) {
        if (!frame.hasPose) missingPoseCount += 1
        if (profile.featureProfile == "onehand162") {
            val selectedPresent = OneHand162FeatureBuilder(profile).selectedHandLandmarks(frame)?.size == HAND_LANDMARK_COUNT
            if (!selectedPresent) missingHandCount += 1
        } else if (!frame.hasAnyHand) {
            missingHandCount += 1
        }
    }

    private fun resetFeatureQualityCounters() {
        missingPoseCount = 0
        missingHandCount = 0
    }

    private fun resetCollection(status: String, resetGate: Boolean) {
        clearSequenceBuffer()
        if (resetGate) dynamicGate.noteNoOutputState()
        postStatus(status)
        collectionPausedUntilMs = SystemClock.elapsedRealtime() + STATUS_RESET_DELAY_MS
        scheduleStatus("Looking for hand", STATUS_RESET_DELAY_MS)
    }

    private fun clearSequenceBuffer() {
        oneHandBuffer?.clear()
        clearSequenceState()
    }

    private fun clearSequenceState() {
        collectionStartedAtMs = 0L
        signingStatusStartedAtMs = 0L
        collectionInvalidStartedAtMs = 0L
        collectionLostHandFrames = 0
        previousOneHandFrame = null
        resetSlidingWindowState()
    }

    private fun postCollectionProgress(profile: RecognitionProfile?, framesCollected: Int) {
        val total = profile?.sequenceLength ?: LandmarkSequenceBuffer.SEQUENCE_LENGTH
        postStatus("Collecting sign ${framesCollected.coerceAtMost(total)}/$total")
    }

    private fun collectionTimeoutMs(profile: RecognitionProfile): Long {
        return if (profile.id == OneHandCalibrationConfig.CALIBRATED_PROFILE_ID ||
            OneHandCalibrationConfig.USE_ANDROID_CALIBRATED_ONEHAND_MODEL
        ) {
            CALIBRATED_ONEHAND_COLLECTION_TIMEOUT_MS
        } else {
            ONEHAND_COLLECTION_TIMEOUT_MS
        }
    }

    private fun logInferenceResult(raw: RecognitionResult) {
        val top3 = raw.top3.joinToString(prefix = "[", postfix = "]") {
            "${it.label}:${String.format(Locale.US, "%.3f", it.confidence)}"
        }
        val top1 = raw.top3.getOrNull(0)
        val top2 = raw.top3.getOrNull(1)
        Log.i(
            TAG,
            "inference_result top3=$top3 top1=${top1?.label ?: ""} top2=${top2?.label ?: ""} confidence=${String.format(Locale.US, "%.3f", raw.confidence)} margin=${String.format(Locale.US, "%.3f", raw.margin)}"
        )
    }

    private fun logGateInput(profile: RecognitionProfile, raw: RecognitionResult) {
        Log.i(
            TAG,
            "gate_input label=${raw.label} conf=${String.format(Locale.US, "%.3f", raw.confidence)} margin=${String.format(Locale.US, "%.3f", raw.margin)} profile=${profile.id} supportedLabels=${profile.labels}"
        )
    }

    private fun logGateOutput(gate: DynamicWordGateResult) {
        Log.i(
            TAG,
            "gate_output accepted=${gate.accepted} reason=${gate.reason} cooldownActive=${gate.cooldownActive} duplicateBlocked=${gate.duplicateBlocked}"
        )
    }

    private fun logEmitResult(label: String, emitted: Boolean) {
        Log.i(TAG, "emit_result label=$label emitted=$emitted")
    }

    private fun statusTextForRejection(reason: String): String {
        return when (reason) {
            "LOW_CONFIDENCE" -> "Low confidence"
            "LOW_MARGIN", "NAME_NEEDS_SECOND_WINDOW", "SLIDING_WINDOW_NEEDS_SECOND_MATCH", "UNSTABLE_LANDMARKS" -> "Hold hand clearer"
            "LOW_HAND_PRESENCE", "BAD_SEQUENCE", "SHAPE_MISMATCH" -> "Hold hand clearer"
            else -> "Try again"
        }
    }

    private fun selectedPoseWristIndex(profile: RecognitionProfile): Int {
        return when (OneHand162FeatureBuilder(profile).selectedMediaPipeSide()) {
            "right" -> 16
            else -> 15
        }
    }

    private fun computeMotionEnergy(snapshot: Array<FloatArray>, wristIndex: Int): Float {
        val offset = wristIndex * 3
        val pairs = snapshot.toList().zipWithNext().takeLast(10)
        if (pairs.isEmpty()) return 0f
        var total = 0f
        pairs.forEach { (a, b) ->
            val dx = a[offset] - b[offset]
            val dy = a[offset + 1] - b[offset + 1]
            total += dx * dx + dy * dy
        }
        return total / pairs.size.toFloat()
    }

    private fun computeWristPath(snapshot: Array<FloatArray>, wristIndex: Int): Float {
        val offset = wristIndex * 3
        var total = 0f
        snapshot.toList().zipWithNext().forEach { (a, b) ->
            val dx = a[offset] - b[offset]
            val dy = a[offset + 1] - b[offset + 1]
            total += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return total
    }

    private fun startupFailureStatus(exc: Throwable): String {
        val message = exc.message.orEmpty()
        return when {
            message.contains("Model/labels mismatch", ignoreCase = true) -> "Model/labels mismatch."
            message.contains("SHAPE_MISMATCH", ignoreCase = true) ||
                message.contains("input shape", ignoreCase = true) -> "Shape mismatch."
            else -> "Try again"
        }
    }

    companion object {
        private const val TAG = "VoxGestRecognition"
        private const val ANALYZE_INTERVAL_MS = 0L
        private const val STATUS_MIN_INTERVAL_MS = 220L
        private const val FLIP_LANDMARKS_HORIZONTAL = false
        private const val HAND_LANDMARK_COUNT = 21
        private const val HAND_MAPPING_LOG_INTERVAL_MS = 1000L
        private const val ONEHAND_COLLECTION_TIMEOUT_MS = 8000L
        private const val CALIBRATED_ONEHAND_COLLECTION_TIMEOUT_MS = 10000L
        private const val NO_VALID_LANDMARK_TIMEOUT_MS = 1500L
        private const val LOST_HAND_ABORT_FRAMES = 8
        private const val STATUS_RESET_DELAY_MS = 1000L
        private const val DEBUG_EXPECTED_ONEHAND_LABEL = ""
    }
}


