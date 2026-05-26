package com.voxgest.dryrun

import android.os.SystemClock
import java.util.Locale

data class AlphabetGateResult(
    val accepted: Boolean,
    val label: String,
    val confidence: Float,
    val margin: Float,
    val reason: String
)

class AlphabetAcceptanceGate {
    private var candidateLabel: String = ""
    private var candidateFrames: Int = 0
    private var lockedAcceptedLabel: String = ""
    private var cooldownUntilMs: Long = 0L

    fun evaluate(
        prediction: AlphabetRawPrediction?,
        decision: RouterDecision
    ): AlphabetGateResult {
        if (prediction == null || decision.handPresence < 0.5f) {
            resetForNoHand()
            return AlphabetGateResult(false, "", 0f, 0f, "no_hand")
        }

        if (decision.shapeMotion >= SHAPE_RESET_MOTION) {
            lockedAcceptedLabel = ""
        }

        val label = normalizeLabel(prediction.label)
        if (label == "NOTHING" || label.isBlank()) {
            resetCandidate()
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "nothing_no_output")
        }
        if (!isSupportedOutput(label)) {
            resetCandidate()
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "unsupported_label")
        }
        if (decision.stableFrames < REQUIRED_STABLE_FRAMES) {
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "hand_not_stable")
        }
        if (prediction.confidence < MIN_CONFIDENCE) {
            resetCandidate()
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "low_confidence")
        }
        if (prediction.margin < MIN_MARGIN) {
            resetCandidate()
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "low_margin")
        }

        if (label == candidateLabel) {
            candidateFrames += 1
        } else {
            candidateLabel = label
            candidateFrames = 1
        }

        if (candidateFrames < REQUIRED_LABEL_FRAMES) {
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "need_stable_${candidateFrames}_$REQUIRED_LABEL_FRAMES")
        }

        val now = SystemClock.elapsedRealtime()
        if (now < cooldownUntilMs) {
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "cooldown")
        }
        if (label == lockedAcceptedLabel && label !in CONTROL_LABELS) {
            return AlphabetGateResult(false, label, prediction.confidence, prediction.margin, "same_letter_hold")
        }

        lockedAcceptedLabel = if (label in CONTROL_LABELS) "" else label
        cooldownUntilMs = now + ACCEPTED_COOLDOWN_MS
        resetCandidate()
        return AlphabetGateResult(true, label, prediction.confidence, prediction.margin, "accepted")
    }

    fun resetForNoHand() {
        resetCandidate()
        lockedAcceptedLabel = ""
        cooldownUntilMs = 0L
    }

    fun resetCandidate() {
        candidateLabel = ""
        candidateFrames = 0
    }

    fun reset() {
        resetForNoHand()
    }

    private fun normalizeLabel(value: String): String {
        return when (value.trim().uppercase(Locale.US)) {
            "DEL", "DELETE" -> "DEL"
            "SPACE" -> "SPACE"
            "NOTHING" -> "NOTHING"
            else -> value.trim().uppercase(Locale.US)
        }
    }

    private fun isSupportedOutput(label: String): Boolean {
        return (label.length == 1 && label[0] in 'A'..'Z') || label in CONTROL_LABELS
    }

    companion object {
        private const val REQUIRED_STABLE_FRAMES = 8
        private const val REQUIRED_LABEL_FRAMES = 8
        private const val MIN_CONFIDENCE = 0.85f
        private const val MIN_MARGIN = 0.20f
        private const val ACCEPTED_COOLDOWN_MS = 1000L
        private const val SHAPE_RESET_MOTION = 0.055f
        private val CONTROL_LABELS = setOf("SPACE", "DEL")
    }
}
