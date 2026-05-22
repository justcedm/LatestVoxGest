package com.voxgest.app.avatar

import java.util.Locale

data class Keyframe(
    val t: Long,
    val handX: Float,
    val handY: Float,
    val elbowX: Float,
    val elbowY: Float,
    val shape: HandShape,
    val face: FaceExpression
)

enum class HandShape { OPEN_PALM, FIST, FLAT_PALM, PINCH, TWO_FINGERS, W_HAND }

enum class FaceExpression { NEUTRAL, FRIENDLY, CONFIRM, FIRM, WARM }

data class FrameState(
    val handX: Float,
    val handY: Float,
    val elbowX: Float,
    val elbowY: Float,
    val shape: HandShape,
    val face: FaceExpression
)

object AvatarMotion {
    const val WORD_DELAY_MS = 720L
    const val STATUS_RESET_MS = 760L

    val knownWords = setOf("HELLO", "THANKYOU", "WATER", "EAT")

    fun normalizeWord(word: String): String {
        return word.trim()
            .uppercase(Locale.US)
            .replace("THANK YOU", "THANKYOU")
            .replace(Regex("[^A-Z0-9]+"), "")
    }

    fun wordsFromText(text: String): List<String> {
        val normalized = text.uppercase(Locale.US)
            .replace("THANK YOU", "THANKYOU")
            .replace(Regex("[^A-Z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptyList()

        val direct = normalized.split(" ")
            .map { normalizeWord(it) }
            .filter { it.isNotBlank() && it != "NOTHING" }

        val known = direct.filter { it in knownWords }
        return if (known.isNotEmpty()) known else direct.take(4)
    }
}
