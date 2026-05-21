package com.voxgest.app.avatar

import android.os.Handler
import android.os.Looper
import java.util.Locale

data class AvatarPlaybackState(
    val label: String = "Ready",
    val detail: String = "Avatar ready",
    val currentWord: String = "",
    val isPlaying: Boolean = false,
    val isListening: Boolean = false,
    val isFallback: Boolean = false
)

class AvatarController(
    private val onStateChanged: (AvatarPlaybackState) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var avatarView: AvatarView? = null
    private var lastSequence: List<String> = emptyList()
    private var lastText: String = ""
    private var state = AvatarPlaybackState()

    fun attach(view: AvatarView) {
        avatarView = view
        view.animateIdleBreathing()
        publish(state)
    }

    fun detach() {
        handler.removeCallbacksAndMessages(null)
        avatarView = null
    }

    fun setListening(active: Boolean) {
        avatarView?.setListening(active)
        publish(
            state.copy(
                label = if (active) "Listening" else "Ready",
                detail = if (active) "Listening for speech" else "Avatar ready",
                isListening = active,
                isPlaying = false
            )
        )
    }

    fun playWord(word: String): Boolean {
        val clean = normalizeWord(word)
        if (clean.isBlank() || clean == "NOTHING") {
            return false
        }
        handler.removeCallbacksAndMessages(null)
        lastSequence = listOf(clean)
        lastText = clean
        playSingle(clean)
        return true
    }

    fun playTextAsSigns(text: String): Boolean {
        val sequence = wordsFromText(text)
        if (sequence.isEmpty()) {
            return false
        }
        handler.removeCallbacksAndMessages(null)
        lastSequence = sequence
        lastText = text
        sequence.forEachIndexed { index, word ->
            handler.postDelayed({ playSingle(word) }, index * WORD_DELAY_MS)
        }
        return true
    }

    fun replay(): Boolean {
        return when {
            lastSequence.isNotEmpty() -> playTextAsSigns(lastSequence.joinToString(" "))
            lastText.isNotBlank() -> playTextAsSigns(lastText)
            else -> playWord("HELLO")
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        avatarView?.signText("")
        publish(AvatarPlaybackState(label = "Ready", detail = "Avatar stopped"))
    }

    private fun playSingle(word: String) {
        if (word == "NOTHING") return
        val fallback = word !in KNOWN_ANIMATIONS
        avatarView?.signText(word)
        publish(
            AvatarPlaybackState(
                label = if (fallback) "Fingerspelling" else "Signing",
                detail = if (fallback) "Fingerspelling $word" else "Playing $word",
                currentWord = word,
                isPlaying = true,
                isFallback = fallback
            )
        )
        handler.postDelayed({
            if (state.currentWord == word) {
                publish(
                    state.copy(
                        label = "Ready",
                        detail = "Avatar ready",
                        isPlaying = false,
                        isListening = false
                    )
                )
            }
        }, STATUS_RESET_MS)
    }

    private fun publish(next: AvatarPlaybackState) {
        state = next
        onStateChanged(next)
    }

    private fun wordsFromText(text: String): List<String> {
        val normalized = text.uppercase(Locale.US)
            .replace("THANK YOU", "THANKYOU")
            .replace(Regex("[^A-Z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptyList()

        val direct = normalized.split(" ")
            .map { normalizeWord(it) }
            .filter { it.isNotBlank() && it != "NOTHING" }

        val known = direct.filter { it in KNOWN_ANIMATIONS }
        return if (known.isNotEmpty()) known else direct.take(4)
    }

    private fun normalizeWord(word: String): String {
        return word.trim()
            .uppercase(Locale.US)
            .replace("THANK YOU", "THANKYOU")
            .replace(Regex("[^A-Z0-9]+"), "")
    }

    companion object {
        private const val WORD_DELAY_MS = 720L
        private const val STATUS_RESET_MS = 760L
        val KNOWN_ANIMATIONS = setOf("HELLO", "THANKYOU", "WATER", "EAT")
    }
}
