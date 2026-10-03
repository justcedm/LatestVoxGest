package com.voxgest.dryrun

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Choreographer
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class FslPractical15LiveState(
    val tracking: StandardFslTrackingState,
    val captureState: FslPractical15CaptureState,
    val inference: StandardFslInference? = null,
    val decision: StandardFslGateDecision? = null,
    val quality: FslPractical15EventQuality? = null,
    val blockedEvidence: String? = null
)

/** Debug-intent-only complete-event camera lane; never selected by the production default. */
class FslPractical15CameraRecognitionController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onState: (FslPractical15LiveState) -> Unit,
    private val onAccepted: (StandardFslAcceptedResult) -> Unit,
    private val initialUseBackCamera: Boolean = false,
    private val onFrame: (LandmarkFrame?) -> Boolean = { false }
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val sessionId = java.util.concurrent.atomic.AtomicLong(0)
    private val performance = StandardFslPerformanceTracker()
    private val captureConfig = FslPractical15CaptureConfig()
    private val collector = FslPractical15CompleteEventCollector(captureConfig)
    private val gate = FslPractical15Gate()
    private val displayFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running.get()) return
            performance.recordDisplayVsync(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    private var cameraState: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var experimentalPipeline: ExperimentalLiveStreamLandmarks? = null
    private var adapterName = "VIDEO_DEFAULT"
    private var lastAnatomyLogMs = 0L
    @Volatile private var runtime: FslPractical15TfliteRuntime? = null
    @Volatile private var latestLandmarkMetrics: LandmarkExtractionMetrics? = null
    @Volatile private var lastEventLog = ""
    private val eventMediaPipeMs = mutableListOf<Double>()
    private var domainCapture: DomainCCapture? = null
    @Volatile private var previewHost: PreviewView? = null
    @Volatile private var previewMirror = !initialUseBackCamera
    private var lifecycleObserved = false
    private var resumeAfterStop = false
    private val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
        when (event) {
            androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                val shouldResume = running.get()
                stop()
                resumeAfterStop = shouldResume
            }
            androidx.lifecycle.Lifecycle.Event.ON_START -> {
                if (resumeAfterStop && !released.get()) previewHost?.let { start(it) }
                resumeAfterStop = false
            }
            else -> Unit
        }
    }

    fun start(previewView: PreviewView) {
        check(!released.get())
        if (!running.compareAndSet(false, true)) return
        val session = sessionId.incrementAndGet()
        performance.reset()
        mainExecutor.execute { Choreographer.getInstance().postFrameCallback(displayFrameCallback) }
        // CameraX PreviewView already applies the front-camera display mirror.
        // A second scaleX=-1 here cancels that mirror and puts display-only
        // landmarks on the opposite side of the person. Keep analysis/model
        // input untouched and let PreviewView own the camera transform.
        previewHost = previewView
        if (!lifecycleObserved) {
            lifecycleObserved = true
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        }
        previewMirror = !initialUseBackCamera && previewView.scaleX > 0f
        postState(FslPractical15LiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, collector.state))
        analysisExecutor.execute {
            try {
                val featureParity = StandardFslParityHarness(appContext).runFeatureParity()
                check(featureParity.status == StandardFslParityStatus.PASS) { featureParity.evidence }
                val loadedRuntime = FslPractical15TfliteRuntime(appContext)
                val modelParity = loadedRuntime.goldenParity()
                check(modelParity.status == StandardFslParityStatus.PASS) { modelParity.evidence }
                if (!running.get() || sessionId.get() != session) {
                    loadedRuntime.close()
                    return@execute
                }
                runtime = loadedRuntime
                domainCapture = DomainCCapture.openIfEnabled(appContext)
                val liveStreamEnabled = BuildConfig.DEBUG &&
                    java.io.File(appContext.filesDir, "practical15_live_stream.enabled").isFile
                adapterName = if (liveStreamEnabled) "LIVE_STREAM_EXPERIMENT" else "VIDEO_DEFAULT"
                if (liveStreamEnabled) {
                    experimentalPipeline = ExperimentalLiveStreamLandmarks(appContext,
                        onFrame = { frame ->
                            // Wait for the serial consumer before admitting another pair: bounded delivery.
                            if (running.get() && sessionId.get() == session && !analysisExecutor.isShutdown) {
                                analysisExecutor.submit { handleFrame(frame, session) }
                                    .get(2, java.util.concurrent.TimeUnit.SECONDS)
                            }
                        },
                        onMetrics = { metrics ->
                            if (!analysisExecutor.isShutdown) analysisExecutor.execute {
                                val timing = LandmarkExtractionMetrics(metrics.timestamp * 1_000_000L,
                                    null, 0.0, metrics.handMs.toDouble(), metrics.poseMs.toDouble(), metrics.pairMs.toDouble())
                                latestLandmarkMetrics = timing
                                performance.recordLandmarks(timing)
                                Log.i(TAG, "FSL_PRACTICAL15_ASYNC timestamp=${metrics.timestamp} " +
                                    "pair_ms=${metrics.pairMs} dropped_latest=${metrics.replacedFrames}")
                            }
                        },
                        onError = { reason -> mainExecutor.execute {
                            Log.e(TAG, "FSL_PRACTICAL15_ASYNC BLOCKED reason=$reason")
                            postState(FslPractical15LiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                                collector.state, blockedEvidence = reason))
                            stop()
                        } })
                } else {
                    extractor = MediaPipeLandmarkExtractor(appContext,
                        mirrorCameraFrame = Practical15TasksAnatomy.ANALYSIS_MIRRORED,
                        practical15AnatomicalSlots = true,
                        onMetrics = { metrics -> latestLandmarkMetrics = metrics; performance.recordLandmarks(metrics) })
                }
                Log.i(TAG, "FSL_PRACTICAL15_ADAPTER adapter=$adapterName default=VIDEO_DEFAULT " +
                    "handedness=${Practical15TasksAnatomy.POLICY} temporal=complete_event_resample48")
                Log.i(TAG, "FSL_PRACTICAL15_LABELS ${loadedRuntime.profile.labels.joinToString(",")}")
                Log.i(TAG, "${featureParity.marker} ${featureParity.status} ${featureParity.evidence}")
                Log.i(TAG, "${modelParity.marker} ${modelParity.status} ${modelParity.evidence}")
                Log.i(
                    TAG,
                    "FSL_PRACTICAL15_MODEL_LOAD PASS active_profile=${FslPractical15Profile.ID} " +
                        "input=[1,48,225] output=[1,15] labels=${loadedRuntime.profile.labels.size} " +
                        "feature=fullsign225_frame_v1_complete_trajectory_v1 temporal=complete_event_resample48 " +
                        "model_input=unmirrored threshold_confidence=0.95 threshold_margin=0.05 " +
                        "minimum_trajectory_motion_mean_l2=0.02 " +
                        "live_approved=false android_default_changed=false"
                )
                Log.i(
                    TAG,
                    "FSL_PRACTICAL15_CAPTURE_CONFIG PASS " +
                        "neutral_arm_frames=${captureConfig.neutralArmFrames} " +
                        "release_frames=${captureConfig.releaseFrames} " +
                        "minimum_raw_event_frames=${captureConfig.minimumEventFrames} " +
                        "maximum_event_frames=${captureConfig.maximumEventFrames} " +
                        "maximum_event_duration_ms=${captureConfig.maximumEventDurationMs} " +
                        "maximum_consecutive_missing_pose=${captureConfig.maximumConsecutiveMissingPose} " +
                        "minimum_pose_presence_ratio=0.65 minimum_any_hand_presence_ratio=0.65"
                )
                mainExecutor.execute { bindCamera(previewView, session) }
            } catch (error: Throwable) {
                Log.e(TAG, "FSL_PRACTICAL15_MODEL_LOAD BLOCKED", error)
                running.set(false)
                closeRuntime()
                postState(
                    FslPractical15LiveState(
                        StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                        collector.state,
                        blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
                    )
                )
                mainExecutor.execute { unbindCamera() }
            }
        }
    }

    fun stop() {
        resumeAfterStop = false
        running.set(false)
        sessionId.incrementAndGet()
        if (analysisExecutor.isShutdown) return
        mainExecutor.execute { Choreographer.getInstance().removeFrameCallback(displayFrameCallback) }
        mainExecutor.execute { unbindCamera() }
        analysisExecutor.execute {
            performance.logIfDue(force = true)
            closeRuntime()
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        previewHost = null
        analysisExecutor.shutdown()
    }

    private fun bindCamera(previewView: PreviewView, session: Long) {
        if (!running.get() || sessionId.get() != session) return
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (!running.get() || sessionId.get() != session) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val selector = if (initialUseBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                check(provider.hasCamera(selector)) { "Requested camera unavailable; select the other lens explicitly" }
                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                @Suppress("DEPRECATION")
                val builder = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(192, 144))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                Camera2Interop.Extender(builder).setSessionCaptureCallback(
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            performance.recordCameraCapture()
                        }
                    }
                )
                val analysis = builder.build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    // On activity rotation CameraX recreates this lane. Do not infer with stale display geometry.
                    mainExecutor.execute { previewMirror = !initialUseBackCamera && previewView.scaleX > 0f }
                    analyze(image)
                }
                imageAnalysis = analysis
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analysis
                )
                cameraState = camera.cameraInfo.cameraState
                cameraState?.observe(lifecycleOwner) { state ->
                    if (state.error != null && running.get() && sessionId.get() == session) {
                        postState(FslPractical15LiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                            collector.state, blockedEvidence = "CAMERA_UNAVAILABLE error_code=${state.error?.code}"))
                        stop() // Fail closed. Explicit camera restart is available in the existing UI.
                    }
                }
                Log.i(
                    TAG,
                    "FSL_PRACTICAL15_CAMERA_BIND PASS lens=${if (initialUseBackCamera) "BACK" else "FRONT"} " +
                        "preview_mirrored=${!initialUseBackCamera} analysis_mirrored=false model_input=unmirrored"
                )
                postState(FslPractical15LiveState(StandardFslTrackingState.READY, collector.state))
            } catch (error: Throwable) {
                running.set(false)
                unbindCamera()
                analysisExecutor.execute { closeRuntime() }
                Log.e(TAG, "FSL_PRACTICAL15_CAMERA_BIND BLOCKED", error)
                postState(
                    FslPractical15LiveState(
                        StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                        collector.state,
                        blockedEvidence = error.message
                    )
                )
            }
        }, mainExecutor)
    }

    private fun analyze(image: ImageProxy) {
        val analysisSession = sessionId.get()
        try {
            if (!running.get()) return
            performance.recordAnalyzer()
            val inverse = android.graphics.Matrix()
            val transform = if (image.imageInfo.sensorToBufferTransformMatrix.invert(inverse))
                FloatArray(9).also { inverse.getValues(it) } else null
            val metadata = CameraFrameMetadata(
                image.imageInfo.timestamp, image.imageInfo.rotationDegrees, image.width, image.height,
                transform, if (initialUseBackCamera) "BACK" else "FRONT", previewMirror
            )
            experimentalPipeline?.let { it.submit(image, metadata); return }
            val rawFrame = extractor?.processFrame(image) ?: return
            handleFrame(rawFrame.copy(cameraMetadata = metadata), analysisSession)
        } catch (error: Throwable) {
            frameError(error)
        } finally {
            image.close()
        }
    }

    /** Both adapters feed the very same frozen collector, normalizer, model and gate. */
    private fun handleFrame(frame: LandmarkFrame, analysisSession: Long) {
        try {
            if (!running.get() || sessionId.get() != analysisSession) return
            if (frame.timestampMs - lastAnatomyLogMs >= 1000) {
                lastAnatomyLogMs = frame.timestampMs
                Log.i(TAG, "FSL_PRACTICAL15_ANATOMY adapter=$adapterName timestamp=${frame.timestampMs} " +
                    "pose=${frame.hasPose} left=${frame.hasLeftHand} right=${frame.hasRightHand} " +
                    "assignments=${frame.handObservations.joinToString { "${it.mediaPipeHandedness}->${it.slot}:${it.handednessScore}" }} " +
                    "analysis_mirrored=false")
            }
            domainCapture?.frame(frame) // Raw Tasks coordinates, before the collector normalizes them.
            val overlayStarted = SystemClock.elapsedRealtimeNanos()
            val published = onFrame(frame)
            performance.recordOverlay((SystemClock.elapsedRealtimeNanos() - overlayStarted) / 1_000_000.0, published)
            val update = collector.onFrame(frame)
            domainCapture?.update(update)
            when {
                update.reason == "SIGN_ENTRY" -> {
                    eventMediaPipeMs.clear()
                    latestLandmarkMetrics?.totalMs?.let(eventMediaPipeMs::add)
                }
                update.state == FslPractical15CaptureState.SIGN_ACTIVE ->
                    latestLandmarkMetrics?.totalMs?.let(eventMediaPipeMs::add)
                update.state == FslPractical15CaptureState.WAIT_FOR_RELEASE ->
                    eventMediaPipeMs.clear()
            }
            logCaptureTransition(update)
            val candidate = update.candidate
            if (candidate == null) {
                postState(
                    FslPractical15LiveState(
                        if (frame.hasPose) StandardFslTrackingState.READY else StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                        update.state
                    )
                )
                performance.logIfDue()
                return
            }
            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val inference = runtime?.infer(candidate.resampledWindow) ?: return
            performance.recordTflite((SystemClock.elapsedRealtimeNanos() - inferenceStarted) / 1_000_000.0)
            val decision = gate.evaluate(inference, candidate.quality)
            collector.markCandidateHandled()
            val now = SystemClock.elapsedRealtime()
            val endToRawMs = (now - candidate.quality.endTimestampMs).coerceAtLeast(0L)
            val mediaPipeMedianMs = percentile(eventMediaPipeMs, 0.50)
            val mediaPipeP95Ms = percentile(eventMediaPipeMs, 0.95)
            Log.i(
                TAG,
                "FSL_PRACTICAL15_EVENT event=${candidate.eventNumber} adapter=$adapterName completion=NEUTRAL_RELEASE " +
                    "event_duration_ms=${candidate.quality.endTimestampMs - candidate.quality.startTimestampMs} " +
                    "captured_frames=${candidate.quality.rawFrameCount} resample=exact48 " +
                    "pose=${candidate.quality.posePresentFrames} left=${candidate.quality.leftHandPresentFrames} " +
                    "right=${candidate.quality.rightHandPresentFrames} " +
                    "any_hand=${candidate.quality.anyHandPresentFrames} " +
                    "effective_window_seconds=${(candidate.quality.endTimestampMs - candidate.quality.startTimestampMs) / 1000.0} " +
                    "event_sample_fps=${(candidate.quality.rawFrameCount - 1) * 1000.0 / (candidate.quality.endTimestampMs - candidate.quality.startTimestampMs).coerceAtLeast(1)} " +
                    "trajectory_motion_mean_l2=${candidate.quality.trajectoryMotionMeanL2} " +
                    "top5=${inference.top5.joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                    "raw_top1=${inference.top1.label} probability=${inference.top1.probability} margin=${inference.margin} " +
                    "final=${if (decision.accepted) "ACCEPT" else "REJECT"} reason=${decision.reason} " +
                    "mediapipe_ms_median=$mediaPipeMedianMs mediapipe_ms_p95=$mediaPipeP95Ms " +
                    "tflite_ms=${inference.latencyMs} sign_end_to_raw_ms=$endToRawMs " +
                    "sign_end_to_accepted_ms=${if (decision.accepted) endToRawMs.toString() else "NA"}"
            )
            postState(
                FslPractical15LiveState(
                    if (decision.accepted) StandardFslTrackingState.READY else StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                    collector.state,
                    inference,
                    decision,
                    candidate.quality
                )
            )
            if (decision.accepted) {
                mainExecutor.execute {
                    if (!running.get() || sessionId.get() != analysisSession) return@execute
                    onAccepted(
                        StandardFslAcceptedResult(
                            inference.top1.label,
                            inference.top1.probability,
                            inference.margin,
                            candidate.quality.posePresentFrames > 0,
                            candidate.quality.leftHandPresentFrames > 0,
                            candidate.quality.rightHandPresentFrames > 0,
                            inference.latencyMs,
                            inference.top5
                        )
                    )
                }
            }
            eventMediaPipeMs.clear()
            performance.logIfDue()
        } catch (error: Throwable) {
            frameError(error)
        }
    }

    private fun frameError(error: Throwable) {
            Log.e(TAG, "FSL_PRACTICAL15_LIVE ERROR", error)
            collector.reset() // Never carry a half-event across a failed detector/inference call.
            eventMediaPipeMs.clear()
            postState(
                FslPractical15LiveState(
                    StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                    collector.state,
                    blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
                )
            )
    }

    private fun logCaptureTransition(update: FslPractical15CaptureUpdate) {
        val key = "${update.state}:${update.reason}"
        if (key == lastEventLog) return
        lastEventLog = key
        Log.i(TAG, "FSL_PRACTICAL15_CAPTURE state=${update.state} reason=${update.reason}")
    }

    private fun postState(state: FslPractical15LiveState) = mainExecutor.execute { onState(state) }

    private fun unbindCamera() {
        cameraState?.removeObservers(lifecycleOwner)
        cameraState = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun closeRuntime() {
        experimentalPipeline?.close()
        experimentalPipeline = null
        runCatching { extractor?.close() }
        extractor = null
        mainExecutor.execute { onFrame(null) }
        runCatching { runtime?.close() }
        runtime = null
        domainCapture?.close()
        domainCapture = null
        collector.reset()
        latestLandmarkMetrics = null
        eventMediaPipeMs.clear()
        lastEventLog = ""
        lastAnatomyLogMs = 0L
    }

    private fun percentile(values: List<Double>, fraction: Double): String {
        if (values.isEmpty()) return "NA"
        val sorted = values.filter { it.isFinite() && it >= 0.0 }.sorted()
        if (sorted.isEmpty()) return "NA"
        val index = ((sorted.lastIndex) * fraction).toInt().coerceIn(sorted.indices)
        return String.format(java.util.Locale.US, "%.3f", sorted[index])
    }

    companion object {
        private const val TAG = "VoxGestPractical15"
    }
}
