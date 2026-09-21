package com.voxgest.dryrun

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** DEBUG laboratory adapter. Not registered in any profile or stable camera controller. */
class ExperimentalLiveStreamLandmarks(
    context: Context,
    private val onFrame: (LandmarkFrame) -> Unit,
    private val onMetrics: (Metrics) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    data class Metrics(val timestamp: Long, val handMs: Long, val poseMs: Long,
        val pairMs: Long, val replacedFrames: Int)
    private data class Input(val timestamp: Long, val bitmap: Bitmap)
    private data class Active(val input: Input, val image: MPImage, val started: Long,
        var handMs: Long = 0, var poseMs: Long = 0)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val pairer = ExactTimestampPairer<HandLandmarkerResult, PoseLandmarkerResult>(1, 1500)
    private val lock = Any()
    private var latest: Input? = null
    private var closed = false
    private var pumpQueued = false
    private var lastTimestamp = Long.MIN_VALUE
    private var replaced = 0
    private var active: Active? = null // worker-confined
    private var hand: HandLandmarker? = null
    private var pose: PoseLandmarker? = null

    init {
        check(BuildConfig.DEBUG) { "LIVE_STREAM experiment is debug-only" }
        val app = context.applicationContext
        worker.execute {
            try {
                hand = HandLandmarker.createFromOptions(app, HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("model/hand_landmarker.task").build())
                    .setRunningMode(RunningMode.LIVE_STREAM).setNumHands(2)
                    .setMinHandDetectionConfidence(.45f).setMinHandPresenceConfidence(.45f).setMinTrackingConfidence(.45f)
                    .setResultListener { result, _ -> dispatch {
                        pairer.hand(result.timestampMs(), result)
                        active?.let { it.handMs = SystemClock.elapsedRealtime() - it.started }
                        complete()
                    } }
                    .setErrorListener { error -> dispatch { fail("HAND_${error.javaClass.simpleName}") } }.build())
                pose = PoseLandmarker.createFromOptions(app, PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("model/pose_landmarker_lite.task").build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinPoseDetectionConfidence(.45f).setMinPosePresenceConfidence(.45f).setMinTrackingConfidence(.45f)
                    .setResultListener { result, _ -> dispatch {
                        pairer.pose(result.timestampMs(), result)
                        active?.let { it.poseMs = SystemClock.elapsedRealtime() - it.started }
                        complete()
                    } }
                    .setErrorListener { error -> dispatch { fail("POSE_${error.javaClass.simpleName}") } }.build())
            } catch (error: Throwable) { fail("INIT_${error.javaClass.simpleName}") }
        }
        worker.scheduleWithFixedDelay({
            if (pairer.expire(SystemClock.elapsedRealtime()).isNotEmpty()) {
                // A dropped detector callback gives no lifetime guarantee: close both graphs first.
                fail("UNMATCHED_FRAME_TIMEOUT_RESTART_REQUIRED")
            }
        }, 100, 100, TimeUnit.MILLISECONDS)
    }

    /** Caller owns/closes ImageProxy after return. Only an owned upright bitmap crosses threads. */
    fun submit(image: ImageProxy): Boolean = synchronized(lock) {
        if (closed) return false
        val timestamp = image.imageInfo.timestamp / 1_000_000L
        if (timestamp <= lastTimestamp) { replaced++; return false }
        lastTimestamp = timestamp
        val bitmap = ImageProxyBitmapConverter.toUprightBitmap(image, false)
        latest?.let { it.bitmap.recycle(); replaced++ } // Never submitted to Tasks.
        latest = Input(timestamp, bitmap)
        if (!pumpQueued) { pumpQueued = true; dispatch { pump() } }
        true
    }

    private fun pump() {
        val input = synchronized(lock) {
            pumpQueued = false
            if (closed || active != null) return
            latest.also { latest = null }
        } ?: return
        val now = SystemClock.elapsedRealtime()
        if (!pairer.submit(input.timestamp, now)) { input.bitmap.recycle(); return }
        val mpImage = BitmapImageBuilder(input.bitmap).build()
        active = Active(input, mpImage, now)
        try {
            checkNotNull(hand).detectAsync(mpImage, input.timestamp)
            checkNotNull(pose).detectAsync(mpImage, input.timestamp)
        } catch (error: Throwable) { fail("SUBMIT_${error.javaClass.simpleName}") }
    }

    private fun complete() {
        val paired = pairer.drain().singleOrNull() ?: return
        val owned = active ?: return
        check(paired.timestamp == owned.input.timestamp)
        fun points(values: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) =
            values.map { LandmarkPoint(it.x(), it.y(), it.z()) }
        var left: List<LandmarkPoint>? = null
        var right: List<LandmarkPoint>? = null
        val observations = mutableListOf<HandObservation>()
        val policy = ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
        paired.hand.landmarks().forEachIndexed { index, landmarks ->
            val category = paired.hand.handedness().getOrNull(index)?.firstOrNull()
            val label = category?.categoryName().orEmpty()
            val side = AnatomicalHandedness.resolve(label, policy)
            when (side) {
                AnatomicalHandSide.LEFT -> left = points(landmarks)
                AnatomicalHandSide.RIGHT -> right = points(landmarks)
                null -> Unit // Match the stable unmirrored policy: never infer anatomy from image X.
            }
            observations += HandObservation(side?.name?.lowercase() ?: "unassigned", label.lowercase(),
                landmarks.map { it.x() }.average().toFloat(), "policy=$policy/analysis_mirrored=false", category?.score())
        }
        val frame = LandmarkFrame(paired.pose.landmarks().firstOrNull()?.let(::points), left, right,
            paired.timestamp, observations, owned.input.bitmap.width, owned.input.bitmap.height)
        try {
            onFrame(frame)
            onMetrics(Metrics(paired.timestamp, owned.handMs, owned.poseMs,
                SystemClock.elapsedRealtime() - owned.started, synchronized(lock) { replaced }))
        } finally {
            // Both Tasks returned results. Release MPImage's ref; do not manually recycle submitted pixels.
            owned.image.close()
            active = null
            pump()
        }
    }

    private fun dispatch(action: () -> Unit) {
        try { worker.execute { if (!synchronized(lock) { closed }) action() } }
        catch (_: java.util.concurrent.RejectedExecutionException) { /* closed lifecycle */ }
    }
    private fun fail(reason: String) {
        closeOnWorker()
        onError(reason)
    }
    private fun closeOnWorker() {
        synchronized(lock) { closed = true; latest?.bitmap?.recycle(); latest = null }
        // Never close Tasks from their callback threads; serialize shutdown on the owner worker.
        runCatching { hand?.close() }; runCatching { pose?.close() }
        hand = null; pose = null
        active?.image?.close(); active = null
        pairer.reset()
        worker.shutdown()
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            worker.execute { closeOnWorker() }
        }
    }
}
