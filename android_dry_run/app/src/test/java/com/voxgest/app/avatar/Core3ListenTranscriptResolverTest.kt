package com.voxgest.app.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Core3ListenTranscriptResolverTest {
    @Test
    fun `routes only exact verified transcripts case and punctuation independently`() {
        assertEquals("HELLO", Core3ListenTranscriptResolver.resolve("hello"))
        assertEquals("HELLO", Core3ListenTranscriptResolver.resolve(" Hello! "))
        assertEquals("HELLO", Core3ListenTranscriptResolver.resolve("HELLO"))
        assertEquals("MILK", Core3ListenTranscriptResolver.resolve("milk."))
        assertEquals("RICE", Core3ListenTranscriptResolver.resolve("Rice?"))
    }

    @Test
    fun `does not guess for unsupported or multiword transcripts`() {
        assertNull(Core3ListenTranscriptResolver.resolve("water"))
        assertNull(Core3ListenTranscriptResolver.resolve("hello there"))
        assertNull(Core3ListenTranscriptResolver.resolve("milk and rice"))
        assertNull(Core3ListenTranscriptResolver.resolve(""))
    }
}
