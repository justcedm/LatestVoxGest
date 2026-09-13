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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Mapua14LiveSegmentLiveState(
    val tracking: StandardFslTrackingState,
    val segmentState: Mapua14LiveSegmentState,
    val segmentReason: String,
    val inference: StandardFslInference? = null,
    val decision: StandardFslGateDecision? = null,
    val quality: StandardFullSign225WindowQuality? = null,
    val timing: StandardFullSign225WindowTiming? = null,
    val postEndTiming: Mapua14PostEndResultTiming? = null,
    val cameraSource: CameraSource? = null,
    val blockedEvidence: String? = null
)

/**
 * Experimental successor to the rollback rolling48 lane.
 *
 * Each event is captured once, finalized with the audited complete-trajectory
 * policy, resampled to 48, and inferred once by the unchanged RD-TCN48 model.
 */
class Mapua14LiveSegmentCameraRecognitionController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onState: (Mapua14LiveSegmentLiveState) -> Unit,
    private val onAccepted: (StandardFslAcceptedResult) -> Unit,
    private val requestedCameraSource: CameraSource = CameraSource.FRONT,
    private val mirrorFrontPreview: Boolean = true,
    private val onPreviewMirroringChanged: (Boolean) -> Unit = {},
    private val onFrame: (LandmarkFrame?) -> Boolean = { false }
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val performance = StandardFslPerformanceTracker()
    private val segmentMachine = Mapua14LiveSegmentStateMachine()
    private val handIdentity = TemporalAnatomicalHandIdentityStabilizer(
        ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
    )
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
    @Volatile private var runtime: Mapua14RescueTfliteRuntime? = null
    @Volatile private var resolvedCameraSource: ResolvedCameraSource? = null
    @Volatile private var latestHandDiagnostics: TemporalHandIdentityDiagnostics? = null
    private var eventOrdinal = 0L
    private var lastSegmentLogKey = ""
    private var lastSegmentLogAtMs = Long.MIN_VALUE
    private var lastHandLogAtMs = Long.MIN_VALUE

    fun start(previewView: PreviewView) {
        check(!released.get()) {
            "Mapua14LiveSegmentCameraRecognitionController is released"
        }
        if (!running.compareAndSet(false, true)) return
        performance.reset()
        mainExecutor.execute {
            Choreographer.getInstance().postFrameCallback(displayFrameCallback)
        }
        postState(
            Mapua14LiveSegmentLiveState(
                tracking = StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                segmentState = Mapua14LiveSegmentState.IDLE,
                segmentReason = "STARTING"
            )
        )
        analysisExecutor.execute {
            try {
                val featureParity = StandardFslParityHarness(appContext).runFeatureParity()
                check(featureParity.status == StandardFslParityStatus.PASS) {
                    featureParity.evidence
                }
                val loadedRuntime = Mapua14RescueTfliteRuntime(appContext)
                val tfliteParity = loadedRuntime.goldenParity()
                check(tfliteParity.status == StandardFslParityStatus.PASS) {
                    tfliteParity.evidence
                }
                check(loadedRuntime.profile.sequenceLength == Mapua14CompleteTrajectory48.OUTPUT_LENGTH) {
                    "live segment profile requires RD-TCN48; actual=${loadedRuntime.profile.sequenceLength}"
                }
                if (!running.get()) {
                    loadedRuntime.close()
                    return@execute
                }
                runtime = loadedRuntime
                Log.i(TAG, "${featureParity.marker} ${featureParity.status} ${featureParity.evidence}")
                Log.i(TAG, "${tfliteParity.marker} ${tfliteParity.status} ${tfliteParity.evidence}")
                Log.i(
                    TAG,
                    "MAPUA14_SEGMENT_MODEL_LOAD PASS runtime_profile=${Mapua14LiveSegmentProfile.ID} " +
                        "model_profile=${Mapua14RescueProfile.ID} input=[1,48,225] output=[1,14] " +
                        "model_unchanged=true model_input=canonical_unmirrored " +
                        "temporal_policy=complete_trajectory_linear_resample48 android_default_changed=false"
                )
                mainExecutor.execute { discoverCamera(previewView) }
            } catch (error: Throwable) {
                fail("MAPUA14_SEGMENT_MODEL_LOAD", error)
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        mainExecutor.execute {
            Choreographer.getInstance().removeFrameCallback(displayFrameCallback)
            unbindCamera()
        }
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

    private fun discoverCamera(previewView: PreviewView) {
        if (!running.get()) return
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (!running.get()) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val available = AndroidCameraSourceDiscovery.enumerate(provider)
                Log.i(
                    TAG,
                    "MAPUA14_CAMERA_DISCOVERY requested=$requestedCameraSource available=$available"
                )
                val resolved = AndroidCameraSourceDiscovery.resolve(
                    provider,
                    requestedCameraSource
                )
                resolvedCameraSource = resolved
                val previewMirrored = CameraPreviewMirrorPolicy.shouldMirror(
                    resolved.resolved,
                    mirrorFrontPreview
                )
                previewView.scaleX = CameraPreviewMirrorPolicy.previewViewScaleX(
                    resolved.resolved,
                    mirrorFrontPreview
                )
                onPreviewMirroringChanged(previewMirrored)
                analysisExecutor.execute {
                    try {
                        extractor = MediaPipeLandmarkExtractor(
                            context = appContext,
                            mirrorCameraFrame = false,
                            reportedHandednessPolicy =
                                ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT,
                            onMetrics = performance::recordLandmarks,
                            handIdentityStabilizer = handIdentity,
                            onHandIdentityDiagnostics = ::recordHandIdentity
                        )
                        mainExecutor.execute {
                            bindResolvedCamera(previewView, provider, resolved, previewMirrored)
                        }
                    } catch (error: Throwable) {
                        fail("MAPUA14_SEGMENT_LANDMARK_LOAD", error)
                    }
                }
            } catch (error: Throwable) {
                fail("MAPUA14_CAMERA_DISCOVERY", error)
            }
        }, mainExecutor)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindResolvedCamera(
        previewView: PreviewView,
        provider: ProcessCameraProvider,
        resolved: ResolvedCameraSource,
        previewMirrored: Boolean
    ) {
        if (!running.get()) return
        try {
            val preview = Preview.Builder()
                .setMirrorMode(
                    CameraPreviewMirrorPolicy.cameraXMirrorMode(
                        resolved.resolved,
                        mirrorFrontPreview
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
            analysis.setAnalyzer(analysisExecutor, ::analyze)
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
                "MAPUA14_CAMERA_BIND STARTED requested=${resolved.requested} " +
                    "resolved=${resolved.resolved} camera_id=${resolved.cameraId} " +
                    "preview_mirrored=$previewMirrored analysis_mirrored=false " +
                    "backpressure=KEEP_ONLY_LATEST pipeline=Preview+ImageAnalysis+MediaPipe"
            )
            camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                val error = state.error
                Log.i(
                    TAG,
                    "MAPUA14_CAMERA_STATE type=${state.type} " +
                        "error_code=${error?.code ?: "NONE"} " +
                        "error_cause=${error?.cause?.javaClass?.simpleName ?: "NONE"}"
                )
                if (error != null) {
                    fail(
                        "MAPUA14_CAMERA_ASYNC",
                        IllegalStateException(
                            "CameraX state error code=${error.code}",
                            error.cause
                        )
                    )
                } else if (state.type == CameraState.Type.OPEN) {
                    Log.i(
                        TAG,
                        "MAPUA14_CAMERA_BIND PASS camera_state=OPEN " +
                            "requested=${resolved.requested} resolved=${resolved.resolved}"
                    )
                    postState(
                        Mapua14LiveSegmentLiveState(
                            tracking = StandardFslTrackingState.READY,
                            segmentState = segmentMachine.state,
                            segmentReason = "CAMERA_READY",
                            cameraSource = resolved.resolved
                        )
                    )
                }
            }
        } catch (error: Throwable) {
            fail("MAPUA14_CAMERA_BIND", error)
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!running.get()) return
            performance.recordAnalyzer()
            val frame = extractor?.processFrame(image) ?: return
            val overlayStarted = SystemClock.elapsedRealtimeNanos()
            val published = onFrame(frame)
            performance.recordOverlay(
                (SystemClock.elapsedRealtimeNanos() - overlayStarted) / 1_000_000.0,
                published
            )

            val temporalStarted = SystemClock.elapsedRealtimeNanos()
            val update = segmentMachine.onFrame(frame)
            performance.recordTemporal(
                (SystemClock.elapsedRealtimeNanos() - temporalStarted) / 1_000_000.0
            )
            logSegmentState(update)

            if (update.state != Mapua14LiveSegmentState.FINALIZING) {
                postTrackingState(update, frame)
                performance.logIfDue()
                return
            }

            eventOrdinal += 1L
            val inferenceReadyTimestampMs = (System.nanoTime() / 1_000_000L)
            val trajectory = segmentMachine.finalizeForInference(inferenceReadyTimestampMs)
            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val inference = checkNotNull(runtime).infer(trajectory.prepared.copyModelInput())
            performance.recordTflite(
                (SystemClock.elapsedRealtimeNanos() - inferenceStarted) / 1_000_000.0
            )
            val resultTimestampMs = (System.nanoTime() / 1_000_000L)
            val postEndTiming = segmentMachine.markInferenceFinished(resultTimestampMs)
            val gateStarted = SystemClock.elapsedRealtimeNanos()
            val gateEvaluation = Mapua14SegmentGate.evaluate(
                inference,
                trajectory,
                resultTimestampMs
            )
            performance.recordGate(
                (SystemClock.elapsedRealtimeNanos() - gateStarted) / 1_000_000.0
            )
            logClassifier(eventOrdinal, inference, trajectory, postEndTiming)
            logGate(eventOrdinal, inference, trajectory, gateEvaluation, postEndTiming)

            postState(
                Mapua14LiveSegmentLiveState(
                    tracking = if (gateEvaluation.decision.accepted) {
                        StandardFslTrackingState.READY
                    } else {
                        StandardFslTrackingState.HOLD_SIGN_CLEARLY
                    },
                    segmentState = segmentMachine.state,
                    segmentReason = "INFERENCE_COMPLETE",
                    inference = inference,
                    decision = gateEvaluation.decision,
                    quality = trajectory.prepared.quality,
                    timing = gateEvaluation.timing,
                    postEndTiming = postEndTiming,
                    cameraSource = resolvedCameraSource?.resolved
                )
            )
            if (gateEvaluation.decision.accepted) {
                val quality = trajectory.prepared.quality
                val accepted = StandardFslAcceptedResult(
                    label = inference.top1.label,
                    confidence = inference.top1.probability,
                    margin = inference.margin,
                    posePresent = quality.posePresentFrames > 0,
                    leftHandPresent = quality.leftHandPresentFrames > 0,
                    rightHandPresent = quality.rightHandPresentFrames > 0,
                    inferenceLatencyMs = inference.latencyMs,
                    top5 = inference.top5
                )
                mainExecutor.execute { onAccepted(accepted) }
            }
            performance.logIfDue()
        } catch (error: Throwable) {
            Log.e(TAG, "MAPUA14_SEGMENT_LIVE ERROR", error)
            segmentMachine.reset()
            postState(
                Mapua14LiveSegmentLiveState(
                    tracking = StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                    segmentState = segmentMachine.state,
                    segmentReason = "PIPELINE_ERROR",
                    cameraSource = resolvedCameraSource?.resolved,
                    blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
                )
            )
        } finally {
            image.close()
        }
    }

    private fun postTrackingState(
        update: Mapua14LiveSegmentUpdate,
        frame: LandmarkFrame
    ) {
        val tracking = when (update.state) {
            Mapua14LiveSegmentState.ARMING,
            Mapua14LiveSegmentState.CAPTURING,
            Mapua14LiveSegmentState.FINALIZING,
            Mapua14LiveSegmentState.INFERENCE -> StandardFslTrackingState.READY
            else -> if (frame.hasPose) {
                StandardFslTrackingState.READY
            } else {
                StandardFslTrackingState.HOLD_SIGN_CLEARLY
            }
        }
        postState(
            Mapua14LiveSegmentLiveState(
                tracking = tracking,
                segmentState = update.state,
                segmentReason = update.reason,
                cameraSource = resolvedCameraSource?.resolved
            )
        )
    }

    private fun recordHandIdentity(diagnostics: TemporalHandIdentityDiagnostics) {
        latestHandDiagnostics = diagnostics
        val now = SystemClock.elapsedRealtime()
        val exceptional = diagnostics.status == TemporalHandAssignmentStatus.FAILED_CLOSED ||
            diagnostics.slotChanges.isNotEmpty() ||
            diagnostics.leftDropout.lastReacquiredAfterFrames != null ||
            diagnostics.rightDropout.lastReacquiredAfterFrames != null
        if (!exceptional && now - lastHandLogAtMs < HAND_LOG_INTERVAL_MS) return
        lastHandLogAtMs = now
        Log.i(
            TAG,
            "MAPUA14_HAND_IDENTITY status=${diagnostics.status} " +
                "left_present=${diagnostics.leftPresent} right_present=${diagnostics.rightPresent} " +
                "handedness=${diagnostics.handedness.map { d -> "${d.detectionIndex}:${d.mediaPipeHandedness}:${d.handednessConfidence}->${d.assignedSide}" }} " +
                "left_wrist=${pointText(diagnostics.leftWrist)} right_wrist=${pointText(diagnostics.rightWrist)} " +
                "pose_left_wrist=${pointText(diagnostics.poseLeftWristAnchor)} " +
                "pose_right_wrist=${pointText(diagnostics.poseRightWristAnchor)} " +
                "hand_to_hand_distance=${diagnostics.interHandDistance} " +
                "slot_changes=${diagnostics.slotChanges} cumulative_slot_changes=${diagnostics.cumulativeSlotChangeCount} " +
                "left_dropout_frames=${diagnostics.leftDropout.currentFrames} " +
                "left_dropout_ms=${diagnostics.leftDropout.currentDurationMs} " +
                "right_dropout_frames=${diagnostics.rightDropout.currentFrames} " +
                "right_dropout_ms=${diagnostics.rightDropout.currentDurationMs} " +
                "unassigned=${diagnostics.unassignedDetectionIndices} " +
                "fail_closed_reasons=${diagnostics.failClosedReasons}"
        )
    }

    private fun logSegmentState(update: Mapua14LiveSegmentUpdate) {
        val now = SystemClock.elapsedRealtime()
        val key = "${update.state}:${update.reason}"
        if (key == lastSegmentLogKey && now - lastSegmentLogAtMs < SEGMENT_LOG_INTERVAL_MS) {
            return
        }
        lastSegmentLogKey = key
        lastSegmentLogAtMs = now
        Log.i(
            TAG,
            "MAPUA14_SEGMENT_STATE state=${update.state} reason=${update.reason} " +
                "captured_frames=${update.capturedFrameCount} activity=${update.activity} " +
                "completion=${update.completion ?: "NONE"}"
        )
    }

    private fun logClassifier(
        eventId: Long,
        inference: StandardFslInference,
        trajectory: Mapua14InferenceTrajectory,
        postEnd: Mapua14PostEndResultTiming
    ) {
        val prepared = trajectory.prepared
        Log.i(
            TAG,
            "MAPUA14_SEGMENT_CLASSIFIER event_id=$eventId " +
                "raw_top1=${inference.top1.label} raw_top1_score=${inference.top1.probability} " +
                "raw_top2=${inference.top2.label} raw_top2_score=${inference.top2.probability} " +
                "top5=${inference.top5.joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                "margin=${inference.margin} tflite_ms=${inference.latencyMs} " +
                "captured_frames=${prepared.capturedFrameCount} envelope_start=${prepared.motionStartCaptureIndex} " +
                "envelope_end=${prepared.motionEndCaptureIndex} envelope_frames=${prepared.completeTrajectoryFrameCount} " +
                "resampled_frames=${prepared.modelInput.size} feature_count=${prepared.modelInput.first().size} " +
                "interpolated_left=${prepared.interpolatedLeftFrames} " +
                "interpolated_right=${prepared.interpolatedRightFrames} " +
                "completion=${trajectory.completion} end_to_inference_ready_ms=${trajectory.endToInferenceReadyMs} " +
                "end_to_result_ms=${postEnd.endToResultMs}"
        )
    }

    private fun logGate(
        eventId: Long,
        inference: StandardFslInference,
        trajectory: Mapua14InferenceTrajectory,
        evaluation: Mapua14SegmentGateEvaluation,
        postEnd: Mapua14PostEndResultTiming
    ) {
        val quality = trajectory.prepared.quality
        val timing = evaluation.timing
        Log.i(
            TAG,
            "MAPUA14_SEGMENT_GATE event_id=$eventId predicted=${inference.top1.label} " +
                "confidence=${inference.top1.probability} margin=${inference.margin} " +
                "pose_ratio=${quality.posePresenceRatio} any_hand_ratio=${quality.anyHandPresenceRatio} " +
                "left_ratio=${quality.leftHandPresenceRatio} right_ratio=${quality.rightHandPresenceRatio} " +
                "both_ratio=${quality.bothHandsPresenceRatio} trajectory_duration_ms=${timing.windowDurationMs} " +
                "median_gap_ms=${timing.medianFrameGapMs} max_gap_ms=${timing.maxFrameGapMs} " +
                "final=${if (evaluation.decision.accepted) "ACCEPT" else "REJECT"} " +
                "reason=${evaluation.decision.reason} semantic_token_emitted=${evaluation.decision.accepted} " +
                "end_to_result_ms=${postEnd.endToResultMs} target_le_2000=${postEnd.withinTarget} " +
                "p95_goal_le_3000=${postEnd.withinP95Goal} " +
                "hand_status=${latestHandDiagnostics?.status ?: "UNKNOWN"}"
        )
    }

    private fun pointText(point: LandmarkPoint?): String {
        if (point == null) return "NONE"
        return String.format(Locale.US, "(%.4f,%.4f,%.4f)", point.x, point.y, point.z)
    }

    private fun fail(marker: String, error: Throwable) {
        if (!running.getAndSet(false)) return
        Log.e(TAG, "$marker BLOCKED", error)
        postState(
            Mapua14LiveSegmentLiveState(
                tracking = StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                segmentState = segmentMachine.state,
                segmentReason = marker,
                cameraSource = resolvedCameraSource?.resolved,
                blockedEvidence = "${error.javaClass.simpleName}: ${error.message}"
            )
        )
        mainExecutor.execute { unbindCamera() }
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.execute { closeRuntime() }
        }
    }

    private fun postState(state: Mapua14LiveSegmentLiveState) {
        performance.recordUiState()
        mainExecutor.execute { onState(state) }
    }

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
        resolvedCameraSource = null
        latestHandDiagnostics = null
        handIdentity.reset()
        segmentMachine.reset()
    }

    companion object {
        private const val TAG = "VoxGestMapua14Segment"
        private const val SEGMENT_LOG_INTERVAL_MS = 1_000L
        private const val HAND_LOG_INTERVAL_MS = 1_000L
    }
}
