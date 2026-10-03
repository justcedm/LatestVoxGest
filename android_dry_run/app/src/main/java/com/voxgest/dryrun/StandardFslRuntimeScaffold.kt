package com.voxgest.dryrun

data class StandardFslFrameUpdate(
    val feature: StandardFullSign225Frame,
    val diagnostics: StandardFslDiagnostics
)

data class StandardFslDecisionUpdate(
    val decision: StandardFslGateDecision,
    val diagnostics: StandardFslDiagnostics
)

/**
 * Model-independent orchestration for the future standard runtime. Keeping it
 * model-independent allows camera/feature diagnostics before a model exists,
 * without ever substituting a legacy model into the 105-class slot.
 */
class StandardFslRuntimeScaffold(rejectionConfig: StandardFslRejectionConfig) {
    private val buffer = StandardFullSign225RollingBuffer()
    private val gate = StandardFslRejectionGate(rejectionConfig)
    private var latestQuality: StandardFullSign225FrameQuality? = null
    private var latestEvent: StandardFslEventUpdate? = null

    val bufferFrames: Int get() = buffer.size
    val readyForInference: Boolean get() = buffer.isReady

    fun append(frame: LandmarkFrame): StandardFslFrameUpdate {
        val canonical = StandardFullSign225FeatureBuilder.build(frame, inputMirrored = false)
        latestQuality = canonical.quality
        buffer.add(canonical)
        return StandardFslFrameUpdate(
            feature = canonical,
            diagnostics = diagnostics(
                prediction = null,
                accepted = false,
                rejectionReason = if (buffer.isReady) "READY_FOR_INFERENCE" else "BUFFER_NOT_READY",
                nowMs = frame.timestampMs
            )
        )
    }

    fun snapshotForInference(): Array<FloatArray>? = buffer.snapshot()

    fun recordEvent(update: StandardFslEventUpdate) {
        latestEvent = update
    }

    fun cancelTemporalCandidate() {
        gate.resetCandidate()
    }

    fun diagnosticsSnapshot(reason: String, nowMs: Long): StandardFslDiagnostics = diagnostics(
        prediction = null,
        accepted = false,
        rejectionReason = reason,
        nowMs = nowMs
    )

    /** Current 20-frame presence evidence for diagnostics and live-test recording. */
    fun windowQuality(): StandardFullSign225WindowQuality = buffer.quality()

    fun evaluate(
        inference: StandardFslInference,
        nowMs: Long,
        currentFrameUsable: Boolean = true
    ): StandardFslDecisionUpdate {
        val prediction = StandardFslPrediction(
            top1Label = inference.top1.label,
            top1Confidence = inference.top1.probability,
            top2Label = inference.top2.label,
            top2Confidence = inference.top2.probability,
            inferenceLatencyMs = inference.latencyMs
        )
        val decision = gate.evaluate(
            prediction = prediction,
            window = buffer.quality(),
            nowMs = nowMs,
            currentFrameUsable = currentFrameUsable,
            timing = buffer.timing(nowMs)
        )
        return StandardFslDecisionUpdate(
            decision = decision,
            diagnostics = diagnostics(
                prediction = prediction,
                accepted = decision.accepted,
                rejectionReason = if (decision.accepted) null else decision.reason,
                nowMs = nowMs
            )
        )
    }

    fun reset() {
        buffer.clear()
        gate.reset()
        latestQuality = null
        latestEvent = null
    }

    private fun diagnostics(
        prediction: StandardFslPrediction?,
        accepted: Boolean,
        rejectionReason: String?,
        nowMs: Long
    ): StandardFslDiagnostics {
        val quality = latestQuality
        val timing = buffer.timing(nowMs)
        val event = latestEvent
        return StandardFslDiagnostics(
            posePresent = quality?.posePresent == true,
            leftHandPresent = quality?.leftHandPresent == true,
            rightHandPresent = quality?.rightHandPresent == true,
            bufferFrames = buffer.size,
            activeProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
            top1Label = prediction?.top1Label,
            top1Confidence = prediction?.top1Confidence,
            top2Label = prediction?.top2Label,
            top2Margin = prediction?.margin,
            accepted = accepted,
            rejectionReason = rejectionReason,
            inferenceLatencyMs = prediction?.inferenceLatencyMs,
            eventState = event?.state ?: StandardFslEventState.IDLE,
            eventReason = event?.reason,
            activityScore = event?.activity?.activityScore,
            wristDisplacement = event?.activity?.wristDisplacement,
            fingertipDisplacement = event?.activity?.fingertipDisplacement,
            jointShapeDisplacement = event?.activity?.jointShapeDisplacement,
            activityTemporalVariance = event?.activity?.temporalVariance,
            recentValidFrameRatio = event?.activity?.recentValidFrameRatio,
            windowDurationMs = timing.takeIf { it.available }?.windowDurationMs,
            oldestFrameAgeMs = timing.takeIf { it.available }?.oldestFrameAgeMs,
            medianFrameGapMs = timing.takeIf { it.available }?.medianFrameGapMs,
            maxFrameGapMs = timing.takeIf { it.available }?.maxFrameGapMs
        )
    }
}
