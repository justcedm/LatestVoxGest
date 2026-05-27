/**
 * @file SignVocabulary.kt
 * @description Canonical VoxGest sign vocabulary, aliases, categories, and speech mapping helpers.
 * @author VoxGest Team
 * @version 1.0.0
 */
package com.voxgest.dryrun

import java.util.Locale

enum class SignCategory {
    GREETING,
    EMERGENCY,
    MEDICAL,
    DAILY,
    CONVERSATION,
    ALPHABET,
    PHRASE
}

data class SignEntry(
    val label: String,
    val animationClip: String,
    val category: SignCategory,
    val aliases: List<String>
)

object SignVocabulary {
    val entries: List<SignEntry> = buildList {
        add(SignEntry("HELLO", "HELLO", SignCategory.GREETING, listOf("hello", "hi", "hey")))
        add(SignEntry("GOODBYE", "GOODBYE", SignCategory.GREETING, listOf("goodbye", "bye", "see you")))
        add(SignEntry("THANK YOU", "THANKYOU", SignCategory.GREETING, listOf("thank you", "thanks", "thank", "thankyou")))
        add(SignEntry("PLEASE", "PLEASE", SignCategory.GREETING, listOf("please")))
        add(SignEntry("SORRY", "SORRY", SignCategory.GREETING, listOf("sorry", "apologize", "excuse me")))
        add(SignEntry("YOU'RE WELCOME", "YOUREWELCOME", SignCategory.GREETING, listOf("you're welcome", "welcome", "no problem")))

        add(SignEntry("HELP", "HELP", SignCategory.EMERGENCY, listOf("help", "i need help", "emergency")))
        add(SignEntry("STOP", "STOP", SignCategory.EMERGENCY, listOf("stop", "halt", "wait")))
        add(SignEntry("DANGER", "DANGER", SignCategory.EMERGENCY, listOf("danger", "dangerous", "careful")))
        add(SignEntry("CALL 911", "CALL911", SignCategory.EMERGENCY, listOf("call 911", "emergency services")))

        add(SignEntry("DOCTOR", "DOCTOR", SignCategory.MEDICAL, listOf("doctor", "physician")))
        add(SignEntry("HOSPITAL", "HOSPITAL", SignCategory.MEDICAL, listOf("hospital", "clinic")))
        add(SignEntry("PAIN", "PAIN", SignCategory.MEDICAL, listOf("pain", "hurts", "it hurts")))
        add(SignEntry("MEDICINE", "MEDICINE", SignCategory.MEDICAL, listOf("medicine", "medication", "pill")))
        add(SignEntry("SICK", "SICK", SignCategory.MEDICAL, listOf("sick", "ill", "not well")))
        add(SignEntry("ALLERGY", "ALLERGY", SignCategory.MEDICAL, listOf("allergy", "allergic")))

        add(SignEntry("WATER", "WATER", SignCategory.DAILY, listOf("water", "drink water")))
        add(SignEntry("FOOD", "EAT", SignCategory.DAILY, listOf("food", "eat", "hungry")))
        add(SignEntry("BATHROOM", "BATHROOM", SignCategory.DAILY, listOf("bathroom", "restroom", "toilet")))
        add(SignEntry("SLEEP", "SLEEP", SignCategory.DAILY, listOf("sleep", "tired", "rest")))
        add(SignEntry("YES", "YES", SignCategory.DAILY, listOf("yes", "correct", "right")))
        add(SignEntry("OKAY", "OKAY", SignCategory.DAILY, listOf("okay", "ok", "are you okay")))
        add(SignEntry("NO", "NO", SignCategory.DAILY, listOf("no", "nope", "negative")))
        add(SignEntry("PLEASE WAIT", "PLEASEWAIT", SignCategory.DAILY, listOf("please wait", "hold on", "one moment")))
        add(SignEntry("UNDERSTAND", "UNDERSTAND", SignCategory.DAILY, listOf("understand", "i understand", "i see")))
        add(SignEntry("REPEAT", "REPEAT", SignCategory.DAILY, listOf("repeat", "say again", "one more time")))

        add(SignEntry("MY", "MY", SignCategory.CONVERSATION, listOf("my", "mine")))
        add(SignEntry("NAME", "NAME", SignCategory.CONVERSATION, listOf("name")))
        add(SignEntry("IS", "IS", SignCategory.CONVERSATION, listOf("is")))
        add(SignEntry("WHAT", "WHAT", SignCategory.CONVERSATION, listOf("what")))
        add(SignEntry("YOUR", "YOUR", SignCategory.CONVERSATION, listOf("your")))
        add(SignEntry("YOU", "YOU", SignCategory.CONVERSATION, listOf("you")))
        add(SignEntry("STUDENT", "STUDENT", SignCategory.CONVERSATION, listOf("student")))
        add(SignEntry("WHERE", "WHERE", SignCategory.CONVERSATION, listOf("where")))
        add(SignEntry("LIVE", "LIVE", SignCategory.CONVERSATION, listOf("live", "lives")))
        add(SignEntry("HOW", "HOW", SignCategory.CONVERSATION, listOf("how")))
        add(SignEntry("WHO", "WHO", SignCategory.CONVERSATION, listOf("who")))
        add(SignEntry("WHEN", "WHEN", SignCategory.CONVERSATION, listOf("when")))
        add(SignEntry("I LOVE YOU", "ILOVEYOU", SignCategory.CONVERSATION, listOf("i love you", "love you")))
        add(SignEntry("NICE TO MEET YOU", "NICETOMEETYOU", SignCategory.CONVERSATION, listOf("nice to meet you", "pleased to meet you")))

        for (letter in 'A'..'Z') {
            add(
                SignEntry(
                    label = letter.toString(),
                    animationClip = "LETTER_$letter",
                    category = SignCategory.ALPHABET,
                    aliases = listOf(letter.lowercaseChar().toString(), "letter ${letter.lowercaseChar()}")
                )
            )
        }
    }

    fun findByLabelOrAlias(value: String): SignEntry? {
        val normalized = normalizePhrase(value)
        if (normalized.isBlank()) return null
        return entries.firstOrNull { entry ->
            normalizePhrase(entry.label) == normalized ||
                normalizeRuntimeLabel(entry.label).lowercase(Locale.US) == normalized ||
                entry.aliases.any { normalizePhrase(it) == normalized }
        }
    }

    fun labelsForSpeech(text: String): List<String> {
        val tokens = normalizePhrase(text).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val output = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            var matched: SignEntry? = null
            var matchedWindow = 0
            val maxWindow = minOf(4, tokens.size - index)
            for (window in maxWindow downTo 1) {
                val phrase = tokens.subList(index, index + window).joinToString(" ")
                val candidate = findByLabelOrAlias(phrase)
                if (candidate != null) {
                    matched = candidate
                    matchedWindow = window
                    break
                }
            }
            if (matched != null) {
                output.add(playbackLabel(matched))
                index += matchedWindow
            } else {
                output.add(tokens[index].uppercase(Locale.US))
                index += 1
            }
        }
        return output.filter { it.isNotBlank() && it != "NOTHING" }
    }

    fun playbackLabel(entry: SignEntry): String {
        val baseName = entry.animationClip.substringBeforeLast(".")
        return if (entry.category == SignCategory.ALPHABET && baseName.startsWith("LETTER_")) {
            normalizeRuntimeLabel(entry.label)
        } else {
            normalizeRuntimeLabel(baseName)
        }
    }

    fun normalizeRuntimeLabel(label: String): String {
        return label.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "")
    }

    fun normalizePhrase(text: String): String {
        return text.lowercase(Locale.US)
            .replace("thankyou", "thank you")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
