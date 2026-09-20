package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOutputCoordinatorTest {
    @Test
    fun rawAndNonqualifiedResultsCannotReachComposer() {
        val fixture = fixture(allowlist = setOf("YES"), composerLabels = setOf("YES"))

        val raw = fixture.coordinator.handleRecognition(
            RecognitionResult("YES", 0.99f, 0.8f, false, "raw_tflite")
        )
        val disabled = fixture.coordinator.handleRecognition(accepted("NO"))

        assertFalse(raw.userFacingAccepted)
        assertEquals(DemoAllowlistReason.UPSTREAM_REJECTED.name, raw.reason)
        assertFalse(disabled.userFacingAccepted)
        assertEquals(DemoAllowlistReason.NOT_DEMO_QUALIFIED.name, disabled.reason)
        assertTrue(fixture.coordinator.snapshot().tokens.isEmpty())
    }

    @Test
    fun optInSuggestionsRequireExplicitSelectionAndNeverChangeComposition() {
        val fixture = fixture(allowlist = setOf("YES"), composerLabels = setOf("YES"))
        val accepted = fixture.coordinator.handleRecognition(accepted("YES"))

        assertTrue(accepted.userFacingAccepted)
        assertEquals(listOf("YES"), accepted.snapshot.tokens)
        assertTrue(accepted.suggestions.isEmpty())
        assertNull(fixture.coordinator.selectedSuggestionText())

        fixture.settings.setEnabled(true)
        val suggestions = fixture.coordinator.currentSuggestions()
        assertEquals("Yes.", suggestions.single().text)
        assertNull(fixture.coordinator.selectedSuggestionText())
        assertNull(fixture.coordinator.selectSuggestion("not-a-suggestion"))

        fixture.coordinator.selectSuggestion(suggestions.single().id)
        assertEquals("Yes.", fixture.coordinator.selectedSuggestionText())
        assertEquals(listOf("YES"), fixture.coordinator.snapshot().tokens)

        // New recognition invalidates the selection without changing prior history entries.
        fixture.coordinator.handleRecognition(accepted("YES"))
        assertNull(fixture.coordinator.selectedSuggestionText())
        assertEquals(listOf("YES", "YES"), fixture.coordinator.snapshot().tokens)
    }

    @Test
    fun allowlistedButUnknownComposerLabelStillFailsClosed() {
        val fixture = fixture(allowlist = setOf("YES"), composerLabels = emptySet())
        val update = fixture.coordinator.handleRecognition(accepted("YES"))

        assertFalse(update.userFacingAccepted)
        assertTrue(update.reason.startsWith("COMPOSER_REJECTED_"))
        assertTrue(update.snapshot.tokens.isEmpty())
    }

    @Test
    fun verifiedStandardResultBypassesDemoAllowlistAndLegacyComposerVocabulary() {
        val fixture = fixture(allowlist = setOf("WHAT"), composerLabels = setOf("WHAT"))
        val standard = RecognitionResult(
            "GOOD MORNING",
            0.91f,
            0.44f,
            true,
            "standard_fsl105_gate_accepted",
            emptyList(),
            RecognitionResult.Source.STANDARD_FSL105
        )

        val update = fixture.coordinator.handleRecognition(standard)

        assertTrue(update.userFacingAccepted)
        assertEquals(DemoAllowlistReason.STANDARD_FSL105_QUALIFIED.name, update.reason)
        assertEquals(listOf("GOOD MORNING"), update.snapshot.tokens)
    }

    @Test
    fun legacyResultStillCannotBypassDemoAllowlist() {
        val fixture = fixture(allowlist = setOf("WHAT"), composerLabels = setOf("WHAT"))

        val update = fixture.coordinator.handleRecognition(accepted("HELLO"))

        assertFalse(update.userFacingAccepted)
        assertEquals(DemoAllowlistReason.NOT_DEMO_QUALIFIED.name, update.reason)
    }

    @Test
    fun verifiedMapua14ResultUsesItsOwnNonDemoQualificationLane() {
        val fixture = fixture(allowlist = emptySet(), composerLabels = emptySet())
        val result = RecognitionResult(
            "THANK_YOU",
            0.91f,
            0.44f,
            true,
            "mapua14_rescue_v1_accepted",
            emptyList(),
            RecognitionResult.Source.MAPUA14_RESCUE_V1
        )

        val update = fixture.coordinator.handleRecognition(result)

        assertTrue(update.userFacingAccepted)
        assertEquals(DemoAllowlistReason.MAPUA14_RESCUE_QUALIFIED.name, update.reason)
        assertEquals(listOf("THANK_YOU"), update.snapshot.tokens)
    }

    @Test
    fun experimentalPractical15ResultUsesItsOwnSourceLabel() {
        val fixture = fixture(allowlist = emptySet(), composerLabels = emptySet())
        val result = RecognitionResult(
            "HOW_MUCH",
            0.97f,
            0.55f,
            true,
            "fsl_practical15_v1_experimental_accepted",
            emptyList(),
            RecognitionResult.Source.FSL_PRACTICAL15_V1
        )

        val update = fixture.coordinator.handleRecognition(result)

        assertTrue(update.userFacingAccepted)
        assertEquals(DemoAllowlistReason.FSL_PRACTICAL15_EXPERIMENTAL.name, update.reason)
        assertEquals(listOf("HOW_MUCH"), update.snapshot.tokens)
    }

    private fun fixture(allowlist: Set<String>, composerLabels: Set<String>): Fixture {
        val settings = SentenceSuggestionSettings(MemoryStore())
        return Fixture(
            RecognitionOutputCoordinator(
                allowlistPolicy = DemoAllowlistPolicy(allowlist),
                composer = TokenComposer(composerLabels),
                suggestionEngine = SentenceSuggestionEngine(),
                suggestionSettings = settings
            ),
            settings
        )
    }

    private fun accepted(label: String): RecognitionResult {
        return RecognitionResult(label, 0.9f, 0.5f, true, "upstream_gate_accepted")
    }

    private data class Fixture(
        val coordinator: RecognitionOutputCoordinator,
        val settings: SentenceSuggestionSettings
    )

    private class MemoryStore : SentenceSuggestionSettings.Store {
        private val values = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return values[key] ?: defaultValue
        }

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
