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

data class Mapua14RescueLiveState(
    val tracking: StandardFslTrackingState,
    val inference: StandardFslInference? = null,
    val decision: StandardFslGateDecision? = null,
    val quality: StandardFullSign225WindowQuality? = null,
    val timing: StandardFullSign225WindowTiming? = null,
    val blockedEvidence: String? = null
)

/**
 * Debug/diagnostic-only live lane for MAPUA14_RESCUE_V1.
 *
 * It shares canonical unmirrored frame math and latest-frame camera policy with
 * Standard, but owns its model, dynamic rolling window, and acceptance state.
 * No failure can fall through to Standard or Legacy Demo under this identity.
 */
class Mapua14RescueCameraRecognitionController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onState: (Mapua14RescueLiveState) -> Unit,
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
    private val eventStateMachine = StandardFslEventStateMachine()
    private val displayFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running.get()) return
            performance.recordDisplayVsync(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Volatile private var profile: Mapua14RescueProfile? = null
    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var runtime: Mapua14RescueTfliteRuntime? = null
    @Volatile private var window: Mapua14RescueWindow? = null
    @Volatile private var gate: Mapua14RescueGate? = null

    fun start(previewView: PreviewView) {
        check(!released.get()) { "Mapua14RescueCameraRecognitionController is released" }
        if (!running.compareAndSet(false, true)) return
        performance.reset()
        mainExecutor.execute { Choreographer.getInstance().postFrameCallback(displayFrameCallback) }
        previewView.scaleX = if (initialUseBackCamera) 1f else -1f
        postState(Mapua14RescueLiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY))
        analysisExecutor.execute {
            try {
                val featureParity = StandardFslParityHarness(appContext).runFeatureParity()
                check(featureParity.status == StandardFslParityStatus.PASS) { featureParity.evidence }
                val loadedRuntime = Mapua14RescueTfliteRuntime(appContext)
                val tfliteParity = loadedRuntime.goldenParity()
                check(tfliteParity.status == StandardFslParityStatus.PASS) { tfliteParity.evidence }
                if (!running.get()) {
                    loadedRuntime.close()
                    return@execute
                }
                val loadedProfile = loadedRuntime.profile
                profile = loadedProfile
                runtime = loadedRuntime
                window = Mapua14RescueWindow(loadedProfile.sequenceLength)
                gate = Mapua14RescueGate(loadedProfile.sequenceLength)
                extractor = StandardFslCameraPipeline.createLandmarkExtractor(
                    context = appContext,
                    lens = if (initialUseBackCamera) GradingCameraLens.BACK else GradingCameraLens.FRONT,
                    onMetrics = performance::recordLandmarks
                )
                Log.i(TAG, "${featureParity.marker} ${featureParity.status} ${featureParity.evidence}")
                Log.i(TAG, "${tfliteParity.marker} ${tfliteParity.status} ${tfliteParity.evidence}")
                Log.i(
                    TAG,
                    "MAPUA14_MODEL_LOAD PASS active_profile=${Mapua14RescueProfile.ID} " +
                        "input=${loadedProfile.inputShape.contentToString()} output=${loadedProfile.outputShape.contentToString()} " +
                        "labels=${loadedProfile.labels} model_input=unmirrored android_default_changed=false"
                )
                mainExecutor.execute { bindCamera(previewView) }
            } catch (error: Throwable) {
                Log.e(TAG, "MAPUA14_MODEL_LOAD BLOCKED", error)
                running.set(false)
                closeRuntime()
                postState(Mapua14RescueLiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"))
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
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
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
                Log.i(TAG, "MAPUA14_CAMERA_BIND PASS preview_mirrored=${!initialUseBackCamera} analysis_mirrored=false backpressure=KEEP_ONLY_LATEST")
                postState(Mapua14RescueLiveState(StandardFslTrackingState.READY))
            } catch (error: Throwable) {
                running.set(false)
                Log.e(TAG, "MAPUA14_CAMERA_BIND BLOCKED", error)
                postState(Mapua14RescueLiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, blockedEvidence = error.message))
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
            val localWindow = window ?: return
            val localGate = gate ?: return
            val event = eventStateMachine.onFrame(frame)
            if (event.flushWindow) {
                localWindow.clear()
                localGate.reset()
            }
            if (!event.collectFrame) {
                logEventRejection(event)
                postState(Mapua14RescueLiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY))
                performance.logIfDue()
                return
            }
            val temporalStarted = SystemClock.elapsedRealtimeNanos()
            localWindow.add(frame)
            performance.recordTemporal((SystemClock.elapsedRealtimeNanos() - temporalStarted) / 1_000_000.0)
            if (!event.allowInference) localGate.cancelCandidate()
            val snapshot = localWindow.snapshot()
            if (snapshot == null || !event.allowInference) {
                postState(Mapua14RescueLiveState(if (frame.hasPose && frame.hasAnyHand) StandardFslTrackingState.READY else StandardFslTrackingState.HOLD_SIGN_CLEARLY))
                performance.logIfDue()
                return
            }
            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val inference = runtime?.infer(snapshot) ?: return
            performance.recordTflite((SystemClock.elapsedRealtimeNanos() - inferenceStarted) / 1_000_000.0)
            val quality = localWindow.quality()
            val timing = localWindow.timing(frame.timestampMs)
            val gateStarted = SystemClock.elapsedRealtimeNanos()
            val decision = localGate.evaluate(inference, quality, timing, event.activity.currentFrameUsable)
            performance.recordGate((SystemClock.elapsedRealtimeNanos() - gateStarted) / 1_000_000.0)
            if (decision.accepted) eventStateMachine.markAccepted()
            else if (decision.reason == "TEMPORAL_STABILITY") eventStateMachine.markCandidate()
            else eventStateMachine.clearCandidate()
            Log.i(
                TAG,
                "MAPUA14_LIVE raw_top1=${inference.top1.label} raw_top1_score=${inference.top1.probability} " +
                    "top3=${inference.top5.take(3).joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                    "tracking_pose=${quality.posePresenceRatio} tracking_any_hand=${quality.anyHandPresenceRatio} " +
                    "window_duration_ms=${timing.windowDurationMs} final=${if (decision.accepted) "ACCEPT" else "REJECT"} " +
                    "reason=${decision.reason} tflite_ms=${inference.latencyMs}"
            )
            postState(Mapua14RescueLiveState(if (decision.accepted) StandardFslTrackingState.READY else StandardFslTrackingState.HOLD_SIGN_CLEARLY, inference, decision, quality, timing))
            if (decision.accepted) {
                val accepted = StandardFslAcceptedResult(
                    inference.top1.label, inference.top1.probability, inference.margin,
                    quality.posePresentFrames > 0, quality.leftHandPresentFrames > 0, quality.rightHandPresentFrames > 0,
                    inference.latencyMs, inference.top5
                )
                mainExecutor.execute { onAccepted(accepted) }
            }
            performance.logIfDue()
        } catch (error: Throwable) {
            Log.e(TAG, "MAPUA14_LIVE ERROR", error)
            postState(Mapua14RescueLiveState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"))
        } finally {
            image.close()
        }
    }

    private fun logEventRejection(event: StandardFslEventUpdate) {
        Log.i(
            TAG,
            "MAPUA14_EVENT final=REJECT reason=${event.reason} raw_top1=NOT_RUN " +
                "activity=${event.activity.activityScore} freshness=${event.activity.recentValidFrameRatio}"
        )
    }

    private fun postState(state: Mapua14RescueLiveState) = mainExecutor.execute { onState(state) }

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
        window = null
        gate = null
        profile = null
        eventStateMachine.reset()
    }

    companion object {
        private const val TAG = "VoxGestMapua14"
    }
}
