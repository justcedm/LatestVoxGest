package com.voxgest.app.avatar

import android.content.Context

class AvatarAssetLoader(private val context: Context) {
    fun hasModelAsset(): Boolean = assetExists(MODEL_PATH)

    fun hasGlbAnimation(word: String): Boolean {
        val normalized = AvatarMotion.normalizeWord(word)
        return normalized.isNotBlank() && assetExists("avatar/animations/$normalized.glb")
    }

    fun hasCanvasAnimation(word: String): Boolean {
        val animation = AvatarAnimationLibrary.resolve(word)
        val path = animation.assetPath ?: return false
        return assetExists(path)
    }

    fun rendererMode(): String {
        return if (hasModelAsset()) {
            "GLB-ready assets present"
        } else {
            "Canvas fallback"
        }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val MODEL_PATH = "avatar/models/voxgest_avatar.glb"
    }
}
