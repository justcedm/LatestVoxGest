package com.voxgest.dryrun

data class GateInput(
    val label: String,
    val confidence: Float,
    val margin: Float,
    val handPresence: Float,
    val qualityStatus: String = "GOOD"
)

data class GateResult(
    val accepted: Boolean,
    val reason: String
)

class RecognitionGate {
    fun evaluate(input: GateInput): GateResult {
        val label = input.label.uppercase()
        if (label.isBlank()) return GateResult(false, "empty_label")
        if (label == "NOTHING") return GateResult(false, "nothing_no_output")
        if (input.qualityStatus in BLOCKED_QUALITY) return GateResult(false, input.qualityStatus.lowercase())
        val threshold = PROFILE_THRESHOLDS[label] ?: DEFAULT_THRESHOLD
        if (input.handPresence < threshold.presence) return GateResult(false, "low_hand_presence")
        if (input.confidence < threshold.confidence) return GateResult(false, "low_confidence")
        if (input.margin < threshold.margin) return GateResult(false, "low_margin")
        return GateResult(true, "accepted")
    }

    private data class Threshold(
        val confidence: Float,
        val margin: Float,
        val presence: Float
    )

    companion object {
        private val BLOCKED_QUALITY = setOf(
            "BAD_SEQUENCE",
            "UNSTABLE_LANDMARKS",
            "LOW_HAND_PRESENCE",
            "MISSING_LANDMARKS",
            "WRONG_INPUT_SHAPE",
            "LANDMARK_PROFILE_NOT_READY"
        )
        private val DEFAULT_THRESHOLD = Threshold(confidence = 0.65f, margin = 0.08f, presence = 0.65f)
        private val PROFILE_THRESHOLDS = mapOf(
            "WHAT" to Threshold(confidence = 0.62f, margin = 0.06f, presence = 0.60f),
            "YOUR" to Threshold(confidence = 0.62f, margin = 0.06f, presence = 0.60f),
            "NAME" to Threshold(confidence = 0.60f, margin = 0.05f, presence = 0.60f),
            "MY" to Threshold(confidence = 0.58f, margin = 0.04f, presence = 0.60f),
            "EAT" to Threshold(confidence = 0.60f, margin = 0.08f, presence = 0.25f),
            "HELLO" to Threshold(confidence = 0.62f, margin = 0.10f, presence = 0.25f),
            "WATER" to Threshold(confidence = 0.62f, margin = 0.10f, presence = 0.25f),
            "THANKYOU" to Threshold(confidence = 0.62f, margin = 0.10f, presence = 0.25f)
        )
    }
}
