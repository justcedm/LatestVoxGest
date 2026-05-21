package com.voxgest.dryrun

import java.util.Locale

data class AvatarPhraseResult(
    val displayText: String,
    val matchLabel: String,
    val sequence: List<String>,
    val sequenceLabel: String
)

object AvatarPhraseMapper {
    private val directlyAnimated = setOf("YES", "NO", "HELLO", "THANKYOU", "WATER", "EAT")
    private val controlledFallback = setOf("HELP", "PLEASE", "STOP", "DOCTOR")
    private val fingerspellOnly = setOf("NAME", "GO", "BATHROOM", "PAIN", "SICK", "MEDICINE", "HOSPITAL", "HURT")

    fun map(input: String): AvatarPhraseResult {
        val display = input.trim().ifBlank { "Ready" }
        val normalized = normalize(display)
        val sequence = when {
            normalized.isBlank() -> emptyList()
            normalized == "yes" -> listOf("YES")
            normalized == "no" -> listOf("NO")
            normalized == "hello" || normalized.contains("hello") -> listOf("HELLO")
            normalized == "thanks" || normalized == "thank you" || normalized == "thankyou" ||
                normalized.contains("thank you") -> listOf("THANKYOU")
            normalized == "water" || normalized.contains("water") -> listOf("WATER")
            normalized == "eat" || normalized.contains("eat") || normalized.contains("food") -> listOf("EAT")
            normalized.contains("your name") || normalized.contains("name") -> listOf("NAME")
            normalized.contains("help") -> listOf("HELP")
            normalized.contains("please") -> listOf("PLEASE")
            normalized.contains("doctor") -> listOf("DOCTOR")
            normalized.contains("stop") -> listOf("STOP")
            normalized.contains("where") && normalized.contains("go") -> listOf("GO")
            normalized.contains("bathroom") -> listOf("BATHROOM")
            normalized.contains("hurt") -> listOf("HURT")
            normalized.contains("pain") -> listOf("PAIN")
            normalized.contains("sick") -> listOf("SICK")
            normalized.contains("medicine") -> listOf("MEDICINE")
            normalized.contains("hospital") -> listOf("HOSPITAL")
            else -> wordsForFingerspelling(normalized)
        }
        val label = when {
            sequence.isEmpty() -> "Ready"
            sequence.all { it in directlyAnimated } -> "Avatar animation"
            sequence.any { it in controlledFallback } -> "Placeholder sign"
            sequence.any { it in fingerspellOnly } -> "Fingerspelling fallback"
            else -> "Fingerspelling fallback"
        }
        val sequenceLabel = if (sequence.isEmpty()) "idle" else sequence.joinToString(" -> ")
        return AvatarPhraseResult(
            displayText = display,
            matchLabel = label,
            sequence = sequence,
            sequenceLabel = "Sequence: $sequenceLabel"
        )
    }

    fun isAnimatedWord(word: String): Boolean = word.uppercase(Locale.US) in directlyAnimated

    fun isPlaceholderWord(word: String): Boolean = word.uppercase(Locale.US) in controlledFallback

    private fun normalize(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun wordsForFingerspelling(value: String): List<String> {
        return value.split(' ')
            .filter { it.isNotBlank() }
            .take(4)
            .map { it.uppercase(Locale.US) }
    }
}
