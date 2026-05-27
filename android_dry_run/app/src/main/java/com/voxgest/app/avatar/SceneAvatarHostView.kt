package com.voxgest.app.avatar

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class SceneAvatarHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val canvasAvatarView: AvatarView = AvatarView(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressBar = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(TEAL)
        visibility = View.GONE
    }

    private var surfaceView: SurfaceView? = null
    private var modelViewer: ModelViewer? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var loadJob: Job? = null
    private var rendererStarted = false
    private var modelLoaded = false
    private var embeddedAnimationAvailable = false
    private var breathingBone: BreathingBone? = null
    private val fillLightEntities = mutableListOf<Int>()

    private var fpsWindowStartNanos = 0L
    private var fpsFrameCount = 0
    var lastMeasuredFps: Float = 0f
        private set

    init {
        addView(
            canvasAvatarView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        addView(
            progressBar,
            LayoutParams(PROGRESS_SIZE_PX, PROGRESS_SIZE_PX, Gravity.CENTER)
        )
        if (AvatarFeatureFlags.ENABLE_3D_AVATAR) {
            tryEnableSceneAvatar()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (AvatarFeatureFlags.ENABLE_3D_AVATAR && modelLoaded && !rendererStarted) {
            startRenderLoop()
        }
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        stopRenderLoop()
        destroy3DRenderer()
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun tryEnableSceneAvatar() {
        val assetPath = AvatarFeatureFlags.AVATURN_AVATAR_ASSET
        if (!assetExists(assetPath)) {
            fallback("GLB asset missing: $assetPath")
            return
        }
        Log.i(TAG, "asset found: $assetPath")
        showLoading(true)

        try {
            Utils.init()
            val sv = SurfaceView(context).also {
                it.visibility = View.INVISIBLE
            }
            addView(
                sv,
                1,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val viewer = ModelViewer(sv)
            surfaceView = sv
            modelViewer = viewer
            configureSceneDefaults(viewer)
            Log.i(TAG, "renderer initialized")

            loadJob = scope.launch {
                val buffer = withContext(Dispatchers.IO) { readAssetBuffer(assetPath) }
                try {
                    viewer.loadModelGlb(buffer)
                    frameAvatarRoot(viewer)
                    configureCamera(viewer)
                    configureLights(viewer)
                    bindRig(viewer)
                    embeddedAnimationAvailable = (viewer.animator?.animationCount ?: 0) > 0
                    modelLoaded = true
                    Log.i(TAG, "model loaded: $assetPath")
                    if (embeddedAnimationAvailable) {
                        Log.i(TAG, "embedded animation count: ${viewer.animator?.animationCount ?: 0}")
                    } else {
                        Log.i(TAG, "no embedded animations; procedural breathing idle enabled")
                    }
                    canvasAvatarView.visibility = View.INVISIBLE
                    sv.visibility = View.VISIBLE
                    showLoading(false)
                    startRenderLoop()
                } catch (exc: Throwable) {
                    fallback("model load failed: ${exc.message ?: exc.javaClass.simpleName}", exc)
                }
            }
        } catch (exc: Throwable) {
            fallback("renderer failed: ${exc.message ?: exc.javaClass.simpleName}", exc)
        }
    }

    private fun startRenderLoop() {
        val viewer = modelViewer ?: return
        if (rendererStarted) return
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!rendererStarted) return
                choreographer.postFrameCallback(this)
                applyIdleOrEmbeddedAnimation(viewer, frameTimeNanos)
                viewer.render(frameTimeNanos)
                trackFps(frameTimeNanos)
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
        fpsWindowStartNanos = 0L
        fpsFrameCount = 0
    }

    private fun applyIdleOrEmbeddedAnimation(viewer: ModelViewer, frameTimeNanos: Long) {
        val animator = viewer.animator
        if (embeddedAnimationAvailable && animator != null && animator.animationCount > 0) {
            val duration = max(animator.getAnimationDuration(0), MIN_ANIMATION_DURATION_SECONDS)
            val seconds = ((frameTimeNanos / NANOS_PER_SECOND) % duration).toFloat()
            animator.applyAnimation(0, seconds)
            animator.updateBoneMatrices()
            return
        }
        applyProceduralBreathing(viewer, frameTimeNanos)
    }

    private fun applyProceduralBreathing(viewer: ModelViewer, frameTimeNanos: Long) {
        val bone = breathingBone ?: return
        val seconds = frameTimeNanos / NANOS_PER_SECOND
        val angleRadians = BREATHING_AMPLITUDE_DEGREES.toRadians() * sin(2.0 * PI * BREATHING_HZ * seconds)
        val transformManager = viewer.engine.transformManager
        val animated = multiplyColumnMajor(bone.baseTransform, rotationXColumnMajor(angleRadians.toFloat()))
        transformManager.setTransform(bone.instance, animated)
    }

    private fun trackFps(frameTimeNanos: Long) {
        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = frameTimeNanos
            fpsFrameCount = 0
            return
        }
        fpsFrameCount += 1
        val elapsed = frameTimeNanos - fpsWindowStartNanos
        if (elapsed >= FPS_LOG_WINDOW_NANOS) {
            lastMeasuredFps = fpsFrameCount * NANOS_PER_SECOND_FLOAT / elapsed
            Log.i(TAG, "fps=${"%.1f".format(lastMeasuredFps)}")
            if (lastMeasuredFps < MIN_TARGET_FPS) {
                Log.w(TAG, "fps below target: ${"%.1f".format(lastMeasuredFps)} < $MIN_TARGET_FPS")
            }
            fpsWindowStartNanos = frameTimeNanos
            fpsFrameCount = 0
        }
    }

    private fun configureSceneDefaults(viewer: ModelViewer) {
        viewer.scene.skybox = null
        viewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
    }

    private fun configureCamera(viewer: ModelViewer) {
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
            1.2,
            2.8,
            0.0,
            1.0,
            0.0,
            0.0,
            1.0,
            0.0
        )
    }

    private fun frameAvatarRoot(viewer: ModelViewer) {
        viewer.transformToUnitCube(Float3(0.0f, 0.0f, 0.0f))
        val root = viewer.asset?.root ?: return
        val transformManager = viewer.engine.transformManager
        if (!transformManager.hasComponent(root)) return
        val instance = transformManager.getInstance(root)
        val base = FloatArray(MATRIX_SIZE)
        transformManager.getTransform(instance, base)
        val adjustment = translationScaleColumnMajor(
            scale = AVATAR_ROOT_SCALE,
            translateX = 0f,
            translateY = AVATAR_ROOT_TRANSLATE_Y,
            translateZ = 0f
        )
        transformManager.setTransform(instance, multiplyColumnMajor(adjustment, base))
    }

    private fun configureLights(viewer: ModelViewer) {
        val scene = viewer.scene
        val engine = viewer.engine
        val lightManager = engine.lightManager
        scene.indirectLight?.intensity = AMBIENT_INTENSITY * FILAMENT_LIGHT_SCALE

        val key = viewer.light
        if (key != 0 && lightManager.hasComponent(key)) {
            val instance = lightManager.getInstance(key)
            lightManager.setColor(instance, 1.0f, 1.0f, 1.0f)
            lightManager.setDirection(instance, -0.45f, -0.85f, -0.35f)
            lightManager.setIntensity(instance, KEY_LIGHT_INTENSITY * FILAMENT_LIGHT_SCALE)
        } else {
            val keyEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .direction(-0.45f, -0.85f, -0.35f)
                .intensity(KEY_LIGHT_INTENSITY * FILAMENT_LIGHT_SCALE)
                .castShadows(false)
                .build(engine, keyEntity)
            scene.addEntity(keyEntity)
            fillLightEntities += keyEntity
        }

        val fillEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.72f, 0.93f, 0.92f)
            .direction(0.75f, -0.35f, -0.25f)
            .intensity(FILL_LIGHT_INTENSITY * FILAMENT_LIGHT_SCALE)
            .castShadows(false)
            .build(engine, fillEntity)
        scene.addEntity(fillEntity)
        fillLightEntities += fillEntity
        Log.i(TAG, "lighting configured: ambient=$AMBIENT_INTENSITY key=$KEY_LIGHT_INTENSITY fill=$FILL_LIGHT_INTENSITY")
    }

    private fun bindRig(viewer: ModelViewer) {
        val asset = viewer.asset
        Log.i(TAG, MixamoAvatarRig.describeResolvedBones(asset))
        val chestEntity = MixamoAvatarRig.findBone(asset, "chest")
        if (chestEntity == 0) {
            Log.w(TAG, "procedural idle fallback reason: chest bone not found")
            return
        }

        val transformManager = viewer.engine.transformManager
        if (!transformManager.hasComponent(chestEntity)) {
            Log.w(TAG, "procedural idle fallback reason: chest transform missing")
            return
        }

        val instance = transformManager.getInstance(chestEntity)
        val base = FloatArray(MATRIX_SIZE)
        transformManager.getTransform(instance, base)
        breathingBone = BreathingBone(chestEntity, instance, base)
        Log.i(TAG, "procedural breathing bound to chest entity=$chestEntity")
    }

    private fun destroy3DRenderer() {
        val viewer = modelViewer
        if (viewer != null) {
            for (entity in fillLightEntities) {
                try {
                    viewer.scene.removeEntity(entity)
                    if (viewer.engine.lightManager.hasComponent(entity)) {
                        viewer.engine.lightManager.destroy(entity)
                    }
                    EntityManager.get().destroy(entity)
                } catch (_: Throwable) {
                }
            }
            fillLightEntities.clear()
            try {
                viewer.destroyModel()
            } catch (_: Throwable) {
            }
            try {
                viewer.destroy()
            } catch (_: Throwable) {
            }
        }
        modelViewer = null
        surfaceView = null
        modelLoaded = false
        embeddedAnimationAvailable = false
        breathingBone = null
    }

    private fun fallback(reason: String, throwable: Throwable? = null) {
        Log.w(TAG, "fallback reason: $reason", throwable)
        val failedSurface = surfaceView
        stopRenderLoop()
        destroy3DRenderer()
        failedSurface?.let { removeView(it) }
        showLoading(false)
        canvasAvatarView.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        progressBar.bringToFront()
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

    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun rotationXColumnMajor(angleRadians: Float): FloatArray {
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun translationScaleColumnMajor(
        scale: Float,
        translateX: Float,
        translateY: Float,
        translateZ: Float
    ): FloatArray {
        return floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            translateX, translateY, translateZ, 1f
        )
    }

    private fun multiplyColumnMajor(left: FloatArray, right: FloatArray): FloatArray {
        val out = FloatArray(MATRIX_SIZE)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                out[column * 4 + row] =
                    left[0 * 4 + row] * right[column * 4 + 0] +
                    left[1 * 4 + row] * right[column * 4 + 1] +
                    left[2 * 4 + row] * right[column * 4 + 2] +
                    left[3 * 4 + row] * right[column * 4 + 3]
            }
        }
        return out
    }

    private data class BreathingBone(
        val entity: Int,
        val instance: Int,
        val baseTransform: FloatArray
    )

    companion object {
        private const val TAG = "VoxGestAvatar3D"
        private const val CAMERA_FOV_DEGREES = 36.0
        private const val CAMERA_NEAR = 0.05
        private const val CAMERA_FAR = 20.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NANOS_PER_SECOND_FLOAT = 1_000_000_000f
        private const val MIN_ANIMATION_DURATION_SECONDS = 1.0f
        private const val BREATHING_HZ = 0.4
        private const val BREATHING_AMPLITUDE_DEGREES = 1.5
        private const val MATRIX_SIZE = 16
        private const val PROGRESS_SIZE_PX = 56
        private const val TEAL = 0xFF00897B.toInt()
        private const val MIN_TARGET_FPS = 30f
        private const val FPS_LOG_WINDOW_NANOS = 2_000_000_000L
        private const val AMBIENT_INTENSITY = 1.0f
        private const val KEY_LIGHT_INTENSITY = 1.8f
        private const val FILL_LIGHT_INTENSITY = 0.6f
        private const val FILAMENT_LIGHT_SCALE = 50_000f
        private const val AVATAR_ROOT_SCALE = 1.10f
        private const val AVATAR_ROOT_TRANSLATE_Y = -0.90f
    }
}
