package com.voxgest.app.avatar

interface AvatarRenderer {
    val rendererMode: String

    fun playWord(word: String): Boolean
    fun setStatus(status: AvatarStatus)
    fun setListening(active: Boolean)
    fun stop()
    fun detach()
}

class CanvasAvatarRenderer(
    private var view: AvatarView?,
    private val assetLoader: AvatarAssetLoader
) : AvatarRenderer {
    override val rendererMode: String = assetLoader.rendererMode()

    override fun playWord(word: String): Boolean {
        val normalized = AvatarMotion.normalizeWord(word)
        if (normalized.isBlank() || normalized == "NOTHING") return false
        view?.signText(normalized)
        return true
    }

    override fun setStatus(status: AvatarStatus) {
        view?.setStatus(status)
    }

    override fun setListening(active: Boolean) {
        view?.setListening(active)
    }

    override fun stop() {
        view?.signText("")
    }

    override fun detach() {
        view = null
    }
}

object MissingAvatarRenderer : AvatarRenderer {
    override val rendererMode: String = "Avatar asset missing"

    override fun playWord(word: String): Boolean = false
    override fun setStatus(status: AvatarStatus) = Unit
    override fun setListening(active: Boolean) = Unit
    override fun stop() = Unit
    override fun detach() = Unit
}
