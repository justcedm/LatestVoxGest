package com.voxgest.dryrun

import java.util.Locale

data class RecognitionOutputUpdate(
    val userFacingAccepted: Boolean,
    val reason: String,
    val allowlistDecision: DemoAllowlistDecision,
    val tokenResult: TokenResult?,
    val snapshot: TokenComposer.Snapshot,
    val suggestions: List<SentenceSuggestion>,
    val selectedSuggestion: SentenceSuggestion?
)

/**
 * Side-effect-free output boundary for recognition text.
 *
 * This class has no TTS or avatar dependency. A suggestion becomes available to those consumers
 * only through [selectedSuggestionText], after an explicit [selectSuggestion] call.
 */
class RecognitionOutputCoordinator(
    private val allowlistPolicy: DemoAllowlistPolicy,
    private val composer: TokenComposer,
    private val suggestionEngine: SentenceSuggestionEngine,
    private val suggestionSettings: SentenceSuggestionSettings
) {
    private var selectedSuggestion: SentenceSuggestion? = null

    @Synchronized
    fun handleRecognition(result: RecognitionResult?): RecognitionOutputUpdate {
        val verifiedFullSignRuntime = result?.source == RecognitionResult.Source.STANDARD_FSL105 ||
            result?.source == RecognitionResult.Source.MAPUA14_RESCUE_V1
        val allowlistDecision = if (verifiedFullSignRuntime) {
            evaluateStandardResult(requireNotNull(result))
        } else {
            allowlistPolicy.evaluate(result)
        }
        if (!allowlistDecision.userFacing) {
            return currentUpdate(
                userFacingAccepted = false,
                reason = allowlistDecision.reason.name,
                allowlistDecision = allowlistDecision,
                tokenResult = null
            )
        }

        val tokenResult = if (verifiedFullSignRuntime) {
            composer.acceptVerifiedWord(allowlistDecision.canonicalLabel)
        } else {
            composer.accept(allowlistDecision.canonicalLabel)
        }
        if (!tokenResult.accepted) {
            return currentUpdate(
                userFacingAccepted = false,
                reason = "COMPOSER_REJECTED_${tokenResult.reason.uppercase(Locale.US)}",
                allowlistDecision = allowlistDecision,
                tokenResult = tokenResult
            )
        }

        // A selection applies only to the exact snapshot that produced it.
        selectedSuggestion = null
        return currentUpdate(
            userFacingAccepted = true,
            reason = allowlistDecision.reason.name,
            allowlistDecision = allowlistDecision,
            tokenResult = tokenResult
        )
    }

    private fun evaluateStandardResult(result: RecognitionResult): DemoAllowlistDecision {
        val label = result.label.orEmpty()
        val reason = when {
            !result.accepted -> DemoAllowlistReason.UPSTREAM_REJECTED
            !result.confidence.isFinite() || !result.margin.isFinite() ->
                DemoAllowlistReason.NON_FINITE_SCORE
            label.isBlank() -> DemoAllowlistReason.EMPTY_LABEL
            DemoAllowlistPolicy.isReservedOutputToken(label) ->
                DemoAllowlistReason.NON_CANONICAL_LABEL
            else -> if (result.source == RecognitionResult.Source.MAPUA14_RESCUE_V1) {
                DemoAllowlistReason.MAPUA14_RESCUE_QUALIFIED
            } else {
                DemoAllowlistReason.STANDARD_FSL105_QUALIFIED
            }
        }
        return DemoAllowlistDecision(
            userFacing = reason == DemoAllowlistReason.STANDARD_FSL105_QUALIFIED ||
                reason == DemoAllowlistReason.MAPUA14_RESCUE_QUALIFIED,
            canonicalLabel = label,
            reason = reason,
            confidence = result.confidence,
            margin = result.margin
        )
    }

    @Synchronized
    fun selectSuggestion(id: String): SentenceSuggestion? {
        val selected = currentSuggestions().firstOrNull { it.id == id }
        selectedSuggestion = selected
        return selected
    }

    @Synchronized
    fun selectedSuggestionText(): String? = currentSelection()?.text

    @Synchronized
    fun snapshot(): TokenComposer.Snapshot = composer.snapshot()

    @Synchronized
    fun currentSuggestions(): List<SentenceSuggestion> {
        if (!suggestionSettings.isEnabled()) return emptyList()
        return suggestionEngine.suggest(composer.snapshot())
    }

    @Synchronized
    fun clear() {
        composer.clear()
        selectedSuggestion = null
    }

    private fun currentUpdate(
        userFacingAccepted: Boolean,
        reason: String,
        allowlistDecision: DemoAllowlistDecision,
        tokenResult: TokenResult?
    ): RecognitionOutputUpdate {
        val snapshot = composer.snapshot()
        val suggestions = if (suggestionSettings.isEnabled()) {
            suggestionEngine.suggest(snapshot)
        } else {
            emptyList()
        }
        val selected = selectedSuggestion?.let { prior -> suggestions.firstOrNull { it == prior } }
        return RecognitionOutputUpdate(
            userFacingAccepted = userFacingAccepted,
            reason = reason,
            allowlistDecision = allowlistDecision,
            tokenResult = tokenResult,
            snapshot = snapshot,
            suggestions = suggestions,
            selectedSuggestion = selected
        )
    }

    private fun currentSelection(): SentenceSuggestion? {
        val prior = selectedSuggestion ?: return null
        return currentSuggestions().firstOrNull { it == prior }
    }
}
