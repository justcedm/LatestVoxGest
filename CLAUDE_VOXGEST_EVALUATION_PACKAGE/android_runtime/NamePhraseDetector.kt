package com.voxgest.dryrun

import java.util.Locale

data class NamePhraseUpdate(
    val mode: RecognitionMode? = null,
    val hint: String = "",
    val sentence: String = "",
    val finalized: Boolean = false
)

class NamePhraseDetector {
    private val letters = StringBuilder()
    private var active: Boolean = false
    private var lastInputAtMs: Long = 0L

    fun observeAcceptedTokens(tokens: List<String>, nowMs: Long): NamePhraseUpdate {
        val normalized = tokens.map { it.uppercase(Locale.US) }
        if (!active && normalized.takeLast(2) == listOf("MY", "NAME")) {
            active = true
            letters.clear()
            lastInputAtMs = nowMs
            return NamePhraseUpdate(
                mode = RecognitionMode.PHRASE,
                hint = NAME_HINT,
                sentence = NAME_PREFIX
            )
        }
        return NamePhraseUpdate()
    }

    fun acceptLetter(letter: Char, nowMs: Long): NamePhraseUpdate {
        if (!active || letter !in 'A'..'Z') return NamePhraseUpdate()
        letters.append(letter)
        lastInputAtMs = nowMs
        return NamePhraseUpdate(
            mode = RecognitionMode.PHRASE,
            hint = NAME_HINT,
            sentence = "$NAME_PREFIX ${letters}"
        )
    }

    fun checkPause(nowMs: Long): NamePhraseUpdate {
        if (!active || letters.isEmpty()) return NamePhraseUpdate()
        if (nowMs - lastInputAtMs < FINALIZE_PAUSE_MS) return NamePhraseUpdate()
        active = false
        return NamePhraseUpdate(
            mode = RecognitionMode.WORDS,
            hint = "",
            sentence = "$NAME_PREFIX ${letters}",
            finalized = true
        )
    }

    fun reset() {
        active = false
        letters.clear()
        lastInputAtMs = 0L
    }

    fun isActive(): Boolean = active

    companion object {
        const val NAME_HINT = "Tap letters below to spell your name"
        private const val NAME_PREFIX = "My name is"
        private const val FINALIZE_PAUSE_MS = 1500L
    }
}
