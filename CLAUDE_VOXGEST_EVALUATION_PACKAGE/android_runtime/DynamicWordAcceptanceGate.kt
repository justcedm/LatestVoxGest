package com.voxgest.dryrun

import android.os.SystemClock
import java.util.Locale

data class DynamicWordGateResult(
    val accepted: Boolean,
    val label: String,
    val confidence: Float,
    val margin: Float,
    val reason: String,
    val cooldownActive: Boolean = false,
    val duplicateBlocked: Boolean = false
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
        if (!isValidOneHandProfile(profile)) {
            resetStableCandidate()
            return reject(label, raw, "UNSUPPORTED_LABEL")
        }
        val threshold = thresholdFor(label)
        if (handPresence < MIN_HAND_PRESENCE) {
            noteNoOutputState()
            return reject(label, raw, "LOW_HAND_PRESENCE")
        }
        if (raw.confidence < threshold.confidence) {
            resetStableCandidate()
            return reject(label, raw, "LOW_CONFIDENCE")
        }
        if (raw.margin < threshold.margin) {
            resetStableCandidate()
            return reject(label, raw, "LOW_MARGIN")
        }

        if (shouldAcceptCalibratedHighConfidence(label, raw, profile)) {
            lastAcceptedLabel = label
            cooldownUntilMs = SystemClock.elapsedRealtime() + ACCEPTED_COOLDOWN_MS
            movementResetSeen = false
            resetStableCandidate()
            return DynamicWordGateResult(true, label, raw.confidence, raw.margin, "HIGH_CONFIDENCE")
        }

        if (label == stableCandidateLabel) {
            stableCandidateCount += 1
        } else {
            stableCandidateLabel = label
            stableCandidateCount = 1
        }

        if (label == "NAME") {
            val highConfidenceSingleWindow = raw.confidence >= NAME_SINGLE_WINDOW_CONFIDENCE
            if (!highConfidenceSingleWindow && stableCandidateCount < NAME_REQUIRED_WINDOWS) {
                return reject(label, raw, "NAME_NEEDS_SECOND_WINDOW")
            }
        }

        val now = SystemClock.elapsedRealtime()
        if (label == lastAcceptedLabel && now < cooldownUntilMs) {
            return reject(label, raw, "DUPLICATE_COOLDOWN", cooldownActive = true, duplicateBlocked = true)
        }
        if (label == lastAcceptedLabel && !movementResetSeen) {
            return reject(label, raw, "DUPLICATE_COOLDOWN", duplicateBlocked = true)
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

    private fun reject(
        label: String,
        raw: RecognitionResult,
        reason: String,
        cooldownActive: Boolean = false,
        duplicateBlocked: Boolean = false
    ): DynamicWordGateResult {
        return DynamicWordGateResult(false, label, raw.confidence, raw.margin, reason, cooldownActive, duplicateBlocked)
    }

    private fun resetStableCandidate() {
        stableCandidateLabel = ""
        stableCandidateCount = 0
    }

    private fun thresholdFor(label: String): Threshold {
        return when (label) {
            "MY", "WHAT", "YOUR" -> Threshold(confidence = 0.75f, margin = 0.12f)
            "NAME" -> Threshold(confidence = 0.70f, margin = 0.10f)
            else -> Threshold(confidence = 0.80f, margin = 0.15f)
        }
    }

    private fun isValidOneHandProfile(profile: RecognitionProfile): Boolean {
        val validProfile = profile.id == RecognitionProfile.ACTIVE_RECOGNITION_PROFILE ||
            profile.id == OneHandCalibrationConfig.CALIBRATED_PROFILE_ID
        return validProfile && profile.inputShape.contentEquals(RecognitionProfile.ONEHAND162_INPUT_SHAPE)
    }

    private fun shouldAcceptCalibratedHighConfidence(
        label: String,
        raw: RecognitionResult,
        profile: RecognitionProfile
    ): Boolean {
        return OneHandCalibrationConfig.DEBUG_ACCEPT_CALIBRATED_HIGH_CONFIDENCE &&
            profile.id == OneHandCalibrationConfig.CALIBRATED_PROFILE_ID &&
            label in ALLOWED_ONEHAND_LABELS &&
            raw.confidence >= CALIBRATED_HIGH_CONFIDENCE &&
            raw.margin >= CALIBRATED_HIGH_MARGIN
    }

    private data class Threshold(
        val confidence: Float,
        val margin: Float
    )

    companion object {
        private val ALLOWED_ONEHAND_LABELS = setOf("WHAT", "YOUR", "NAME", "MY")
        private const val MIN_HAND_PRESENCE = 0.65f
        private const val NAME_REQUIRED_WINDOWS = 2
        private const val NAME_SINGLE_WINDOW_CONFIDENCE = 0.85f
        private const val CALIBRATED_HIGH_CONFIDENCE = 0.85f
        private const val CALIBRATED_HIGH_MARGIN = 0.20f
        private const val ACCEPTED_COOLDOWN_MS = 1000L
    }
}
