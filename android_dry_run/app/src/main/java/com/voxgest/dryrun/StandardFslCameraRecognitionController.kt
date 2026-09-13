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
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class StandardFslTrackingState {
    READY,
    HOLD_SIGN_CLEARLY
}

data class StandardFslLiveUiState(
    val tracking: StandardFslTrackingState,
    val diagnostics: StandardFslDiagnostics? = null,
    val blockedEvidence: String? = null
)

data class StandardFslAcceptedResult(
    val label: String,
    val confidence: Float,
    val margin: Float,
    val posePresent: Boolean,
    val leftHandPresent: Boolean,
    val rightHandPresent: Boolean,
    val inferenceLatencyMs: Double,
    val top5: List<StandardFslRankedPrediction>
)

/**
 * Standard FullSign225 camera runtime for the normal grading screen.
 *
 * This is intentionally separate from VoxGestCameraRecognitionController:
 * it never loads or falls back to OneHand162. Startup is fail-closed through
 * artifact verification, both golden parity checks, and the activation gate.
 */
class StandardFslCameraRecognitionController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onState: (StandardFslLiveUiState) -> Unit,
    private val onAccepted: (StandardFslAcceptedResult) -> Unit,
    private val initialUseBackCamera: Boolean = false,
    private val requestedCameraSource: CameraSource =
        if (initialUseBackCamera) CameraSource.BACK else CameraSource.FRONT,
    private val initialMirrorFrontPreview: Boolean = true,
    private val onPreviewMirroringChanged: (Boolean) -> Unit = {},
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

    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var boundCamera: Camera? = null
    @Volatile private var cameraStateObserver: Observer<CameraState>? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var runtime: StandardFslTfliteRuntime? = null
    @Volatile private var scaffold: StandardFslRuntimeScaffold? = null
    @Volatile private var lastStateAtMs = 0L
    @Volatile private var lastTracking: StandardFslTrackingState? = null
    private var lastEventLogKey = ""
    private var lastEventLogAtMs = Long.MIN_VALUE

    fun start(previewView: PreviewView) {
        check(!released.get()) { "StandardFslCameraRecognitionController is released" }
        if (!running.compareAndSet(false, true)) return

        performance.reset()
        mainExecutor.execute { Choreographer.getInstance().postFrameCallback(displayFrameCallback) }
        // UX only: ImageAnalysis receives unmirrored frames.
        // AUTO cannot be resolved until CameraX returns its inventory. Keep the
        // presentation neutral until bindCamera publishes the actual source.
        previewView.scaleX = 1f
        onPreviewMirroringChanged(false)
        postState(StandardFslLiveUiState(StandardFslTrackingState.HOLD_SIGN_CLEARLY), force = true)
        analysisExecutor.execute {
            try {
                val parity = StandardFslParityHarness(appContext).run()
                Log.i(TAG, "${parity.feature.marker} ${parity.feature.status} ${parity.feature.evidence}")
                Log.i(TAG, "${parity.tflite.marker} ${parity.tflite.status} ${parity.tflite.evidence}")
                val artifacts = StandardFslArtifactGate.inspect(appContext)
                val activation = GradingProfileActivationGate.standard(
                    artifacts.state,
                    featureParityPassed = parity.feature.status == StandardFslParityStatus.PASS,
                    tfliteParityPassed = parity.tflite.status == StandardFslParityStatus.PASS
                )
                check(activation.state == GradingProfileActivationState.READY) {
                    "STANDARD_FSL activation blocked: ${activation.evidence} ${artifacts.evidence}"
                }
                if (!running.get()) return@execute

                runtime = StandardFslTfliteRuntime(appContext)
                val lens = if (requestedCameraSource == CameraSource.BACK) {
                    GradingCameraLens.BACK
                } else {
                    GradingCameraLens.FRONT
                }
                extractor = StandardFslCameraPipeline.createLandmarkExtractor(
                    context = appContext,
                    lens = lens,
                    onMetrics = performance::recordLandmarks
                )
                scaffold = StandardFslRuntimeScaffold(StandardFslRuntimePolicy.REJECTION_CONFIG)
                Log.i(
                    TAG,
                    "STANDARD_MODEL_LOAD PASS active_profile=STANDARD_FSL_FULLSIGN225 " +
                        "input=[1,20,225] output=[1,105] feature_version=fullsign225_20f_v1 " +
                        "model_input=unmirrored ${artifacts.evidence}"
                )
                Log.i(TAG, "ONEHAND162_FALLBACK PASS preserved=true loaded=false reason=standard_profile_is_ready")
                mainExecutor.execute { bindCamera(previewView) }
            } catch (error: Throwable) {
                fail("STANDARD_MODEL_LOAD", error)
            }
        }
    }

    /** Clears the current rolling window and temporal gate without changing any threshold. */
    fun clear() {
        if (released.get()) return
        analysisExecutor.execute {
            scaffold?.reset()
            eventStateMachine.reset()
            postState(StandardFslLiveUiState(StandardFslTrackingState.READY), force = true)
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        mainExecutor.execute { Choreographer.getInstance().removeFrameCallback(displayFrameCallback) }
        mainExecutor.execute { unbindCamera() }
        analysisExecutor.execute {
            performance.logIfDue(force = true)
            closeRuntimeOnAnalysisThread()
            postState(StandardFslLiveUiState(StandardFslTrackingState.HOLD_SIGN_CLEARLY), force = true)
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
        analysisExecutor.shutdown()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCamera(previewView: PreviewView) {
        if (!running.get()) return
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val resolved = AndroidCameraSourceDiscovery.resolve(
                    provider,
                    requestedCameraSource
                )
                val cameraSource = resolved.resolved
                val previewMirrored = CameraPreviewMirrorPolicy.shouldMirror(
                    cameraSource,
                    initialMirrorFrontPreview
                )
                previewView.scaleX = CameraPreviewMirrorPolicy.previewViewScaleX(
                    cameraSource,
                    initialMirrorFrontPreview
                )
                onPreviewMirroringChanged(previewMirrored)
                val preview = Preview.Builder()
                    .setMirrorMode(
                        CameraPreviewMirrorPolicy.cameraXMirrorMode(
                            cameraSource,
                            initialMirrorFrontPreview
                        )
                    )
                    .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                @Suppress("DEPRECATION")
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(192, 144))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                Camera2Interop.Extender(analysisBuilder).setSessionCaptureCallback(
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
                val analysis = analysisBuilder.build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
                imageAnalysis = analysis
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    resolved.selector,
                    preview,
                    analysis
                )
                Log.i(
                    TAG,
                    "CAMERA_BIND PASS requested=$requestedCameraSource " +
                        "resolved=${resolved.resolved} camera_id=${resolved.cameraId} " +
                        "preview_mirrored=$previewMirrored " +
                        "analysis_mirrored=false model_input=unmirrored"
                )
                val stateObserver = Observer<CameraState> { state ->
                    val error = state.error
                    Log.i(
                        TAG,
                        "STANDARD_CAMERA_STATE type=${state.type} " +
                            "error_code=${error?.code ?: "NONE"}"
                    )
                    if (error != null) {
                        fail(
                            "STANDARD_CAMERA_ASYNC",
                            IllegalStateException(
                                "CameraX state error code=${error.code}",
                                error.cause
                            )
                        )
                    } else if (state.type == CameraState.Type.OPEN) {
                        postState(
                            StandardFslLiveUiState(StandardFslTrackingState.READY),
                            force = true
                        )
                    }
                }
                boundCamera = camera
                cameraStateObserver = stateObserver
                camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
            } catch (error: Throwable) {
                fail("STANDARD_CAMERA_BIND", error)
            }
        }, mainExecutor)
    }

    private fun fail(marker: String, error: Throwable) {
        if (!running.getAndSet(false)) return
        Log.e(TAG, "$marker BLOCKED", error)
        postState(
            StandardFslLiveUiState(
                tracking = StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
            ),
            force = true
        )
        mainExecutor.execute { unbindCamera() }
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.execute { closeRuntimeOnAnalysisThread() }
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!running.get()) return
            performance.recordAnalyzer()
            val frame = extractor?.processFrame(image) ?: return
            val overlayStarted = SystemClock.elapsedRealtimeNanos()
            val overlayPublished = onFrame(frame)
            performance.recordOverlay(
                durationMs = (SystemClock.elapsedRealtimeNanos() - overlayStarted) / NANOS_PER_MS,
                published = overlayPublished
            )
            val localScaffold = scaffold ?: return
            val event = eventStateMachine.onFrame(frame)
            if (event.flushWindow) {
                localScaffold.reset()
            }
            localScaffold.recordEvent(event)
            if (!event.collectFrame) {
                val diagnostics = localScaffold.diagnosticsSnapshot(event.reason, frame.timestampMs)
                postState(
                    StandardFslLiveUiState(StandardFslTrackingState.HOLD_SIGN_CLEARLY, diagnostics),
                    force = event.flushWindow
                )
                logEventRejection(event, diagnostics, frame.timestampMs)
                performance.logIfDue()
                return
            }
            val currentFrameUsable = event.activity.currentFrameUsable
            val temporalStarted = SystemClock.elapsedRealtimeNanos()
            var diagnostics = localScaffold.append(frame).diagnostics
            val window = localScaffold.snapshotForInference()
            performance.recordTemporal(
                (SystemClock.elapsedRealtimeNanos() - temporalStarted) / NANOS_PER_MS
            )
            if (!event.allowInference) {
                localScaffold.cancelTemporalCandidate()
            }
            if (window == null || !event.allowInference) {
                val tracking = if (frame.hasPose && frame.hasAnyHand) {
                    StandardFslTrackingState.READY
                } else {
                    StandardFslTrackingState.HOLD_SIGN_CLEARLY
                }
                postState(StandardFslLiveUiState(tracking, diagnostics))
                performance.logIfDue()
                return
            }

            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val inference = runtime?.infer(window) ?: return
            performance.recordTflite(
                (SystemClock.elapsedRealtimeNanos() - inferenceStarted) / NANOS_PER_MS
            )
            val gateStarted = SystemClock.elapsedRealtimeNanos()
            val update = localScaffold.evaluate(
                inference = inference,
                nowMs = frame.timestampMs,
                currentFrameUsable = currentFrameUsable
            )
            performance.recordGate(
                (SystemClock.elapsedRealtimeNanos() - gateStarted) / NANOS_PER_MS
            )
            diagnostics = update.diagnostics
            val decision = update.decision
            if (decision.accepted) {
                eventStateMachine.markAccepted()
                diagnostics = diagnostics.copy(
                    eventState = StandardFslEventState.ACCEPTED,
                    eventReason = "TOKEN_ACCEPTED"
                )
            } else if (decision.reason == "TEMPORAL_STABILITY") {
                eventStateMachine.markCandidate()
                diagnostics = diagnostics.copy(
                    eventState = StandardFslEventState.CANDIDATE,
                    eventReason = "PREDICTION_CANDIDATE"
                )
            } else {
                eventStateMachine.clearCandidate()
                diagnostics = diagnostics.copy(
                    eventState = eventStateMachine.state,
                    eventReason = "PREDICTION_REJECTED_${decision.reason}"
                )
            }
            val tracking = if (decision.accepted) {
                StandardFslTrackingState.READY
            } else {
                StandardFslTrackingState.HOLD_SIGN_CLEARLY
            }
            postState(StandardFslLiveUiState(tracking, diagnostics))
            Log.i(
                TAG,
                "STANDARD_FSL_LIVE decision=${if (decision.accepted) "ACCEPT" else "REJECT"} " +
                    "reason=${decision.reason} stable_windows=${decision.stableWindowCount} " +
                    "cooldown_active=${decision.cooldownActive} " +
                    "top5=${inference.top5.joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                    diagnostics.toLogLine()
            )
            if (decision.accepted) {
                val accepted = StandardFslAcceptedResult(
                    label = inference.top1.label,
                    confidence = inference.top1.probability,
                    margin = inference.margin,
                    posePresent = diagnostics.posePresent,
                    leftHandPresent = diagnostics.leftHandPresent,
                    rightHandPresent = diagnostics.rightHandPresent,
                    inferenceLatencyMs = inference.latencyMs,
                    top5 = inference.top5
                )
                mainExecutor.execute { onAccepted(accepted) }
            }
            performance.logIfDue()
        } catch (error: Throwable) {
            Log.e(TAG, "STANDARD_FSL_LIVE ERROR", error)
            postState(
                StandardFslLiveUiState(
                    tracking = StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                    blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
                ),
                force = true
            )
        } finally {
            image.close()
        }
    }

    private fun unbindCamera() {
        val camera = boundCamera
        val stateObserver = cameraStateObserver
        if (camera != null && stateObserver != null) {
            camera.cameraInfo.cameraState.removeObserver(stateObserver)
        }
        cameraStateObserver = null
        boundCamera = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    /** Called only on the serialized analysis executor. */
    private fun closeRuntimeOnAnalysisThread() {
        runCatching { extractor?.close() }
        extractor = null
        mainExecutor.execute { onFrame(null) }
        runCatching { runtime?.close() }
        runtime = null
        scaffold = null
        eventStateMachine.reset()
        lastEventLogKey = ""
        lastEventLogAtMs = Long.MIN_VALUE
    }

    private fun logEventRejection(
        event: StandardFslEventUpdate,
        diagnostics: StandardFslDiagnostics,
        nowMs: Long
    ) {
        val key = "${event.state.name}:${event.reason}"
        if (key == lastEventLogKey && nowMs - lastEventLogAtMs < UNCHANGED_EVENT_LOG_INTERVAL_MS) return
        lastEventLogKey = key
        lastEventLogAtMs = nowMs
        Log.i(
            TAG,
            "STANDARD_FSL_EVENT decision=REJECT reason=${event.reason} " +
                "stable_windows=0 cooldown_active=false top5=[] " +
                diagnostics.toLogLine()
        )
    }

    private fun postState(state: StandardFslLiveUiState, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && state.tracking == lastTracking && now - lastStateAtMs < STATE_MIN_INTERVAL_MS) return
        lastTracking = state.tracking
        lastStateAtMs = now
        performance.recordUiState()
        mainExecutor.execute { onState(state) }
    }

    companion object {
        private const val TAG = "VoxGestFullSign225"
        private const val STATE_MIN_INTERVAL_MS = 125L
        private const val NANOS_PER_MS = 1_000_000.0
        private const val UNCHANGED_EVENT_LOG_INTERVAL_MS = 1_000L
    }
}

/** Frozen gate policy used by the previously demonstrated Samsung Standard path. */
object StandardFslRuntimePolicy {
    val REJECTION_CONFIG = StandardFslRejectionConfig(
        confidenceThreshold = 0.7f,
        marginThreshold = 0.2f,
        minimumPosePresenceRatio = 0.65f,
        minimumAnyHandPresenceRatio = 0.65f,
        requiredStableWindows = 2,
        cooldownMs = 1_000L
    )
}
