package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSuggestionEngineTest {
    private val engine = SentenceSuggestionEngine()

    @Test
    fun suggestionIsDeterministicAndUsesOnlySnapshotTokensInOrder() {
        val composer = TokenComposer(setOf("GOOD_MORNING", "YES"))
        composer.accept("GOOD_MORNING")
        composer.accept("YES")
        val snapshotBefore = composer.snapshot()

        val first = engine.suggest(snapshotBefore)
        val second = engine.suggest(snapshotBefore)

        assertEquals(first, second)
        assertEquals(1, first.size)
        assertEquals("Good morning yes.", first.single().text)
        assertEquals(listOf("GOOD_MORNING", "YES"), first.single().sourceTokens)
        assertEquals(listOf("GOOD_MORNING", "YES"), composer.snapshot().tokens)
        assertEquals(snapshotBefore.sentence, composer.snapshot().sentence)
    }

    @Test
    fun pendingRecognizedLettersRemainTheOnlySuggestedContent() {
        val composer = TokenComposer(emptySet())
        composer.accept("A")
        composer.accept("B")

        val suggestion = engine.suggest(composer.snapshot()).single()

        assertEquals("AB.", suggestion.text)
        assertEquals(listOf("A", "B"), suggestion.sourceTokens)
    }

    @Test
    fun emptyCompositionHasNoSuggestion() {
        assertTrue(engine.suggest(TokenComposer(emptySet()).snapshot()).isEmpty())
    }

}
