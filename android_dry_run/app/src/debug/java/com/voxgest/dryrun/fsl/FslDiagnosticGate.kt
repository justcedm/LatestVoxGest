package com.voxgest.dryrun.fsl

data class FslDiagnosticDecision(
    val accepted: Boolean,
    val state: String,
    val reason: String,
    val inferenceOrdinal: Long
)

class FslDiagnosticGate(private val contract: FslRuntimeContract) {
    private var inferenceOrdinal = 0L
    private var lastAcceptedLabel = ""
    private var lastAcceptedOrdinal = Long.MIN_VALUE / 2

    fun evaluate(inference: FslInference): FslDiagnosticDecision {
        inferenceOrdinal += 1
        if (inference.top1.probability < contract.confidenceThreshold) {
            return rejected("LOW_CONFIDENCE")
        }
        if (inference.margin < contract.marginThreshold) {
            return rejected("LOW_MARGIN")
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
            reason = "VALIDATION_ONLY_THRESHOLDS",
            inferenceOrdinal = inferenceOrdinal
        )
    }

    fun reset() {
        inferenceOrdinal = 0L
        lastAcceptedLabel = ""
        lastAcceptedOrdinal = Long.MIN_VALUE / 2
    }

    private fun rejected(reason: String): FslDiagnosticDecision {
        return FslDiagnosticDecision(
            accepted = false,
            state = "DIAGNOSTIC_REJECT",
            reason = reason,
            inferenceOrdinal = inferenceOrdinal
        )
    }
}
