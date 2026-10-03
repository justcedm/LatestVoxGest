package com.voxgest.app.avatar

import java.util.Locale

/**
 * Strict speech-to-CORE3 routing for Listen.
 *
 * Only complete transcripts that normalize to one verified canonical action are accepted. This
 * intentionally rejects phrases and mixed words rather than guessing an FSL animation.
 */
object Core3ListenTranscriptResolver {
    private val unsupportedCharacters = Regex("[^A-Z]+")
    private val verifiedLabels = setOf("HELLO", "MILK", "RICE")

    fun resolve(transcript: String): String? {
        val normalized = unsupportedCharacters
            .replace(transcript.trim().uppercase(Locale.ROOT), " ")
            .trim()
        return normalized.takeIf(verifiedLabels::contains)
    }
}
