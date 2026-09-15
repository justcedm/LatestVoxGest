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
    @Volatile private var boundCamera: Camera? = null
    @Volatile private var cameraStateObserver: Observer<CameraState>? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var runtime: StandardFslTfliteRuntime? = null
    @Volatile private var segmentMachine: Fsl105LiveSegmentStateMachine? = null
    @Volatile private var runtimeConfig: StandardFslRuntimeConfig? = null
    @Volatile private var runtimeTemporalProfile: CompleteSignTemporalProfile? = null
    @Volatile private var latestHandDiagnostics: TemporalHandIdentityDiagnostics? = null
    @Volatile private var latestLandmarkMetrics: LandmarkExtractionMetrics? = null
    @Volatile private var lastStateAtMs = 0L
    @Volatile private var lastTracking: StandardFslTrackingState? = null
    private var lastEventLogKey = ""
    private var lastEventLogAtMs = Long.MIN_VALUE
    private var eventOrdinal = 0L
    private var eventTrackingFailureReason: String? = null

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

                val loadedRuntime = StandardFslTfliteRuntime(appContext)
                val config = loadedRuntime.config
                runtime = loadedRuntime
                runtimeConfig = config
                val temporalProfile = CompleteSignTemporalProfiles.standardFsl105(
                    sequenceLength = config.sequenceLength,
                    featureSize = config.featureSize
                )
                runtimeTemporalProfile = temporalProfile
                segmentMachine = Fsl105LiveSegmentStateMachine(temporalProfile)
                val lens = if (requestedCameraSource == CameraSource.BACK) {
                    GradingCameraLens.BACK
                } else {
                    GradingCameraLens.FRONT
                }
                extractor = StandardFslCameraPipeline.createLandmarkExtractor(
                    context = appContext,
                    lens = lens,
                    onMetrics = { metrics ->
                        latestLandmarkMetrics = metrics
                        performance.recordLandmarks(metrics)
                    },
                    handIdentityStabilizer = handIdentity,
                    onHandIdentityDiagnostics = { diagnostics ->
                        latestHandDiagnostics = diagnostics
                    }
                )
                Log.i(
                    TAG,
                    "STANDARD_MODEL_LOAD PASS runtime_banner=${config.runtimeBanner} " +
                        "input=${config.inputShape.contentToString()} " +
                        "output=${config.outputShape.contentToString()} feature_version=${config.featureVersion} " +
                        "model_input=unmirrored ${artifacts.evidence}"
                )
                Log.i(TAG, "ONEHAND162_FALLBACK PASS preserved=true loaded=false reason=standard_profile_is_ready")
                mainExecutor.execute { bindCamera(previewView) }
            } catch (error: Throwable) {
                fail("STANDARD_MODEL_LOAD", error)
            }
        }
    }

    /** Clears the current sign event without changing any threshold or model contract. */
    fun clear() {
        if (released.get()) return
        analysisExecutor.execute {
            segmentMachine?.reset()
            handIdentity.reset()
            eventTrackingFailureReason = null
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
            val temporalStarted = SystemClock.elapsedRealtimeNanos()
            val machine = segmentMachine ?: return
            val event = machine.onFrame(frame)
            performance.recordTemporal(
                (SystemClock.elapsedRealtimeNanos() - temporalStarted) / NANOS_PER_MS
            )
            if (event.state == Mapua14LiveSegmentState.CAPTURING &&
                event.capturedFrameCount <= (runtimeTemporalProfile?.motionBoundaryFrames ?: 0) + 1
            ) {
                eventTrackingFailureReason = null
            }
            if (event.state == Mapua14LiveSegmentState.CAPTURING &&
                latestHandDiagnostics?.status == TemporalHandAssignmentStatus.FAILED_CLOSED
            ) {
                eventTrackingFailureReason = "AMBIGUOUS_ANATOMICAL_COLLISION"
            }
            if (event.reason == "RELEASE_CONFIRMED") eventTrackingFailureReason = null
            logSegmentState(event, frame.timestampMs)

            if (event.state != Mapua14LiveSegmentState.FINALIZING) {
                val diagnostics = segmentDiagnostics(event, frame)
                val tracking = when (event.state) {
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
                postState(StandardFslLiveUiState(tracking, diagnostics))
                performance.logIfDue()
                return
            }

            eventOrdinal += 1L
            val inferenceReadyTimestampMs = System.nanoTime() / 1_000_000L
            val trajectory = try {
                machine.finalizeForInference(inferenceReadyTimestampMs)
            } catch (error: IllegalArgumentException) {
                Log.i(
                    TAG,
                    "FSL105_SEGMENT_GATE event_id=$eventOrdinal final=REJECT " +
                        "reason=MALFORMED_EVENT detail=${error.message} semantic_token_emitted=false"
                )
                machine.reset()
                postState(
                    StandardFslLiveUiState(
                        StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                        segmentDiagnostics(event, frame, rejectionReason = "MALFORMED_EVENT")
                    ),
                    force = true
                )
                return
            }
            val resamplingCompletedTimestampMs = System.nanoTime() / 1_000_000L
            val preflightTimestampMs = System.nanoTime() / 1_000_000L
            val preflight = Fsl105SegmentGate.preflight(
                trajectory,
                preflightTimestampMs,
                eventTrackingFailureReason
            )
            if (preflight != null) {
                val postEndTiming = machine.markInferenceFinished(preflightTimestampMs)
                logPreflightRejection(eventOrdinal, trajectory, preflight, postEndTiming)
                val quality = trajectory.prepared.quality
                postState(
                    StandardFslLiveUiState(
                        StandardFslTrackingState.HOLD_SIGN_CLEARLY,
                        StandardFslDiagnostics(
                            posePresent = quality.posePresentFrames > 0,
                            leftHandPresent = quality.leftHandPresentFrames > 0,
                            rightHandPresent = quality.rightHandPresentFrames > 0,
                            bufferFrames = runtimeConfig?.sequenceLength ?: 20,
                            activeProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
                            top1Label = null,
                            top1Confidence = null,
                            top2Label = null,
                            top2Margin = null,
                            accepted = false,
                            rejectionReason = preflight.decision.reason,
                            inferenceLatencyMs = null,
                            eventState = StandardFslEventState.WAIT_FOR_RELEASE,
                            eventReason = preflight.decision.reason,
                            activityScore = event.activity,
                            windowDurationMs = preflight.timing.windowDurationMs,
                            oldestFrameAgeMs = preflight.timing.oldestFrameAgeMs,
                            medianFrameGapMs = preflight.timing.medianFrameGapMs,
                            maxFrameGapMs = preflight.timing.maxFrameGapMs
                        )
                    ),
                    force = true
                )
                performance.logIfDue()
                return
            }
            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val inference = runtime?.infer(trajectory.prepared.copyModelInput()) ?: return
            performance.recordTflite(
                (SystemClock.elapsedRealtimeNanos() - inferenceStarted) / NANOS_PER_MS
            )
            val resultTimestampMs = System.nanoTime() / 1_000_000L
            val postEndTiming = machine.markInferenceFinished(resultTimestampMs)
            val gateStarted = SystemClock.elapsedRealtimeNanos()
            val gateEvaluation = Fsl105SegmentGate.evaluate(
                inference,
                trajectory,
                resultTimestampMs,
                eventTrackingFailureReason
            )
            performance.recordGate(
                (SystemClock.elapsedRealtimeNanos() - gateStarted) / NANOS_PER_MS
            )
            val decision = gateEvaluation.decision
            val quality = trajectory.prepared.quality
            val timing = gateEvaluation.timing
            val diagnostics = StandardFslDiagnostics(
                posePresent = quality.posePresentFrames > 0,
                leftHandPresent = quality.leftHandPresentFrames > 0,
                rightHandPresent = quality.rightHandPresentFrames > 0,
                bufferFrames = runtimeConfig?.sequenceLength ?: 20,
                activeProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
                top1Label = inference.top1.label,
                top1Confidence = inference.top1.probability,
                top2Label = inference.top2.label,
                top2Margin = inference.margin,
                accepted = decision.accepted,
                rejectionReason = decision.reason.takeUnless { decision.accepted },
                inferenceLatencyMs = inference.latencyMs,
                eventState = if (decision.accepted) {
                    StandardFslEventState.ACCEPTED
                } else {
                    StandardFslEventState.WAIT_FOR_RELEASE
                },
                eventReason = if (decision.accepted) "TOKEN_ACCEPTED" else decision.reason,
                activityScore = event.activity,
                windowDurationMs = timing.windowDurationMs,
                oldestFrameAgeMs = timing.oldestFrameAgeMs,
                medianFrameGapMs = timing.medianFrameGapMs,
                maxFrameGapMs = timing.maxFrameGapMs
            )
            logClassifier(
                eventOrdinal,
                inference,
                trajectory,
                postEndTiming,
                resamplingCompletedTimestampMs,
                resultTimestampMs
            )
            logGate(eventOrdinal, inference, trajectory, gateEvaluation, postEndTiming)
            val tracking = if (decision.accepted) {
                StandardFslTrackingState.READY
            } else {
                StandardFslTrackingState.HOLD_SIGN_CLEARLY
            }
            postState(StandardFslLiveUiState(tracking, diagnostics))
            Log.i(
                TAG,
                    "STANDARD_FSL_LIVE decision=${if (decision.accepted) "ACCEPT" else "REJECT"} " +
                    "reason=${decision.reason} complete_event=true rolling_window=false " +
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
            segmentMachine?.reset()
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
        runtimeConfig = null
        runtimeTemporalProfile = null
        segmentMachine?.reset()
        segmentMachine = null
        handIdentity.reset()
        latestHandDiagnostics = null
        latestLandmarkMetrics = null
        eventTrackingFailureReason = null
        lastEventLogKey = ""
        lastEventLogAtMs = Long.MIN_VALUE
    }

    private fun segmentDiagnostics(
        event: Mapua14LiveSegmentUpdate,
        frame: LandmarkFrame,
        rejectionReason: String = event.reason
    ): StandardFslDiagnostics {
        val mappedState = when (event.state) {
            Mapua14LiveSegmentState.IDLE -> StandardFslEventState.IDLE
            Mapua14LiveSegmentState.ARMING -> StandardFslEventState.PRIMING
            Mapua14LiveSegmentState.CAPTURING -> StandardFslEventState.SIGN_ACTIVE
            Mapua14LiveSegmentState.FINALIZING,
            Mapua14LiveSegmentState.INFERENCE -> StandardFslEventState.CANDIDATE
            Mapua14LiveSegmentState.WAIT_FOR_RELEASE -> StandardFslEventState.WAIT_FOR_RELEASE
        }
        return StandardFslDiagnostics(
            posePresent = frame.hasPose,
            leftHandPresent = frame.hasLeftHand,
            rightHandPresent = frame.hasRightHand,
            bufferFrames = event.capturedFrameCount.coerceIn(
                0,
                StandardFullSign225Contract.SEQUENCE_LENGTH
            ),
            activeProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
            top1Label = null,
            top1Confidence = null,
            top2Label = null,
            top2Margin = null,
            accepted = false,
            rejectionReason = rejectionReason,
            inferenceLatencyMs = null,
            eventState = mappedState,
            eventReason = "${Fsl105LiveSegmentProfile.ID}:${event.reason}",
            activityScore = event.activity
        )
    }

    private fun logSegmentState(event: Mapua14LiveSegmentUpdate, frameTimestampMs: Long) {
        val key = "${event.state}:${event.reason}"
        val nowMs = SystemClock.elapsedRealtime()
        if (key == lastEventLogKey && nowMs - lastEventLogAtMs < UNCHANGED_EVENT_LOG_INTERVAL_MS) {
            return
        }
        lastEventLogKey = key
        lastEventLogAtMs = nowMs
        Log.i(
            TAG,
            "FSL105_SEGMENT_STATE state=${event.state} reason=${event.reason} " +
                "frame_timestamp_ms=$frameTimestampMs " +
                "process_timestamp_ms=${System.nanoTime() / 1_000_000L} " +
                "captured_frames=${event.capturedFrameCount} activity=${event.activity} " +
                "completion=${event.completion ?: "NONE"}"
        )
    }

    private fun logClassifier(
        eventId: Long,
        inference: StandardFslInference,
        trajectory: Fsl105InferenceTrajectory,
        postEnd: Mapua14PostEndResultTiming,
        resamplingCompletedTimestampMs: Long,
        resultTimestampMs: Long
    ) {
        val prepared = trajectory.prepared
        val timestamps = prepared.sourceTimestampsMs
        val captureDurationMs = if (timestamps.size >= 2) {
            timestamps.last() - timestamps.first()
        } else {
            0L
        }
        Log.i(
            TAG,
            "FSL105_SEGMENT_CLASSIFIER event_id=$eventId " +
                "raw_top1=${inference.top1.label} raw_top1_score=${inference.top1.probability} " +
                "raw_top2=${inference.top2.label} raw_top2_score=${inference.top2.probability} " +
                "top5=${inference.top5.joinToString(prefix = "[", postfix = "]") { "${it.label}:${it.probability}" }} " +
                "margin=${inference.margin} mediapipe_ms=${latestLandmarkMetrics?.totalMs ?: "UNKNOWN"} " +
                "tflite_ms=${inference.latencyMs} capture_duration_ms=$captureDurationMs " +
                "captured_frames=${prepared.capturedFrameCount} " +
                "envelope_frames=${prepared.completeTrajectoryFrameCount} " +
                "resampled_frames=${prepared.modelInput.size} feature_count=${prepared.modelInput.first().size} " +
                "pose_frames=${prepared.quality.posePresentFrames} " +
                "left_frames=${prepared.quality.leftHandPresentFrames} " +
                "right_frames=${prepared.quality.rightHandPresentFrames} " +
                "any_hand_frames=${prepared.quality.anyHandPresentFrames} " +
                "both_hand_frames=${prepared.quality.bothHandsPresentFrames} " +
                "interpolated_left=${prepared.interpolatedLeftFrames} " +
                "interpolated_right=${prepared.interpolatedRightFrames} " +
                "completion=${trajectory.completion} " +
                "sign_start_timestamp_ms=${trajectory.signStartTimestampMs} " +
                "estimated_sign_end_timestamp_ms=${trajectory.estimatedSignEndTimestampMs} " +
                "completion_detected_timestamp_ms=${trajectory.completionDetectedTimestampMs} " +
                "inference_ready_timestamp_ms=${trajectory.inferenceReadyTimestampMs} " +
                "resampling_completed_timestamp_ms=$resamplingCompletedTimestampMs " +
                "result_timestamp_ms=$resultTimestampMs " +
                "end_to_inference_ready_ms=${trajectory.endToInferenceReadyMs} " +
                "end_to_raw_result_ms=${postEnd.endToResultMs}"
        )
    }

    private fun logPreflightRejection(
        eventId: Long,
        trajectory: Fsl105InferenceTrajectory,
        evaluation: Fsl105SegmentGateEvaluation,
        postEnd: Mapua14PostEndResultTiming
    ) {
        val prepared = trajectory.prepared
        val quality = prepared.quality
        val timing = evaluation.timing
        Log.i(
            TAG,
            "FSL105_SEGMENT_GATE event_id=$eventId raw_top1=NOT_RUN tflite_ms=NOT_RUN " +
                "captured_frames=${prepared.capturedFrameCount} " +
                "resampled_frames=${prepared.modelInput.size} " +
                "pose_ratio=${quality.posePresenceRatio} any_hand_ratio=${quality.anyHandPresenceRatio} " +
                "left_ratio=${quality.leftHandPresenceRatio} right_ratio=${quality.rightHandPresenceRatio} " +
                "both_ratio=${quality.bothHandsPresenceRatio} trajectory_duration_ms=${timing.windowDurationMs} " +
                "median_gap_ms=${timing.medianFrameGapMs} max_gap_ms=${timing.maxFrameGapMs} " +
                "final=REJECT reason=${evaluation.decision.reason} semantic_token_emitted=false " +
                "end_to_raw_result_ms=NOT_RUN end_to_accepted_result_ms=NOT_ACCEPTED " +
                "sign_end_to_rejection_ms=${postEnd.endToResultMs} " +
                "mediapipe_ms=${latestLandmarkMetrics?.totalMs ?: "UNKNOWN"} " +
                "hand_status=${latestHandDiagnostics?.status ?: "UNKNOWN"}"
        )
    }

    private fun logGate(
        eventId: Long,
        inference: StandardFslInference,
        trajectory: Fsl105InferenceTrajectory,
        evaluation: Fsl105SegmentGateEvaluation,
        postEnd: Mapua14PostEndResultTiming
    ) {
        val quality = trajectory.prepared.quality
        val timing = evaluation.timing
        Log.i(
            TAG,
            "FSL105_SEGMENT_GATE event_id=$eventId predicted=${inference.top1.label} " +
                "confidence=${inference.top1.probability} margin=${inference.margin} " +
                "pose_ratio=${quality.posePresenceRatio} any_hand_ratio=${quality.anyHandPresenceRatio} " +
                "left_ratio=${quality.leftHandPresenceRatio} right_ratio=${quality.rightHandPresenceRatio} " +
                "both_ratio=${quality.bothHandsPresenceRatio} trajectory_duration_ms=${timing.windowDurationMs} " +
                "median_gap_ms=${timing.medianFrameGapMs} max_gap_ms=${timing.maxFrameGapMs} " +
                "final=${if (evaluation.decision.accepted) "ACCEPT" else "REJECT"} " +
                "reason=${evaluation.decision.reason} semantic_token_emitted=${evaluation.decision.accepted} " +
                "end_to_raw_result_ms=${postEnd.endToResultMs} " +
                "end_to_accepted_result_ms=${if (evaluation.decision.accepted) postEnd.endToResultMs else "NOT_ACCEPTED"} " +
                "hand_status=${latestHandDiagnostics?.status ?: "UNKNOWN"} " +
                "hand_fail_reasons=${latestHandDiagnostics?.failClosedReasons.orEmpty()}"
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
