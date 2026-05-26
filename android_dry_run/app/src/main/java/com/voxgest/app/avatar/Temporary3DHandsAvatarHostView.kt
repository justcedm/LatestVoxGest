package com.voxgest.app.avatar

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class Temporary3DHandsAvatarHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val canvasAvatarView: AvatarView = AvatarView(context)

    private var surfaceView: SurfaceView? = null
    private var modelViewer: ModelViewer? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var rendererStarted = false
    private var modelLoaded = false

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (AvatarFeatureFlags.ENABLE_3D_HANDS && modelLoaded && !rendererStarted) {
            startRenderLoop()
        }
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        destroy3DRenderer()
        super.onDetachedFromWindow()
    }

    private fun tryEnable3DHands() {
        if (!assetExists(AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET)) {
            fallback("GLB asset missing: ${AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET}")
            return
        }
        Log.i(TAG, "asset found: ${AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET}")

        try {
            Utils.init()
            val sv = SurfaceView(context).also {
                it.visibility = View.INVISIBLE
            }
            addView(
                sv,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val viewer = ModelViewer(sv)
            surfaceView = sv
            modelViewer = viewer
            Log.i(TAG, "renderer initialized")

            val buffer = readAssetBuffer(AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET)
            viewer.loadModelGlb(buffer)
            viewer.transformToUnitCube()
            configureCamera()
            modelLoaded = true
            Log.i(TAG, "model loaded: ${AvatarFeatureFlags.TEMPORARY_3D_HANDS_ASSET}")

            canvasAvatarView.visibility = View.INVISIBLE
            sv.visibility = View.VISIBLE
            startRenderLoop()
        } catch (exc: Throwable) {
            fallback("renderer failed: ${exc.message ?: exc.javaClass.simpleName}", exc)
        }
    }

    private fun startRenderLoop() {
        val viewer = modelViewer ?: return
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!rendererStarted) return
                choreographer.postFrameCallback(this)
                playFirstAnimationIfPresent(viewer, frameTimeNanos)
                viewer.render(frameTimeNanos)
            }
        }
        frameCallback = callback
        rendererStarted = true
        choreographer.postFrameCallback(callback)
    }

    private fun stopRenderLoop() {
        rendererStarted = false
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun playFirstAnimationIfPresent(viewer: ModelViewer, frameTimeNanos: Long) {
        val animator = viewer.animator ?: return
        if (animator.animationCount <= 0) return
        val duration = max(animator.getAnimationDuration(0), MIN_ANIMATION_DURATION_SECONDS)
        val seconds = (frameTimeNanos / NANOS_PER_SECOND) % duration
        animator.applyAnimation(0, seconds)
        animator.updateBoneMatrices()
    }

    private fun configureCamera() {
        val viewer = modelViewer ?: return
        val camera = viewer.camera
        camera.setProjection(
            CAMERA_FOV_DEGREES,
            1.0,
            CAMERA_NEAR,
            CAMERA_FAR,
            com.google.android.filament.Camera.Fov.VERTICAL
        )
        camera.lookAt(
            0.0,
            0.15,
            2.25,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0
        )
        viewer.scene.skybox = null
        viewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
    }

    private fun destroy3DRenderer() {
        try {
            modelViewer?.destroyModel()
        } catch (_: Throwable) {
        }
        modelViewer = null
        surfaceView = null
        modelLoaded = false
    }

    private fun fallback(reason: String, throwable: Throwable? = null) {
        Log.w(TAG, "fallback reason: $reason", throwable)
        val failedSurface = surfaceView
        stopRenderLoop()
        destroy3DRenderer()
        failedSurface?.let { removeView(it) }
        surfaceView = null
        canvasAvatarView.visibility = View.VISIBLE
    }

    private fun readAssetBuffer(assetPath: String): ByteBuffer {
        context.assets.open(assetPath).use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            return ByteBuffer.wrap(output.toByteArray())
        }
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
        private const val CAMERA_FOV_DEGREES = 38.0
        private const val CAMERA_NEAR = 0.05
        private const val CAMERA_FAR = 20.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0f
        private const val MIN_ANIMATION_DURATION_SECONDS = 1.0f
    }
}
