package com.voxgest.dryrun

import java.util.ArrayList
import java.util.Collections
import java.util.Locale

data class SentenceSuggestion(
    val id: String,
    val text: String,
    val sourceTokens: List<String>
)

/**
 * Deterministic, offline formatting suggestions derived only from composed recognition tokens.
 *
 * The only accepted input type is [TokenComposer.Snapshot], so callers cannot feed raw model
 * predictions into this engine. The engine never changes the supplied composer or snapshot.
 */
class SentenceSuggestionEngine {
    fun suggest(snapshot: TokenComposer.Snapshot): List<SentenceSuggestion> {
        val committedTokens = snapshot.tokens.toList()
        val pendingLetters = snapshot.pendingLetters.toList()
        val sourceTokens = Collections.unmodifiableList(
            ArrayList(committedTokens + pendingLetters)
        )
        if (sourceTokens.isEmpty()) return emptyList()

        val segments = buildList {
            committedTokens.mapTo(this) { displayToken(it) }
            if (pendingLetters.isNotEmpty()) {
                add(pendingLetters.joinToString(separator = ""))
            }
        }.filter { it.isNotBlank() }
        if (segments.isEmpty()) return emptyList()

        val literal = segments.joinToString(separator = " ")
            .replace(WHITESPACE, " ")
            .trim()
        if (literal.isEmpty()) return emptyList()

        val sentence = literal.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
        } + if (literal.last() in TERMINAL_PUNCTUATION) "" else "."

        return listOf(
            SentenceSuggestion(
                id = LITERAL_SEQUENCE_ID,
                text = sentence,
                sourceTokens = sourceTokens
            )
        )
    }

    private fun displayToken(token: String): String {
        return token.replace('_', ' ')
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase(Locale.US)
    }

    companion object {
        const val LITERAL_SEQUENCE_ID = "literal_recognized_sequence"
        private val WHITESPACE = Regex("\\s+")
        private val TERMINAL_PUNCTUATION = setOf('.', '?', '!')
    }
}
