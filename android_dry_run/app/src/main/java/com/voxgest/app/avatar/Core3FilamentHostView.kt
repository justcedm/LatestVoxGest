package com.voxgest.app.avatar

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Lazy, single-model Filament host for Astra's immutable CORE3 GLB.
 *
 * The constructor allocates no Filament objects. Native renderer creation starts only after
 * [load], which is only called from the user-opened Avatar player. Model resources are destroyed
 * on unload; ModelViewer owns and destroys the remaining engine when its TextureView detaches.
 */
class Core3FilamentHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Core3AvatarRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var renderView: TextureView? = null
    private var modelViewer: ModelViewer? = null
    private var loadJob: Job? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var rendererStarted = false
    private var modelParsed = false
    private var resourcesReady = false
    private var modelReleased = true
    private var destroyed = false
    private var viewportColor: Int = Color.rgb(214, 233, 240)
    private var fillLightEntity = 0
    private var clipIndexByName = emptyMap<String, Int>()
    private var neutralClipIndex = -1
    private var activePlayback: ActivePlayback? = null
    private var onLoadReady: ((Core3AvatarLoadMetrics) -> Unit)? = null
    private var onLoadFailure: ((Throwable) -> Unit)? = null
    private var loadStartedAtNanos = 0L
    private var firstRenderedAtNanos = 0L
    private var playbackSerial = 0L
    private val framePacingTracker = Core3FramePacingTracker()

    init {
        setBackgroundColor(viewportColor)
        contentDescription = "Verified 3D Avatar viewport"
    }

    fun setViewportColor(color: Int) {
        if (viewportColor == color) return
        viewportColor = color
        setBackgroundColor(color)
        modelViewer?.let(::configureClearColor)
    }

    override fun load(
        onReady: (Core3AvatarLoadMetrics) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        if (destroyed) {
            onFailure(IllegalStateException("Avatar view is no longer attached"))
            return
        }
        if (resourcesReady) {
            onReady(currentMetrics())
            return
        }
        onLoadReady = onReady
        onLoadFailure = onFailure
        if (loadJob?.isActive == true) return

        loadStartedAtNanos = SystemClock.elapsedRealtimeNanos()
        firstRenderedAtNanos = 0L
        modelParsed = false
        resourcesReady = false
        try {
            prepareRenderer()
            startRenderLoop()
            loadJob = scope.launch {
                try {
                    val verifiedAsset = withContext(Dispatchers.IO) { mapAndVerifyAsset() }
                    val viewer = requireNotNull(modelViewer) { "Filament renderer was released during load" }
                    viewer.loadModelGlb(verifiedAsset.buffer)
                    requireNotNull(viewer.asset) { "Filament could not create the CORE3 GLB asset" }
                    modelReleased = false
                    bindAndVerifyAnimations(viewer)
                    configurePresentationCamera(viewer)
                    logAssetBounds(viewer)
                    modelParsed = true
                    renderView?.visibility = View.VISIBLE
                    Log.i(
                        TAG,
                        "MODEL_PARSED bytes=${verifiedAsset.bytes} sha256=${verifiedAsset.sha256} " +
                            "mapMs=${verifiedAsset.mapTimeMs} animations=${clipIndexByName.keys.sorted()}"
                    )
                } catch (error: Throwable) {
                    fail("LOAD_FAILED", error)
                }
            }
        } catch (error: Throwable) {
            fail("RENDERER_INIT_FAILED", error)
        }
    }

    override fun play(
        clip: Core3AvatarClip,
        onComplete: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            check(resourcesReady) { "Avatar is not ready" }
            val index = requireNotNull(clipIndexByName[clip.runtimeClipName]) {
                "Verified clip is absent from GLB: ${clip.runtimeClipName}"
            }
            activePlayback = ActivePlayback(
                serial = ++playbackSerial,
                clip = clip,
                animationIndex = index,
                onComplete = onComplete,
                onFailure = onFailure,
                requestedAtNanos = System.nanoTime()
            )
            framePacingTracker.beginAction()
            Log.i(TAG, "PLAY_REQUEST label=${clip.canonicalLabel} clip=${clip.runtimeClipName}")
            startRenderLoop()
        } catch (error: Throwable) {
            onFailure(error)
        }
    }

    override fun resetNeutral() {
        check(resourcesReady) { "Avatar is not ready" }
        playbackSerial += 1
        activePlayback = null
        resetNeutralInternal()
        Log.i(TAG, "NEUTRAL_RESET")
    }

    override fun unload() {
        playbackSerial += 1
        activePlayback = null
        onLoadReady = null
        onLoadFailure = null
        loadJob?.cancel()
        loadJob = null
        logFrameMetrics("UNLOAD")
        stopRenderLoop()
        val surfaceAttached = renderView?.isAttachedToWindow == true
        modelViewer?.takeIf { !destroyed && surfaceAttached && !modelReleased }?.let { viewer ->
            try {
                viewer.destroyModel()
                modelReleased = true
            } catch (error: Throwable) {
                Log.w(TAG, "MODEL_UNLOAD_FAILED", error)
            }
        }
        modelParsed = false
        resourcesReady = false
        clipIndexByName = emptyMap()
        neutralClipIndex = -1
        renderView?.visibility = View.INVISIBLE
        Log.i(TAG, "UNLOADED")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (modelViewer != null && (modelParsed || loadJob?.isActive == true)) startRenderLoop()
    }

    override fun onDetachedFromWindow() {
        // ModelViewer's private TextureView listener owns native engine destruction on detach.
        // Never call back into ModelViewer here: child/parent detach ordering is not guaranteed,
        // and a second native destroy causes a SIGSEGV on the Samsung runtime.
        playbackSerial += 1
        activePlayback = null
        onLoadReady = null
        onLoadFailure = null
        loadJob?.cancel()
        loadJob = null
        stopRenderLoop()
        destroyed = true
        modelReleased = true
        fillLightEntity = 0
        modelViewer = null
        renderView = null
        scope.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && modelViewer != null && (modelParsed || loadJob?.isActive == true)) {
            startRenderLoop()
        } else if (visibility != View.VISIBLE) {
            stopRenderLoop()
        }
    }

    fun framePacingSnapshot(): Core3FramePacingSnapshot = framePacingTracker.snapshot()

    private fun prepareRenderer() {
        if (modelViewer != null) return
        Utils.init()
        // SurfaceView is composited below the Activity window on this Samsung and is therefore
        // hidden by Compose's separate Dialog window. TextureView keeps Filament in the dialog's
        // normal view hierarchy while using the same supported ModelViewer API.
        val texture = TextureView(context).apply {
            visibility = View.INVISIBLE
        }
        addView(
            texture,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // No orbit manipulator: ModelViewer's default manipulator targets (0, 0, -4) and would
        // overwrite this asset-native signing camera on every render frame.
        val viewer = ModelViewer(texture, manipulator = null).apply {
            cameraFocalLength = CAMERA_FOCAL_LENGTH_MM
            cameraNear = CAMERA_NEAR_METERS
            cameraFar = CAMERA_FAR_METERS
            camera.lookAt(
                0.0, DEFAULT_CAMERA_TARGET_Y, DEFAULT_CAMERA_DISTANCE,
                0.0, DEFAULT_CAMERA_TARGET_Y, 0.0,
                0.0, 1.0, 0.0
            )
            scene.skybox = null
        }
        configureClearColor(viewer)
        configureLighting(viewer)
        renderView = texture
        modelViewer = viewer
        Log.i(TAG, "RENDERER_CREATED renderer=Filament version=$FILAMENT_VERSION")
    }

    private fun configureClearColor(viewer: ModelViewer) {
        val red = Color.red(viewportColor) / 255f
        val green = Color.green(viewportColor) / 255f
        val blue = Color.blue(viewportColor) / 255f
        viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(red, green, blue, 1f)
        }
    }

    private fun configureLighting(viewer: ModelViewer) {
        val light = viewer.light
        val lightManager = viewer.engine.lightManager
        if (light != 0 && lightManager.hasComponent(light)) {
            val instance = lightManager.getInstance(light)
            lightManager.setColor(instance, 1.0f, 0.97f, 0.92f)
            lightManager.setDirection(instance, -0.30f, -0.30f, -0.90f)
            lightManager.setIntensity(instance, KEY_LIGHT_LUX)
            lightManager.setShadowCaster(instance, false)
        }
        if (fillLightEntity == 0) {
            val fill = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.78f, 0.90f, 1.0f)
                .direction(0.45f, -0.20f, -0.87f)
                .intensity(FILL_LIGHT_LUX)
                .castShadows(false)
                .build(viewer.engine, fill)
            viewer.scene.addEntity(fill)
            fillLightEntity = fill
        }
        Log.i(
            TAG,
            "PRESENTATION_LIGHTING keyLux=$KEY_LIGHT_LUX fillLux=$FILL_LIGHT_LUX " +
                "background=#${Integer.toHexString(viewportColor).uppercase(Locale.ROOT)}"
        )
    }

    private fun configurePresentationCamera(viewer: ModelViewer) {
        val bounds = requireNotNull(viewer.asset).boundingBox
        val center = bounds.center
        val halfExtent = bounds.halfExtent
        val fullHeight = (halfExtent[1] * 2f).coerceAtLeast(MIN_MODEL_HEIGHT_METERS)
        val fullWidth = (halfExtent[0] * 2f).coerceAtLeast(MIN_MODEL_WIDTH_METERS)
        val minY = center[1] - halfExtent[1]
        val maxY = center[1] + halfExtent[1]
        val waistY = minY + fullHeight * UPPER_BODY_CROP_FRACTION
        val headroom = fullHeight * HEADROOM_FRACTION
        val targetY = (waistY + maxY + headroom) * 0.5f
        val cameraDistance = max(
            fullHeight * CAMERA_DISTANCE_HEIGHT_MULTIPLIER,
            fullWidth * CAMERA_DISTANCE_WIDTH_MULTIPLIER
        )
        viewer.camera.lookAt(
            center[0].toDouble(),
            (targetY + fullHeight * CAMERA_EYE_LIFT_FRACTION).toDouble(),
            (center[2] + cameraDistance).toDouble(),
            center[0].toDouble(),
            targetY.toDouble(),
            center[2].toDouble(),
            0.0,
            1.0,
            0.0
        )
        Log.i(
            TAG,
            "PRESENTATION_CAMERA source=ASSET_BOUNDS waistY=$waistY targetY=$targetY " +
                "distance=$cameraDistance fullHeight=$fullHeight fullWidth=$fullWidth"
        )
    }

    private fun startRenderLoop() {
        if (rendererStarted || modelViewer == null || destroyed) return
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!rendererStarted) return
                choreographer.postFrameCallback(this)
                val viewer = modelViewer ?: return
                try {
                    applyPlaybackFrame(viewer, frameTimeNanos)
                    val rendered = viewer.render(frameTimeNanos)
                    if (resourcesReady) framePacingTracker.recordFrame(frameTimeNanos, rendered)
                    if (rendered && modelParsed && firstRenderedAtNanos == 0L) {
                        firstRenderedAtNanos = SystemClock.elapsedRealtimeNanos()
                        Log.i(TAG, "FIRST_FRAME ms=${currentMetrics().firstFrameTimeMs}")
                    }
                    if (
                        modelParsed && !resourcesReady && viewer.progress >= RESOURCE_READY_PROGRESS &&
                        firstRenderedAtNanos != 0L
                    ) {
                        resetNeutralInternal()
                        resourcesReady = true
                        framePacingTracker.reset(currentDisplayRefreshRate())
                        val metrics = currentMetrics()
                        Log.i(TAG, "READY loadMs=${metrics.loadTimeMs} firstFrameMs=${metrics.firstFrameTimeMs}")
                        onLoadReady?.invoke(metrics)
                        onLoadReady = null
                        onLoadFailure = null
                    }
                } catch (error: Throwable) {
                    fail("FRAME_FAILED", error)
                }
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
        framePacingTracker.pause()
    }

    private fun applyPlaybackFrame(viewer: ModelViewer, frameTimeNanos: Long) {
        val playback = activePlayback ?: return
        val animator = requireNotNull(viewer.animator) { "CORE3 animator unavailable during playback" }
        if (playback.startedAtNanos == 0L) {
            playback.startedAtNanos = frameTimeNanos
            // Choreographer's frame timestamp represents the start of this vsync and can precede
            // an input event handled later in the same frame. Wall-clock the first animator update
            // instead so request-to-update latency cannot collapse to a misleading zero.
            val switchLatencyNanos = (System.nanoTime() - playback.requestedAtNanos).coerceAtLeast(0L)
            framePacingTracker.recordActionSwitchLatency(switchLatencyNanos)
            Log.i(
                TAG,
                "ACTION_STARTED label=${playback.clip.canonicalLabel} serial=${playback.serial} " +
                    "switchMs=${switchLatencyNanos / NANOS_PER_MILLISECOND}"
            )
        }
        framePacingTracker.recordAnimationUpdate(frameTimeNanos)
        val elapsedSeconds = ((frameTimeNanos - playback.startedAtNanos) / NANOS_PER_SECOND)
            .toFloat()
            .coerceIn(0f, playback.clip.durationSeconds)
        animator.applyAnimation(playback.animationIndex, elapsedSeconds)
        animator.updateBoneMatrices()
        if (elapsedSeconds >= playback.clip.durationSeconds) {
            activePlayback = null
            Log.i(
                TAG,
                "PLAY_COMPLETE label=${playback.clip.canonicalLabel} serial=${playback.serial} " +
                    "duration=${playback.clip.durationSeconds}"
            )
            logFrameMetrics("PLAY_COMPLETE_${playback.clip.canonicalLabel}")
            playback.onComplete()
        }
    }

    private fun resetNeutralInternal() {
        val viewer = requireNotNull(modelViewer) { "Filament renderer unavailable" }
        val animator = requireNotNull(viewer.animator) { "CORE3 animator unavailable" }
        check(neutralClipIndex >= 0) { "CORE3 neutral reference clip unavailable" }
        animator.applyAnimation(neutralClipIndex, 0f)
        animator.updateBoneMatrices()
    }

    private fun bindAndVerifyAnimations(viewer: ModelViewer) {
        val animator = requireNotNull(viewer.animator) { "CORE3 GLB has no animator" }
        val indexes = buildMap {
            repeat(animator.animationCount) { index ->
                put(animator.getAnimationName(index), index)
            }
        }
        val catalog = Core3AvatarAssets.loadCatalog(context)
        require(indexes.keys.containsAll(catalog.clips.map { it.runtimeClipName })) {
            "CORE3 GLB is missing one or more verified named clips"
        }
        require(animator.animationCount == catalog.clips.size) {
            "CORE3 GLB contains an unexpected animation count: ${animator.animationCount}"
        }
        catalog.clips.forEach { clip ->
            val index = requireNotNull(indexes[clip.runtimeClipName])
            val runtimeDuration = animator.getAnimationDuration(index)
            require(abs(runtimeDuration - clip.durationSeconds) <= DURATION_TOLERANCE_SECONDS) {
                "${clip.runtimeClipName} duration differs from the verified manifest"
            }
        }
        clipIndexByName = indexes
        neutralClipIndex = requireNotNull(indexes["FSL_HELLO"])
    }

    private fun logAssetBounds(viewer: ModelViewer) {
        val bounds = viewer.asset?.boundingBox ?: return
        Log.i(
            TAG,
            "ASSET_BOUNDS center=${bounds.center.contentToString()} " +
                "halfExtent=${bounds.halfExtent.contentToString()} rootTransform=UNCHANGED"
        )
    }

    private fun mapAndVerifyAsset(): VerifiedAsset {
        val started = SystemClock.elapsedRealtimeNanos()
        val descriptor = context.assets.openFd(Core3AvatarAssets.MODEL_ASSET_PATH)
        descriptor.use { afd ->
            require(afd.length == Core3AvatarAssets.EXPECTED_MODEL_BYTES) {
                "CORE3 asset size mismatch: ${afd.length}"
            }
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(mapped.asReadOnlyBuffer())
                val sha256 = digest.digest().joinToString("") { byte ->
                    "%02X".format(Locale.ROOT, byte.toInt() and 0xFF)
                }
                require(sha256 == Core3AvatarAssets.EXPECTED_MODEL_SHA256) {
                    "CORE3 Android asset checksum mismatch"
                }
                return VerifiedAsset(
                    buffer = mapped,
                    bytes = afd.length,
                    sha256 = sha256,
                    mapTimeMs = nanosToMillis(SystemClock.elapsedRealtimeNanos() - started)
                )
            }
        }
    }

    private fun currentMetrics(): Core3AvatarLoadMetrics {
        val now = SystemClock.elapsedRealtimeNanos()
        val firstFrame = firstRenderedAtNanos.takeIf { it != 0L } ?: now
        return Core3AvatarLoadMetrics(
            loadTimeMs = nanosToMillis(now - loadStartedAtNanos),
            firstFrameTimeMs = nanosToMillis(firstFrame - loadStartedAtNanos)
        )
    }

    private fun currentDisplayRefreshRate(): Float =
        renderView?.display?.refreshRate?.takeIf { it.isFinite() && it >= 30f } ?: 60f

    private fun logFrameMetrics(reason: String) {
        val metrics = framePacingTracker.snapshot()
        if (metrics.measuredFrames > 0L) {
            Log.i(TAG, "FRAME_METRICS reason=$reason ${metrics.toLogString()}")
        }
    }

    private fun fail(stage: String, error: Throwable) {
        Log.e(TAG, "$stage: ${error.message ?: error.javaClass.simpleName}", error)
        playbackSerial += 1
        activePlayback?.onFailure?.invoke(error)
        activePlayback = null
        val loadFailure = onLoadFailure
        onLoadReady = null
        onLoadFailure = null
        loadJob?.cancel()
        loadJob = null
        logFrameMetrics("FAILURE_$stage")
        stopRenderLoop()
        if (!modelReleased && renderView?.isAttachedToWindow == true) {
            try {
                modelViewer?.destroyModel()
                modelReleased = true
            } catch (cleanupError: Throwable) {
                Log.w(TAG, "FAILURE_CLEANUP_FAILED", cleanupError)
            }
        }
        modelParsed = false
        resourcesReady = false
        clipIndexByName = emptyMap()
        neutralClipIndex = -1
        loadFailure?.invoke(error)
    }

    private fun nanosToMillis(nanos: Long): Long = nanos.coerceAtLeast(0L) / 1_000_000L

    private data class VerifiedAsset(
        val buffer: ByteBuffer,
        val bytes: Long,
        val sha256: String,
        val mapTimeMs: Long
    )

    private data class ActivePlayback(
        val serial: Long,
        val clip: Core3AvatarClip,
        val animationIndex: Int,
        val onComplete: () -> Unit,
        val onFailure: (Throwable) -> Unit,
        val requestedAtNanos: Long,
        var startedAtNanos: Long = 0L
    )

    companion object {
        private const val TAG = "VoxGestCore3"
        private const val FILAMENT_VERSION = "1.71.4"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NANOS_PER_MILLISECOND = 1_000_000.0
        private const val RESOURCE_READY_PROGRESS = 0.999f
        private const val DURATION_TOLERANCE_SECONDS = 1f / 60f
        private const val CAMERA_FOCAL_LENGTH_MM = 42f
        private const val CAMERA_NEAR_METERS = 0.05f
        private const val CAMERA_FAR_METERS = 20f
        private const val DEFAULT_CAMERA_TARGET_Y = 1.10
        private const val DEFAULT_CAMERA_DISTANCE = 2.35
        private const val MIN_MODEL_HEIGHT_METERS = 0.5f
        private const val MIN_MODEL_WIDTH_METERS = 0.25f
        private const val UPPER_BODY_CROP_FRACTION = 0.43f
        private const val HEADROOM_FRACTION = 0.035f
        private const val CAMERA_EYE_LIFT_FRACTION = 0.015f
        private const val CAMERA_DISTANCE_HEIGHT_MULTIPLIER = 1.07f
        private const val CAMERA_DISTANCE_WIDTH_MULTIPLIER = 1.85f
        private const val KEY_LIGHT_LUX = 92_000f
        private const val FILL_LIGHT_LUX = 48_000f
    }
}
