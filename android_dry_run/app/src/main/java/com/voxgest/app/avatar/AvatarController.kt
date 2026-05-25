package com.voxgest.app.avatar

import android.os.Handler
import android.os.Looper

class AvatarController(
    private val onStateChanged: (AvatarPlaybackState) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var renderer: AvatarRenderer = MissingAvatarRenderer
    private var assetLoader: AvatarAssetLoader? = null
    private var lastSequence: List<String> = emptyList()
    private var lastText: String = ""
    private var state = AvatarPlaybackState()

    fun attach(view: AvatarView) {
        assetLoader = AvatarAssetLoader(view.context)
        renderer = CanvasAvatarRenderer(view, assetLoader!!)
        view.animateIdleBreathing()
        publish(
            state.copy(
                status = AvatarStatus.READY,
                label = "Ready",
                detail = if (assetLoader!!.hasModelAsset()) {
                    "GLB avatar assets detected"
                } else {
                    "Canvas avatar active"
                },
                rendererMode = renderer.rendererMode
            )
        )
    }

    fun detach() {
        handler.removeCallbacksAndMessages(null)
        renderer.stop()
        renderer.detach()
        renderer = MissingAvatarRenderer
    }

    fun setStatus(status: AvatarStatus) {
        renderer.setStatus(status)
        publish(
            when (status) {
                AvatarStatus.READY -> state.copy(status = status, label = "Ready", detail = "Avatar ready", isPlaying = false, isListening = false)
                AvatarStatus.LISTENING -> state.copy(status = status, label = "Listening", detail = "Listening for speech", isPlaying = false, isListening = true)
                AvatarStatus.ANALYZING -> state.copy(status = status, label = "Analyzing", detail = "Preparing sign response", isPlaying = false)
                AvatarStatus.SIGNING -> state.copy(status = status, label = "Signing", detail = "Playing sign", isPlaying = true, isListening = false)
                AvatarStatus.FINGERSPELLING -> state.copy(status = status, label = "Fingerspelling", detail = "Spelling unknown word", isPlaying = true, isFallback = true)
                AvatarStatus.STOPPED -> AvatarPlaybackState(status = status, label = "Ready", detail = "Avatar stopped", rendererMode = renderer.rendererMode)
                AvatarStatus.ASSET_MISSING -> state.copy(status = status, label = "Fallback", detail = "Avatar asset missing", isFallback = true)
            }.copy(rendererMode = renderer.rendererMode)
        )
    }

    fun setListening(active: Boolean) {
        renderer.setListening(active)
        publish(
            state.copy(
                status = if (active) AvatarStatus.LISTENING else AvatarStatus.READY,
                label = if (active) "Listening" else "Ready",
                detail = if (active) "Listening for speech" else "Avatar ready",
                isListening = active,
                isPlaying = false,
                rendererMode = renderer.rendererMode
            )
        )
    }

    fun playWord(word: String): Boolean {
        val clean = AvatarMotion.normalizeWord(word)
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
        val sequence = AvatarMotion.wordsFromText(text)
        if (sequence.isEmpty()) {
            return false
        }
        handler.removeCallbacksAndMessages(null)
        lastSequence = sequence
        lastText = text
        sequence.forEachIndexed { index, word ->
            handler.postDelayed({ playSingle(word) }, index * AvatarMotion.WORD_DELAY_MS)
        }
        return true
    }

    fun replay(): Boolean {
        return when {
            lastSequence.isNotEmpty() -> playTextAsSigns(lastSequence.joinToString(" "))
            lastText.isNotBlank() -> playTextAsSigns(lastText)
            else -> false
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        renderer.stop()
        publish(
            AvatarPlaybackState(
                status = AvatarStatus.STOPPED,
                label = "Ready",
                detail = "Avatar stopped",
                rendererMode = renderer.rendererMode
            )
        )
    }

    private fun playSingle(word: String) {
        if (word == "NOTHING") return
        val animation = AvatarAnimationLibrary.resolve(word)
        val fallback = animation.kind == AvatarAnimationKind.FINGERSPELL
        val canvasAssetPresent = assetLoader?.hasCanvasAnimation(word) ?: false

        if (animation.kind == AvatarAnimationKind.KNOWN_SIGN && !canvasAssetPresent) {
            renderer.setStatus(AvatarStatus.ASSET_MISSING)
        }

        renderer.playWord(word)
        publish(
            AvatarPlaybackState(
                status = if (fallback) AvatarStatus.FINGERSPELLING else AvatarStatus.SIGNING,
                label = if (fallback) "Fingerspelling" else "Signing",
                detail = if (fallback) "Fingerspelling $word" else "Playing $word",
                currentWord = word,
                isPlaying = true,
                isFallback = fallback,
                rendererMode = renderer.rendererMode
            )
        )
        handler.postDelayed({
            if (state.currentWord == word) {
                publish(
                    state.copy(
                        status = AvatarStatus.READY,
                        label = "Ready",
                        detail = "Avatar ready",
                        isPlaying = false,
                        isListening = false,
                        rendererMode = renderer.rendererMode
                    )
                )
            }
        }, AvatarMotion.STATUS_RESET_MS)
    }

    private fun publish(next: AvatarPlaybackState) {
        state = next
        onStateChanged(next)
    }
}
