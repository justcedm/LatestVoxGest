package com.voxgest.dryrun

import android.os.SystemClock
import java.util.Locale

data class DynamicWordGateResult(
    val accepted: Boolean,
    val label: String,
    val confidence: Float,
    val margin: Float,
    val reason: String
)

class DynamicWordAcceptanceGate {
    private var lastAcceptedLabel: String = ""
    private var cooldownUntilMs: Long = 0L
    private var movementResetSeen: Boolean = true

    fun noteNoOutputState() {
        movementResetSeen = true
    }

    fun evaluate(
        raw: RecognitionResult,
        profile: RecognitionProfile,
        decision: RouterDecision,
        handPresence: Float
    ): DynamicWordGateResult {
        val label = raw.label.trim().uppercase(Locale.US)
        if (label.isBlank()) return reject(label, raw, "empty_label")
        if (label == "NOTHING") {
            noteNoOutputState()
            return reject(label, raw, "nothing_no_output")
        }
        if (label !in profile.labels.map { it.uppercase(Locale.US) }) {
            return reject(label, raw, "unsupported_by_${profile.id}")
        }
        if (raw.note.startsWith("Wrong input shape", ignoreCase = true)) {
            return reject(label, raw, "wrong_input_shape")
        }
        val threshold = thresholdsFor(label)
        if (handPresence < MIN_HAND_PRESENCE) {
            noteNoOutputState()
            return reject(label, raw, "low_hand_presence")
        }
        if (decision.motion < threshold.motion) {
            return reject(label, raw, "low_motion")
        }
        if (raw.confidence < threshold.confidence) {
            return reject(label, raw, "low_confidence")
        }
        if (raw.margin < threshold.margin) {
            return reject(label, raw, "low_margin")
        }

        val now = SystemClock.elapsedRealtime()
        if (label == lastAcceptedLabel && now < cooldownUntilMs) {
            return reject(label, raw, "cooldown")
        }
        if (label == lastAcceptedLabel && !movementResetSeen) {
            return reject(label, raw, "repeat_without_reset")
        }

        lastAcceptedLabel = label
        cooldownUntilMs = now + ACCEPTED_COOLDOWN_MS
        movementResetSeen = false
        return DynamicWordGateResult(true, label, raw.confidence, raw.margin, "accepted")
    }

    fun reset() {
        lastAcceptedLabel = ""
        cooldownUntilMs = 0L
        movementResetSeen = true
    }

    private fun reject(label: String, raw: RecognitionResult, reason: String): DynamicWordGateResult {
        return DynamicWordGateResult(false, label, raw.confidence, raw.margin, reason)
    }

    private fun thresholdsFor(label: String): Threshold {
        return when (label) {
            "OKAY" -> Threshold(confidence = 0.82f, margin = 0.22f, motion = 0.010f)
            "MY", "NAME", "YOUR" -> Threshold(confidence = 0.72f, margin = 0.14f, motion = 0.006f)
            else -> Threshold(confidence = 0.70f, margin = 0.15f, motion = 0.008f)
        }
    }

    private data class Threshold(
        val confidence: Float,
        val margin: Float,
        val motion: Float
    )

    companion object {
        private const val MIN_HAND_PRESENCE = 0.75f
        private const val ACCEPTED_COOLDOWN_MS = 1300L
    }
}
