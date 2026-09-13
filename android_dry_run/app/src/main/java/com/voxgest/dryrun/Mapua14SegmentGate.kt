package com.voxgest.dryrun

data class Mapua14SegmentGateEvaluation(
    val decision: StandardFslGateDecision,
    val timing: StandardFullSign225WindowTiming
)

/**
 * One decision per completed sign event.
 *
 * Confidence, margin, tracking-quality and gap thresholds are identical to the
 * rollback Mapua14Rescue gate. The old two-overlapping-window confirmation is
 * intentionally absent because a finalized complete trajectory is itself the
 * temporal confirmation unit. WAIT_FOR_RELEASE supplies duplicate suppression.
 */
object Mapua14SegmentGate {
    const val CONFIDENCE_THRESHOLD = 0.70f
    const val MARGIN_THRESHOLD = 0.20f
    const val MINIMUM_POSE_PRESENCE_RATIO = 0.65f
    const val MINIMUM_ANY_HAND_PRESENCE_RATIO = 0.65f
    const val MAXIMUM_TRAJECTORY_DURATION_MS = 8_000L
    const val MAXIMUM_MEDIAN_FRAME_GAP_MS = 350L
    const val MAXIMUM_FRAME_GAP_MS = 700L

    fun evaluate(
        inference: StandardFslInference,
        trajectory: Mapua14InferenceTrajectory,
        resultTimestampMs: Long
    ): Mapua14SegmentGateEvaluation {
        val prepared = trajectory.prepared
        val timing = timing(prepared.sourceTimestampsMs, resultTimestampMs)

        fun reject(reason: String): Mapua14SegmentGateEvaluation {
            return Mapua14SegmentGateEvaluation(
                StandardFslGateDecision(
                    accepted = false,
                    reason = reason,
                    displayText = StandardFslRejectionGate.HOLD_SIGN_CLEARLY,
                    stableWindowCount = 1,
                    cooldownActive = false
                ),
                timing
            )
        }

        if (trajectory.completion == Mapua14SegmentCompletion.CAPTURE_LIMIT) {
            return reject("CAPTURE_LIMIT_WITHOUT_SIGN_END")
        }
        if (prepared.modelInput.size != Mapua14CompleteTrajectory48.OUTPUT_LENGTH ||
            prepared.modelInput.any { it.size != Mapua14CompleteTrajectory48.FEATURE_SIZE }
        ) {
            return reject("RESAMPLED_SHAPE_INVALID")
        }
        if (prepared.quality.frameCount < Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES + 1) {
            return reject("TRAJECTORY_TOO_SHORT")
        }
        if (prepared.quality.posePresenceRatio < MINIMUM_POSE_PRESENCE_RATIO) {
            return reject("LOW_POSE_PRESENCE")
        }
        if (prepared.quality.anyHandPresenceRatio < MINIMUM_ANY_HAND_PRESENCE_RATIO) {
            return reject("LOW_HAND_PRESENCE")
        }
        if (!timing.available || !timing.chronological) {
            return reject("TRAJECTORY_TIMING_INVALID")
        }
        if (timing.windowDurationMs > MAXIMUM_TRAJECTORY_DURATION_MS) {
            return reject("TRAJECTORY_DURATION_EXCEEDED")
        }
        if (timing.medianFrameGapMs > MAXIMUM_MEDIAN_FRAME_GAP_MS) {
            return reject("MEDIAN_FRAME_GAP_EXCEEDED")
        }
        if (timing.maxFrameGapMs > MAXIMUM_FRAME_GAP_MS) {
            return reject("MAX_FRAME_GAP_EXCEEDED")
        }
        if (!inference.top1.probability.isFinite() || !inference.top2.probability.isFinite()) {
            return reject("NON_FINITE_PREDICTION")
        }
        if (inference.top1.probability < CONFIDENCE_THRESHOLD) {
            return reject("LOW_CONFIDENCE")
        }
        if (inference.margin < MARGIN_THRESHOLD) {
            return reject("LOW_MARGIN")
        }
        return Mapua14SegmentGateEvaluation(
            StandardFslGateDecision(
                accepted = true,
                reason = "ACCEPTED",
                displayText = inference.top1.label,
                stableWindowCount = 1,
                cooldownActive = false
            ),
            timing
        )
    }

    private fun timing(
        timestamps: LongArray,
        resultTimestampMs: Long
    ): StandardFullSign225WindowTiming {
        if (timestamps.size < 2 || timestamps.any { it <= 0L }) {
            return StandardFullSign225WindowTiming(false, false, 0L, 0L, 0L, 0L)
        }
        val gaps = List(timestamps.size - 1) { index ->
            timestamps[index + 1] - timestamps[index]
        }
        val chronological = gaps.all { it > 0L }
        if (!chronological) {
            return StandardFullSign225WindowTiming(true, false, 0L, 0L, 0L, 0L)
        }
        val sorted = gaps.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2L
        } else {
            sorted[middle]
        }
        return StandardFullSign225WindowTiming(
            available = true,
            chronological = true,
            windowDurationMs = timestamps.last() - timestamps.first(),
            oldestFrameAgeMs = (resultTimestampMs - timestamps.first()).coerceAtLeast(0L),
            medianFrameGapMs = median,
            maxFrameGapMs = gaps.maxOrNull() ?: 0L
        )
    }
}
