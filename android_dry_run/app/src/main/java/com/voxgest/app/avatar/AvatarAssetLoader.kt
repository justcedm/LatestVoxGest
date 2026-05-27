package com.voxgest.app.avatar

import android.content.Context

class AvatarAssetLoader(private val context: Context) {
    fun hasModelAsset(): Boolean = false

    fun hasGlbAnimation(word: String): Boolean = false

    fun hasCanvasAnimation(word: String): Boolean {
        return AvatarMotion.hasBuiltInMotion(word)
    }

    fun rendererMode(): String = "SignAvatarView Canvas renderer"

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val MODEL_PATH = ""
    }
}
