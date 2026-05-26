package com.voxgest.dryrun

import android.content.Context
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

class VoxGestCameraRecognitionController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onStatus: (String) -> Unit,
    private val onRecognitionFeedback: (RecognitionFeedback) -> Unit = {},
    private val onAcceptedResult: (RecognitionResult) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val router = RecognitionAutoRouter()
    private val alphabetGate = AlphabetAcceptanceGate()
    private val dynamicGate = DynamicWordAcceptanceGate()

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

    fun start(previewView: PreviewView) {
        if (!running.compareAndSet(false, true)) return
        postStatus("Starting Camera")
        bindCamera(previewView)
        analyzerExecutor.execute {
            try {
                val loadedFullSignRecognizer = VoxGestTfliteRecognizer(appContext)
                val loadedFullSignProfile = loadedFullSignRecognizer.load("fullsign225_phrase_v1")
                val loadedOneHandRecognizer = VoxGestTfliteRecognizer(appContext)
                val loadedOneHandProfile = loadedOneHandRecognizer.load("onehand162_phrase_v1")
                val mirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedFullSignProfile)
                val loadedExtractor = MediaPipeLandmarkExtractor(appContext, mirrorCameraFrame)
                val loadedAlphabetClassifier = AlphabetClassifier(appContext).also { it.load() }

                fullSignRecognizer = loadedFullSignRecognizer
                fullSignProfile = loadedFullSignProfile
                fullSignBuffer = LandmarkSequenceBuffer(loadedFullSignProfile.sequenceLength, loadedFullSignProfile.featureSize)
                oneHandRecognizer = loadedOneHandRecognizer
                oneHandProfile = loadedOneHandProfile
                oneHandBuffer = LandmarkSequenceBuffer(loadedOneHandProfile.sequenceLength, loadedOneHandProfile.featureSize)
                extractor = loadedExtractor
                alphabetClassifier = loadedAlphabetClassifier
                Log.i(TAG, "Automatic router ready; mirrorCameraFrame=$mirrorCameraFrame alphabetFlip=$FLIP_LANDMARKS_HORIZONTAL")
                postStatus("Looking for hand")
            } catch (exc: Throwable) {
                Log.e(TAG, "Pipeline load failed", exc)
                postStatus("Try again")
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
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }
            imageAnalysis = analysis
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
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
            val decision = router.decide(frame)
            logRoute(decision)
            handleRouteChange(decision.route)

            if (frame == null || decision.route == RecognitionRoute.NOTHING) {
                if (decision.handPresence <= 0f) {
                    resetTemporalState()
                    postFeedback(RecognitionFeedback.idle())
                } else {
                    dynamicGate.noteNoOutputState()
                    alphabetGate.resetCandidate()
                    postFeedback(cleanFeedback(DetectionStatus.SEARCHING, ""))
                }
                postStatus(decision.statusText)
                return
            }

            when (decision.route) {
                RecognitionRoute.STATIC -> processStaticAlphabet(frame, decision)
                RecognitionRoute.ONEHAND -> processDynamicWord(
                    frame = frame,
                    decision = decision,
                    profile = oneHandProfile,
                    recognizer = oneHandRecognizer,
                    buffer = oneHandBuffer
                )
                RecognitionRoute.FULLSIGN -> processDynamicWord(
                    frame = frame,
                    decision = decision,
                    profile = fullSignProfile,
                    recognizer = fullSignRecognizer,
                    buffer = fullSignBuffer
                )
                RecognitionRoute.NOTHING -> Unit
            }
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
        alphabetGate.resetCandidate()
        val feature = buildFeature(profile, frame)
        if (feature == null) {
            buffer.clear()
            dynamicGate.noteNoOutputState()
            postFeedback(cleanFeedback(DetectionStatus.SEARCHING, ""))
            postStatus("Try again")
            Log.i(TAG, "route=${decision.route} model=${profile.id} rejected reason=missing_or_wrong_landmarks")
            return
        }
        if (!buffer.add(feature, true)) {
            buffer.clear()
            postStatus("Try again")
            Log.i(TAG, "route=${decision.route} model=${profile.id} rejected reason=feature_shape_${feature.size}_expected_${profile.featureSize}")
            return
        }
        postFeedback(cleanFeedback(DetectionStatus.DETECTING, ""))
        if (!buffer.isReady()) {
            postStatus("Signing...")
            return
        }

        val snapshot = buffer.snapshot()
        val handPresence = buffer.handPresenceRatio()
        buffer.clear()
        if (snapshot == null) {
            postStatus("Try again")
            return
        }

        postStatus("Recognizing...")
        val raw = recognizer.recognize(snapshot)
        val gateResult = dynamicGate.evaluate(raw, profile, decision, handPresence)
        logDynamic(decision, profile, raw, gateResult, snapshot)
        if (!gateResult.accepted) {
            postFeedback(cleanFeedback(statusForRejection(gateResult.reason), gateResult.label))
            postStatus("Try again")
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
        mainExecutor.execute { onAcceptedResult(accepted) }
        postStatus("Accepted")
    }

    private fun handleRouteChange(route: RecognitionRoute) {
        if (route == lastRoute) return
        when (route) {
            RecognitionRoute.STATIC -> {
                oneHandBuffer?.clear()
                fullSignBuffer?.clear()
                dynamicGate.noteNoOutputState()
            }
            RecognitionRoute.ONEHAND -> {
                fullSignBuffer?.clear()
                alphabetGate.resetCandidate()
            }
            RecognitionRoute.FULLSIGN -> {
                oneHandBuffer?.clear()
                alphabetGate.resetCandidate()
            }
            RecognitionRoute.NOTHING -> {
                oneHandBuffer?.clear()
                fullSignBuffer?.clear()
                dynamicGate.noteNoOutputState()
                alphabetGate.resetCandidate()
            }
        }
        lastRoute = route
    }

    private fun buildFeature(profile: RecognitionProfile, frame: LandmarkFrame): FloatArray? {
        return if (profile.featureProfile == "fullsign225") {
            val builder = FullSign225FeatureBuilder(profile)
            if (builder.hasRequiredLandmarks(frame)) builder.build(frame) else null
        } else {
            val builder = OneHand162FeatureBuilder(profile)
            if (builder.hasRequiredLandmarks(frame)) builder.build(frame) else null
        }
    }

    private fun resetTemporalState() {
        router.reset()
        alphabetGate.reset()
        dynamicGate.reset()
        oneHandBuffer?.clear()
        fullSignBuffer?.clear()
        lastRoute = RecognitionRoute.NOTHING
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
    }

    private fun postStatus(status: String) {
        val now = SystemClock.elapsedRealtime()
        if (status == lastStatus && now - lastStatusAtMs < STATUS_MIN_INTERVAL_MS) return
        lastStatus = status
        lastStatusAtMs = now
        mainExecutor.execute { onStatus(status) }
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

    private fun statusForRejection(reason: String): DetectionStatus {
        return if (reason == "low_hand_presence" || reason == "wrong_input_shape") {
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
        Log.i(
            TAG,
            "route=${decision.route} model=${profile.modelAsset} input=$inputShape predicted=${raw.label} conf=${raw.confidence} margin=${raw.margin} accepted=${gate.accepted} reason=${gate.reason}"
        )
    }

    companion object {
        private const val TAG = "VoxGestRecognition"
        private const val ANALYZE_INTERVAL_MS = 90L
        private const val STATUS_MIN_INTERVAL_MS = 220L
        private const val FLIP_LANDMARKS_HORIZONTAL = false
    }
}
