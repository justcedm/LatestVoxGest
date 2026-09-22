package com.voxgest.dryrun.fsl

data class FslDiagnosticDecision(
    val accepted: Boolean,
    val state: String,
    val reason: String,
    val inferenceOrdinal: Long,
    val stableWindowCount: Int
)

data class FslWindowQuality(
    val posePresenceRatio: Float,
    val selectedHandPresenceRatio: Float
)

data class FslDiagnosticSafetyConfig(
    val minimumPosePresenceRatio: Float = 0.65f,
    val minimumSelectedHandPresenceRatio: Float = 0.65f,
    val requiredStableWindows: Int = 2
) {
    init {
        require(minimumPosePresenceRatio in 0f..1f)
        require(minimumSelectedHandPresenceRatio in 0f..1f)
        require(requiredStableWindows >= 1)
    }
}

class FslDiagnosticGate(
    private val contract: FslRuntimeContract,
    private val safety: FslDiagnosticSafetyConfig = FslDiagnosticSafetyConfig()
) {
    private var inferenceOrdinal = 0L
    private var lastAcceptedLabel = ""
    private var lastAcceptedOrdinal = Long.MIN_VALUE / 2
    private var stableCandidateLabel = ""
    private var stableCandidateCount = 0

    fun evaluate(
        inference: FslInference,
        quality: FslWindowQuality = FslWindowQuality(1f, 1f)
    ): FslDiagnosticDecision {
        inferenceOrdinal += 1
        if (quality.posePresenceRatio < safety.minimumPosePresenceRatio) {
            resetStability()
            return rejected("LOW_POSE_PRESENCE")
        }
        if (quality.selectedHandPresenceRatio < safety.minimumSelectedHandPresenceRatio) {
            resetStability()
            return rejected("LOW_SELECTED_HAND_PRESENCE")
        }
        if (inference.top1.probability < contract.confidenceThreshold) {
            resetStability()
            return rejected("LOW_CONFIDENCE")
        }
        if (inference.margin < contract.marginThreshold) {
            resetStability()
            return rejected("LOW_MARGIN")
        }
        if (inference.top1.label == stableCandidateLabel) {
            stableCandidateCount += 1
        } else {
            stableCandidateLabel = inference.top1.label
            stableCandidateCount = 1
        }
        if (stableCandidateCount < safety.requiredStableWindows) {
            return rejected("TEMPORAL_STABILITY")
        }
        if (inference.top1.label == lastAcceptedLabel &&
            inferenceOrdinal - lastAcceptedOrdinal <= contract.cooldownFrames
        ) {
            return rejected("PROVISIONAL_COOLDOWN")
        }
        lastAcceptedLabel = inference.top1.label
        lastAcceptedOrdinal = inferenceOrdinal
        return FslDiagnosticDecision(
            accepted = true,
            state = "DIAGNOSTIC_ACCEPT",
            reason = "ACCEPTED",
            inferenceOrdinal = inferenceOrdinal,
            stableWindowCount = stableCandidateCount
        )
    }

    fun reset() {
        inferenceOrdinal = 0L
        lastAcceptedLabel = ""
        lastAcceptedOrdinal = Long.MIN_VALUE / 2
        resetStability()
    }

    private fun rejected(reason: String): FslDiagnosticDecision {
        return FslDiagnosticDecision(
            accepted = false,
            state = "DIAGNOSTIC_REJECT",
            reason = reason,
            inferenceOrdinal = inferenceOrdinal,
            stableWindowCount = stableCandidateCount
        )
    }

    private fun resetStability() {
        stableCandidateLabel = ""
        stableCandidateCount = 0
    }
}
