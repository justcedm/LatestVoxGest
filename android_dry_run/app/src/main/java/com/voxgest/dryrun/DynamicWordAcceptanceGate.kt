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
    private var stableCandidateLabel: String = ""
    private var stableCandidateCount: Int = 0

    fun noteNoOutputState() {
        movementResetSeen = true
        stableCandidateLabel = ""
        stableCandidateCount = 0
    }

    fun evaluate(
        raw: RecognitionResult,
        profile: RecognitionProfile,
        decision: RouterDecision,
        handPresence: Float
    ): DynamicWordGateResult {
        val label = raw.label.trim().uppercase(Locale.US)
        if (decision.route == RecognitionRoute.NOTHING && decision.handPresence <= 0f) {
            noteNoOutputState()
            return reject(label, raw, "LOW_HAND_PRESENCE")
        }
        if (raw.note.startsWith("SHAPE_MISMATCH", ignoreCase = true) ||
            raw.note.startsWith("Wrong input shape", ignoreCase = true)
        ) {
            resetStableCandidate()
            return reject(label, raw, "SHAPE_MISMATCH")
        }
        if (label.isBlank()) {
            resetStableCandidate()
            return reject(label, raw, "BAD_SEQUENCE")
        }
        if (label == "NOTHING") {
            noteNoOutputState()
            return reject(label, raw, "NOTHING")
        }
        if (label !in ALLOWED_ONEHAND_LABELS) {
            resetStableCandidate()
            return reject(label, raw, "UNSUPPORTED_LABEL")
        }
        if (profile.id != RecognitionProfile.ACTIVE_RECOGNITION_PROFILE ||
            !profile.inputShape.contentEquals(RecognitionProfile.ONEHAND162_INPUT_SHAPE)
        ) {
            resetStableCandidate()
            return reject(label, raw, "UNSUPPORTED_LABEL")
        }
        if (handPresence < MIN_HAND_PRESENCE) {
            noteNoOutputState()
            return reject(label, raw, "LOW_HAND_PRESENCE")
        }
        if (raw.confidence < MIN_CONFIDENCE) {
            resetStableCandidate()
            return reject(label, raw, "LOW_CONFIDENCE")
        }
        if (raw.margin < MIN_MARGIN) {
            resetStableCandidate()
            return reject(label, raw, "LOW_MARGIN")
        }

        if (label == stableCandidateLabel) {
            stableCandidateCount += 1
        } else {
            stableCandidateLabel = label
            stableCandidateCount = 1
        }
        if (stableCandidateCount < REQUIRED_STABLE_PREDICTIONS) {
            return reject(label, raw, "UNSTABLE_LANDMARKS")
        }

        val now = SystemClock.elapsedRealtime()
        if (label == lastAcceptedLabel && now < cooldownUntilMs) {
            return reject(label, raw, "DUPLICATE_COOLDOWN")
        }
        if (label == lastAcceptedLabel && !movementResetSeen) {
            return reject(label, raw, "DUPLICATE_COOLDOWN")
        }

        movementResetSeen = label != lastAcceptedLabel || movementResetSeen
        lastAcceptedLabel = label
        cooldownUntilMs = now + ACCEPTED_COOLDOWN_MS
        movementResetSeen = false
        resetStableCandidate()
        return DynamicWordGateResult(true, label, raw.confidence, raw.margin, "ACCEPTED")
    }

    fun reset() {
        lastAcceptedLabel = ""
        cooldownUntilMs = 0L
        movementResetSeen = true
        resetStableCandidate()
    }

    private fun reject(label: String, raw: RecognitionResult, reason: String): DynamicWordGateResult {
        return DynamicWordGateResult(false, label, raw.confidence, raw.margin, reason)
    }

    private fun resetStableCandidate() {
        stableCandidateLabel = ""
        stableCandidateCount = 0
    }

    companion object {
        private val ALLOWED_ONEHAND_LABELS = setOf("WHAT", "YOUR", "NAME", "MY")
        private const val MIN_CONFIDENCE = 0.85f
        private const val MIN_MARGIN = 0.25f
        private const val MIN_HAND_PRESENCE = 0.75f
        private const val REQUIRED_STABLE_PREDICTIONS = 3
        private const val ACCEPTED_COOLDOWN_MS = 1200L
    }
}
