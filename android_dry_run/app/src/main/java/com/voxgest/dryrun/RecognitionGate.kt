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
    private var streakLabel: String = ""
    private var streakCount: Int = 0

    fun evaluate(input: GateInput): GateResult {
        val label = input.label.uppercase()
        if (label.isBlank()) return GateResult(false, "empty_label")
        if (label == "NOTHING") return GateResult(false, "nothing_no_output")
        if (label !in ALLOWED_ONEHAND_LABELS) return GateResult(false, "unsupported_label")
        if (input.qualityStatus in BLOCKED_QUALITY) {
            reset()
            return GateResult(false, input.qualityStatus.lowercase())
        }
        val threshold = PROFILE_THRESHOLDS[label] ?: DEFAULT_THRESHOLD
        if (input.handPresence < threshold.presence) {
            reset()
            return GateResult(false, "low_hand_presence")
        }
        if (input.confidence < threshold.confidence) {
            reset()
            return GateResult(false, "low_confidence")
        }
        if (input.margin < threshold.margin) {
            reset()
            return GateResult(false, "low_margin")
        }
        if (label == streakLabel) {
            streakCount += 1
        } else {
            streakLabel = label
            streakCount = 1
        }
        if (streakCount < REQUIRED_CONSECUTIVE_MATCHES) {
            return GateResult(false, "need_consistency_${streakCount}_$REQUIRED_CONSECUTIVE_MATCHES")
        }
        reset()
        return GateResult(true, "accepted")
    }

    fun reset() {
        streakLabel = ""
        streakCount = 0
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
        private const val REQUIRED_CONSECUTIVE_MATCHES = 3
        private val ALLOWED_ONEHAND_LABELS = setOf("WHAT", "YOUR", "NAME", "MY", "NOTHING")
        private val DEFAULT_THRESHOLD = Threshold(confidence = 0.85f, margin = 0.25f, presence = 0.75f)
        private val PROFILE_THRESHOLDS = mapOf(
            "WHAT" to DEFAULT_THRESHOLD,
            "YOUR" to DEFAULT_THRESHOLD,
            "NAME" to DEFAULT_THRESHOLD,
            "MY" to DEFAULT_THRESHOLD
        )
    }
}
