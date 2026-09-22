package com.voxgest.dryrun

data class StandardFslRejectionConfig(
    val confidenceThreshold: Float,
    val marginThreshold: Float,
    val minimumPosePresenceRatio: Float,
    val minimumAnyHandPresenceRatio: Float,
    val requiredStableWindows: Int,
    val cooldownMs: Long,
    val maxWindowDurationMs: Long = 3_500L,
    val maxOldestFrameAgeMs: Long = 4_000L,
    val maxMedianFrameGapMs: Long = 300L,
    val maxFrameGapMs: Long = 600L
) {
    init {
        require(confidenceThreshold in 0f..1f)
        require(marginThreshold in 0f..1f)
        require(minimumPosePresenceRatio in 0f..1f)
        require(minimumAnyHandPresenceRatio in 0f..1f)
        require(requiredStableWindows >= 1)
        require(cooldownMs >= 0L)
        require(maxWindowDurationMs > 0L)
        require(maxOldestFrameAgeMs > 0L)
        require(maxMedianFrameGapMs > 0L)
        require(maxFrameGapMs >= maxMedianFrameGapMs)
    }
}

data class StandardFslPrediction(
    val top1Label: String,
    val top1Confidence: Float,
    val top2Label: String,
    val top2Confidence: Float,
    val inferenceLatencyMs: Double
) {
    val margin: Float get() = top1Confidence - top2Confidence
}

data class StandardFslGateDecision(
    val accepted: Boolean,
    val reason: String,
    val displayText: String,
    val stableWindowCount: Int,
    val cooldownActive: Boolean
)

/**
 * No NOTHING/NSAC class is assumed. Every uncertain state becomes a rejection
 * with the user-facing instruction "Hold sign clearly".
 */
class StandardFslRejectionGate(private val config: StandardFslRejectionConfig) {
    private var candidate = ""
    private var stableWindows = 0
    private var cooldownUntilMs = 0L
    private var lastAcceptedLabel = ""

    fun reset() {
        resetCandidate()
        cooldownUntilMs = 0L
        lastAcceptedLabel = ""
    }

    fun resetCandidate() {
        candidate = ""
        stableWindows = 0
    }

    fun evaluate(
        prediction: StandardFslPrediction?,
        window: StandardFullSign225WindowQuality,
        nowMs: Long,
        currentFrameUsable: Boolean = true,
        timing: StandardFullSign225WindowTiming? = null
    ): StandardFslGateDecision {
        if (window.frameCount != StandardFullSign225Contract.SEQUENCE_LENGTH) {
            return reject("BUFFER_NOT_READY", resetCandidate = true)
        }
        if (window.posePresenceRatio < config.minimumPosePresenceRatio) {
            return reject("LOW_POSE_PRESENCE", resetCandidate = true)
        }
        if (window.anyHandPresenceRatio < config.minimumAnyHandPresenceRatio) {
            return reject("LOW_HAND_PRESENCE", resetCandidate = true)
        }
        if (!currentFrameUsable) {
            return reject("CURRENT_FRAME_MISSING", resetCandidate = true)
        }
        if (timing != null) {
            if (!timing.available) return reject("WINDOW_TIMING_UNAVAILABLE", resetCandidate = true)
            if (!timing.chronological) return reject("NON_CHRONOLOGICAL_WINDOW", resetCandidate = true)
            if (timing.windowDurationMs > config.maxWindowDurationMs) {
                return reject("WINDOW_DURATION_EXCEEDED", resetCandidate = true)
            }
            if (timing.oldestFrameAgeMs > config.maxOldestFrameAgeMs) {
                return reject("STALE_WINDOW_OLDEST_FRAME", resetCandidate = true)
            }
            if (timing.medianFrameGapMs > config.maxMedianFrameGapMs) {
                return reject("MEDIAN_FRAME_GAP_EXCEEDED", resetCandidate = true)
            }
            if (timing.maxFrameGapMs > config.maxFrameGapMs) {
                return reject("MAX_FRAME_GAP_EXCEEDED", resetCandidate = true)
            }
        }
        if (prediction == null || prediction.top1Label.isBlank()) {
            return reject("NO_PREDICTION", resetCandidate = true)
        }
        if (!prediction.top1Confidence.isFinite() || !prediction.top2Confidence.isFinite()) {
            return reject("NON_FINITE_PREDICTION", resetCandidate = true)
        }
        if (prediction.top1Confidence < config.confidenceThreshold) {
            return reject("LOW_CONFIDENCE", resetCandidate = true)
        }
        if (prediction.margin < config.marginThreshold) {
            return reject("LOW_MARGIN", resetCandidate = true)
        }
        if (prediction.top1Label == lastAcceptedLabel) {
            return reject("DUPLICATE_REQUIRES_RELEASE", resetCandidate = true)
        }

        if (candidate == prediction.top1Label) {
            stableWindows += 1
        } else {
            candidate = prediction.top1Label
            stableWindows = 1
        }
        if (stableWindows < config.requiredStableWindows) {
            return reject("TEMPORAL_STABILITY", resetCandidate = false)
        }
        if (nowMs < cooldownUntilMs) {
            return reject("COOLDOWN", resetCandidate = false, cooldownActive = true)
        }

        cooldownUntilMs = nowMs + config.cooldownMs
        lastAcceptedLabel = prediction.top1Label
        candidate = ""
        val acceptedStableWindows = stableWindows
        stableWindows = 0
        return StandardFslGateDecision(
            accepted = true,
            reason = "ACCEPTED",
            displayText = prediction.top1Label,
            stableWindowCount = acceptedStableWindows,
            cooldownActive = false
        )
    }

    private fun reject(
        reason: String,
        resetCandidate: Boolean,
        cooldownActive: Boolean = false
    ): StandardFslGateDecision {
        if (resetCandidate) {
            candidate = ""
            stableWindows = 0
        }
        return StandardFslGateDecision(
            accepted = false,
            reason = reason,
            displayText = HOLD_SIGN_CLEARLY,
            stableWindowCount = stableWindows,
            cooldownActive = cooldownActive
        )
    }

    companion object {
        const val HOLD_SIGN_CLEARLY = "Hold sign clearly"
    }
}
