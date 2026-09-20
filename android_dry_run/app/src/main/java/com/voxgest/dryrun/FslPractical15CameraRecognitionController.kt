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
    private val performance = StandardFslPerformanceTracker()
    private val collector = FslPractical15CompleteEventCollector()
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
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var runtime: FslPractical15TfliteRuntime? = null
    @Volatile private var lastEventLog = ""

    fun start(previewView: PreviewView) {
        check(!released.get())
        if (!running.compareAndSet(false, true)) return
        performance.reset()
        mainExecutor.execute { Choreographer.getInstance().postFrameCallback(displayFrameCallback) }
        previewView.scaleX = if (initialUseBackCamera) 1f else -1f
        postState(FslPractical15LiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, collector.state))
        analysisExecutor.execute {
            try {
                val featureParity = StandardFslParityHarness(appContext).runFeatureParity()
                check(featureParity.status == StandardFslParityStatus.PASS) { featureParity.evidence }
                val loadedRuntime = FslPractical15TfliteRuntime(appContext)
                val modelParity = loadedRuntime.goldenParity()
                check(modelParity.status == StandardFslParityStatus.PASS) { modelParity.evidence }
                if (!running.get()) {
                    loadedRuntime.close()
                    return@execute
                }
                runtime = loadedRuntime
                extractor = StandardFslCameraPipeline.createLandmarkExtractor(
                    appContext,
                    if (initialUseBackCamera) GradingCameraLens.BACK else GradingCameraLens.FRONT,
                    performance::recordLandmarks
                )
                Log.i(TAG, "${featureParity.marker} ${featureParity.status} ${featureParity.evidence}")
                Log.i(TAG, "${modelParity.marker} ${modelParity.status} ${modelParity.evidence}")
                Log.i(
                    TAG,
                    "FSL_PRACTICAL15_MODEL_LOAD PASS active_profile=${FslPractical15Profile.ID} " +
                        "input=[1,48,225] output=[1,15] labels=${loadedRuntime.profile.labels.size} " +
                        "feature=fullsign225_frame_v1_complete_trajectory_v1 temporal=complete_event_resample48 " +
                        "model_input=unmirrored threshold_confidence=0.95 threshold_margin=0.05 " +
                        "live_approved=false android_default_changed=false"
                )
                mainExecutor.execute { bindCamera(previewView) }
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
        if (!running.getAndSet(false)) return
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
        analysisExecutor.shutdown()
    }

    private fun bindCamera(previewView: PreviewView) {
        if (!running.get()) return
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (!running.get()) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                @Suppress("DEPRECATION")
                val builder = ImageAnalysis.Builder()
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
                analysis.setAnalyzer(analysisExecutor) { analyze(it) }
                imageAnalysis = analysis
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    if (initialUseBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                Log.i(
                    TAG,
                    "FSL_PRACTICAL15_CAMERA_BIND PASS lens=${if (initialUseBackCamera) "BACK" else "FRONT"} " +
                        "preview_mirrored=${!initialUseBackCamera} analysis_mirrored=false model_input=unmirrored"
                )
                postState(FslPractical15LiveState(StandardFslTrackingState.READY, collector.state))
            } catch (error: Throwable) {
                running.set(false)
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
        try {
            if (!running.get()) return
            performance.recordAnalyzer()
            val frame = extractor?.processFrame(image) ?: return
            val overlayStarted = SystemClock.elapsedRealtimeNanos()
            val published = onFrame(frame)
            performance.recordOverlay((SystemClock.elapsedRealtimeNanos() - overlayStarted) / 1_000_000.0, published)
            val update = collector.onFrame(frame)
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
            Log.i(
                TAG,
                "FSL_PRACTICAL15_EVENT event=${candidate.eventNumber} completion=NEUTRAL_RELEASE " +
                    "captured_frames=${candidate.quality.rawFrameCount} resample=exact48 " +
                    "pose=${candidate.quality.posePresentFrames} left=${candidate.quality.leftHandPresentFrames} " +
                    "right=${candidate.quality.rightHandPresentFrames} " +
                    "top5=${inference.top5.joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                    "raw_top1=${inference.top1.label} probability=${inference.top1.probability} margin=${inference.margin} " +
                    "final=${if (decision.accepted) "ACCEPT" else "REJECT"} reason=${decision.reason} " +
                    "tflite_ms=${inference.latencyMs} sign_end_to_raw_ms=$endToRawMs"
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
            performance.logIfDue()
        } catch (error: Throwable) {
            Log.e(TAG, "FSL_PRACTICAL15_LIVE ERROR", error)
            postState(
                FslPractical15LiveState(
                    StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                    collector.state,
                    blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
                )
            )
        } finally {
            image.close()
        }
    }

    private fun logCaptureTransition(update: FslPractical15CaptureUpdate) {
        val key = "${update.state}:${update.reason}"
        if (key == lastEventLog) return
        lastEventLog = key
        Log.i(TAG, "FSL_PRACTICAL15_CAPTURE state=${update.state} reason=${update.reason}")
    }

    private fun postState(state: FslPractical15LiveState) = mainExecutor.execute { onState(state) }

    private fun unbindCamera() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun closeRuntime() {
        runCatching { extractor?.close() }
        extractor = null
        mainExecutor.execute { onFrame(null) }
        runCatching { runtime?.close() }
        runtime = null
        collector.reset()
        lastEventLog = ""
    }

    companion object {
        private const val TAG = "VoxGestPractical15"
    }
}
