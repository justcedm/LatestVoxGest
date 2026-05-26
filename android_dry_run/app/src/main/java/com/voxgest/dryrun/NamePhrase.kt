/**
 * @file NamePhrase.kt
 * @description Phrase composer for "my name is" speech-to-sign avatar sequences.
 * @author VoxGest Team
 * @version 1.0.0
 */
package com.voxgest.dryrun

/**
 * Decomposes "my name is [name]" into MY, NAME, IS, and fingerspelled letters.
 */
fun buildNamePhraseSequence(spokenText: String): List<SignEntry>? {
    val normalized = SignVocabulary.normalizePhrase(spokenText)
    val match = Regex("\\bmy name is\\b\\s+([a-z ]+)").find(normalized) ?: return null
    val letters = match.groupValues[1].filter { it in 'a'..'z' }
    if (letters.isBlank()) return null

    val sequence = mutableListOf<SignEntry>()
    listOf("MY", "NAME", "IS").forEach { label ->
        SignVocabulary.findByLabelOrAlias(label)?.let { sequence.add(it) }
    }
    letters.forEach { char ->
        SignVocabulary.findByLabelOrAlias(char.toString())?.let { sequence.add(it) }
    }
    return if (sequence.size >= 4) sequence else null
}
