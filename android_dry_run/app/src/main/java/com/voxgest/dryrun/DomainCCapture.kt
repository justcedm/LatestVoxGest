package com.voxgest.dryrun

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in diagnostic side channel. No pixels; never supplies classifier/gate inputs. */
class DomainCCapture private constructor(private val directory: File) : AutoCloseable {
    private val frames = ArrayDeque<LandmarkFrame>()
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private var count = 0
    private val session = java.util.UUID.randomUUID().toString()

    fun frame(frame: LandmarkFrame) {
        if (frames.size == 200) frames.removeFirst()
        frames.addLast(frame)
    }

    fun update(update: FslPractical15CaptureUpdate) {
        if (update.reason == "SIGN_ENTRY") {
            val last = frames.lastOrNull(); frames.clear(); if (last != null) frames.add(last)
        }
        val candidate = update.candidate
        val aborted = update.reason in setOf("EVENT_TIMEOUT", "POSE_TRACKING_LOST", "INCOMPLETE_EVENT_REJECTED")
        if (candidate == null && !aborted) return
        val snapshot = frames.toList()
        frames.clear()
        if (count >= 100) { Log.w(TAG, "CAPTURE_LIMIT_REACHED"); return }
        val number = ++count
        // Capture failures never interrupt inference. Bounded queue avoids memory growth on slow storage.
        try {
            writer.execute {
                try {
                    directory.mkdirs()
                    val totalBytes = directory.listFiles()?.sumOf { it.length() } ?: 0L
                    check(totalBytes < 100L * 1024 * 1024) { "Capture storage limit" }
                    val payload = encode(snapshot, update.reason, candidate)
                    File(directory, "$session-$number.json").writeText(payload.toString())
                    Log.i(TAG, "DOMAIN_C_CAPTURE_WRITTEN event=$number frames=${snapshot.size} pixels=false")
                } catch (error: Exception) {
                    Log.w(TAG, "DOMAIN_C_CAPTURE_FAILED type=${error.javaClass.simpleName}")
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            Log.w(TAG, "DOMAIN_C_CAPTURE_DROPPED writer_backpressure=true")
        }
    }

    override fun close() { frames.clear(); writer.shutdown() }

    companion object {
        private const val TAG = "VoxGestDomainC"
        fun openIfEnabled(context: Context): DomainCCapture? =
            if (BuildConfig.DEBUG && File(context.filesDir, "domain_c_capture.enabled").isFile)
                DomainCCapture(File(context.filesDir, "domain_c_captures")) else null

        fun tensorHash(window: Array<FloatArray>): String {
            require(window.size == 48 && window.all { it.size == 225 && it.all(Float::isFinite) })
            val bytes = ByteBuffer.allocate(48 * 225 * 4).order(ByteOrder.LITTLE_ENDIAN)
            window.forEach { row -> row.forEach { bytes.putFloat(it) } }
            return MessageDigest.getInstance("SHA-256").digest(bytes.array())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }

        fun encode(frames: List<LandmarkFrame>, completion: String, candidate: FslPractical15Candidate?): JSONObject {
            fun points(values: List<LandmarkPoint>?, count: Int) = JSONArray().apply {
                repeat(count) { index ->
                    val p = values?.getOrNull(index)
                    put(JSONArray(listOf(p?.x ?: 0f, p?.y ?: 0f, p?.z ?: 0f)))
                }
            }
            val q = candidate?.quality
            return JSONObject().put("schema", "voxgest_domain_c_tasks_raw_v1")
                .put("profile", FslPractical15Profile.ID).put("pixels_stored", false)
                .put("completion", completion).put("frame_count", frames.size)
                .put("raw_classifier_frame_count", q?.rawFrameCount ?: JSONObject.NULL)
                .put("event_duration_ms", q?.let { it.endTimestampMs - it.startTimestampMs } ?: JSONObject.NULL)
                .put("canonical_sha256_le_f32", candidate?.let { tensorHash(it.resampledWindow) } ?: JSONObject.NULL)
                .put("frames", JSONArray().apply {
                    frames.forEach { f ->
                        val m = f.cameraMetadata
                        put(JSONObject().put("timestamp_ms", f.timestampMs)
                            .put("included_in_canonical", q != null && f.hasPose && f.timestampMs in q.startTimestampMs..q.endTimestampMs)
                            .put("pose", points(f.poseLandmarks, 33))
                            .put("left", points(f.leftHandLandmarks, 21)).put("right", points(f.rightHandLandmarks, 21))
                            .put("pose_present", f.hasPose).put("left_present", f.hasLeftHand).put("right_present", f.hasRightHand)
                            .put("pose_detection_confidence", JSONObject.NULL)
                            .put("hand_detection_confidence", JSONObject.NULL)
                            .put("confidence_note", "Tasks exposes handedness category scores, not detector confidence in these results")
                            .put("handedness", JSONArray().apply {
                                f.handObservations.forEach { o -> put(JSONObject().put("slot", o.slot)
                                    .put("reported", o.mediaPipeHandedness).put("score", o.handednessScore ?: JSONObject.NULL)
                                    .put("policy", o.physicalSideEstimate)) }
                            })
                            .put("source_timestamp_ns", m?.sourceTimestampNanos ?: JSONObject.NULL)
                            .put("rotation_degrees", m?.rotationDegrees ?: JSONObject.NULL)
                            .put("lens", m?.lens ?: "UNKNOWN")
                            .put("source_width", f.sourceWidth).put("source_height", f.sourceHeight)
                            .put("preview_mirrored", m?.previewMirrored ?: JSONObject.NULL)
                            .put("analysis_mirrored", m?.analysisMirrored ?: JSONObject.NULL))
                    }
                })
        }
    }
}
