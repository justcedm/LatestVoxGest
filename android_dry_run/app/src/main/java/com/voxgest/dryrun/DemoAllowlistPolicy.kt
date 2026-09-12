package com.voxgest.dryrun

import java.util.Collections
import java.util.LinkedHashSet

/** Why an upstream recognition result was or was not allowed to become user-facing text. */
enum class DemoAllowlistReason {
    DEMO_QUALIFIED,
    STANDARD_FSL105_QUALIFIED,
    MISSING_RESULT,
    UPSTREAM_REJECTED,
    NON_FINITE_SCORE,
    EMPTY_LABEL,
    NON_CANONICAL_LABEL,
    NOT_DEMO_QUALIFIED
}

data class DemoAllowlistDecision(
    val userFacing: Boolean,
    val canonicalLabel: String,
    val reason: DemoAllowlistReason,
    val confidence: Float,
    val margin: Float
)

/**
 * Final, post-model boundary between an accepted classifier result and user-facing composition.
 *
 * Labels are compared exactly. This class deliberately does not uppercase, alias, or otherwise
 * normalize model output because doing so could hide a model/label contract mismatch.
 */
class DemoAllowlistPolicy(
    allowedLabels: Set<String> = DEMO_ALLOWLIST
) {
    val allowedLabels: Set<String> = Collections.unmodifiableSet(LinkedHashSet(allowedLabels))

    init {
        require(this.allowedLabels.all(::isCanonicalToken)) {
            "DEMO_ALLOWLIST entries must be exact canonical tokens"
        }
        require(this.allowedLabels.none(::isReservedOutputToken)) {
            "DEMO_ALLOWLIST must not contain no-output or UI-control tokens"
        }
    }

    fun evaluate(result: RecognitionResult?): DemoAllowlistDecision {
        if (result == null) {
            return decision(false, "", DemoAllowlistReason.MISSING_RESULT, 0f, 0f)
        }
        val label = result.label.orEmpty()
        if (!result.accepted) {
            return decision(
                false,
                label,
                DemoAllowlistReason.UPSTREAM_REJECTED,
                result.confidence,
                result.margin
            )
        }
        if (!result.confidence.isFinite() || !result.margin.isFinite()) {
            return decision(
                false,
                label,
                DemoAllowlistReason.NON_FINITE_SCORE,
                result.confidence,
                result.margin
            )
        }
        if (label.isEmpty()) {
            return decision(false, label, DemoAllowlistReason.EMPTY_LABEL, result.confidence, result.margin)
        }
        if (!isCanonicalToken(label) || isReservedOutputToken(label)) {
            return decision(
                false,
                label,
                DemoAllowlistReason.NON_CANONICAL_LABEL,
                result.confidence,
                result.margin
            )
        }
        if (label !in allowedLabels) {
            return decision(
                false,
                label,
                DemoAllowlistReason.NOT_DEMO_QUALIFIED,
                result.confidence,
                result.margin
            )
        }
        return decision(
            true,
            label,
            DemoAllowlistReason.DEMO_QUALIFIED,
            result.confidence,
            result.margin
        )
    }

    private fun decision(
        userFacing: Boolean,
        label: String,
        reason: DemoAllowlistReason,
        confidence: Float,
        margin: Float
    ): DemoAllowlistDecision {
        return DemoAllowlistDecision(userFacing, label, reason, confidence, margin)
    }

    companion object {
        /**
         * Fail closed: held-out accuracy is not live-device qualification. Add a label only after
         * its recorded Samsung acceptance test passes; never populate this from model top-1 data.
         */
        @JvmField
        val DEMO_ALLOWLIST: Set<String> = emptySet()

        private val CANONICAL_TOKEN = Regex("[A-Z0-9]+(?:_[A-Z0-9]+)*")
        private val RESERVED_OUTPUT_TOKENS = setOf(
            "NSAC",
            "IDLE",
            "REST",
            "NO_WORD",
            "NONE",
            "DEL",
            "SPACE",
            "CLEAR",
            "SPEAK"
        )

        fun isCanonicalToken(value: String): Boolean = CANONICAL_TOKEN.matches(value)

        fun isReservedOutputToken(value: String): Boolean = value in RESERVED_OUTPUT_TOKENS
    }
}
