package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TokenComposerSnapshotTest {
    @Test
    fun snapshotIsImmutableAndPointInTime() {
        val composer = TokenComposer(setOf("YES", "HELLO"))
        composer.accept("YES")
        val first = composer.snapshot()

        assertEquals(listOf("YES"), first.tokens)
        assertEquals("YES", first.sentence)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (first.tokens as MutableList<String>).add("HELLO")
        }

        composer.accept("HELLO")
        assertEquals(listOf("YES"), first.tokens)
        assertEquals(listOf("YES", "HELLO"), composer.snapshot().tokens)
    }

    @Test
    fun pendingLettersAreCopiedIntoSnapshot() {
        val composer = TokenComposer(emptySet())
        composer.accept("A")
        composer.accept("B")

        val snapshot = composer.snapshot()
        assertEquals(emptyList<String>(), snapshot.tokens)
        assertEquals(listOf("A", "B"), snapshot.pendingLetters)
        assertEquals("AB", snapshot.sentence)
    }
}
