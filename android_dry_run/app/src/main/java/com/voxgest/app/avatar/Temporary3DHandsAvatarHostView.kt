package com.voxgest.app.avatar

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.IOException

class Temporary3DHandsAvatarHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val canvasAvatarView: AvatarView = AvatarView(context)

    init {
        addView(
            canvasAvatarView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (AvatarFeatureFlags.ENABLE_3D_HANDS) {
            tryEnable3DHands()
        }
    }

    private fun tryEnable3DHands() {
        if (!assetExists(AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET)) {
            Log.w(TAG, "Missing ${AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET}; using Canvas avatar")
            return
        }

        val sceneView = createSceneViewReflectively()
        if (sceneView == null) {
            Log.w(TAG, "SceneView/Filament dependency unavailable; using Canvas avatar")
            return
        }

        Log.w(TAG, "SceneView detected, but temporary GLB hand rendering is not enabled without a stable loader; using Canvas avatar")
    }

    private fun createSceneViewReflectively(): View? {
        val candidates = listOf(
            "io.github.sceneview.SceneView",
            "io.github.sceneview.ar.sceneview.ArSceneView"
        )
        for (className in candidates) {
            try {
                val sceneViewClass = Class.forName(className)
                val constructor = sceneViewClass.getConstructor(Context::class.java)
                return constructor.newInstance(context) as? View
            } catch (_: Throwable) {
                // Try the next optional renderer without forcing a Gradle dependency.
            }
        }
        return null
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        private const val TAG = "VoxGestAvatar3D"
    }
}
