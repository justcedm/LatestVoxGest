package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

data class Mapua14RescueProfile(
    val sequenceLength: Int,
    val labels: List<String>,
    val modelAsset: String,
    val labelsAsset: String,
    val manifestAsset: String
) {
    val inputShape = intArrayOf(1, sequenceLength, 225)
    val outputShape = intArrayOf(1, labels.size)

    companion object {
        const val ID = "MAPUA14_RESCUE_V1"
        const val ASSET_ROOT = "model/mapua14_rescue_v1"
        const val MANIFEST_ASSET = "$ASSET_ROOT/runtime_manifest.json"
        val EXPECTED_LABELS = listOf(
            "EIGHT", "FIVE", "FOUR", "HELLO", "NINE", "NO", "ONE",
            "SEVEN", "SIX", "TEN", "THANK_YOU", "THREE", "TWO", "YES"
        )

        fun load(context: Context): Mapua14RescueProfile {
            val manifestBytes = context.assets.open(MANIFEST_ASSET).use { it.readBytes() }
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            require(manifest.getString("profile_id") == ID)
            require(manifest.getString("status") == "experimental_developer_diagnostic_only")
            require(!manifest.getBoolean("android_default_changed"))
            require(manifest.getString("feature_version") == "fullsign225_frame_v1_complete_trajectory_v1")
            require(manifest.getString("coordinate_orientation") == "canonical_unmirrored")
            require(!manifest.getBoolean("anatomical_slot_swap"))
            val input = manifest.getJSONArray("input_shape")
            val output = manifest.getJSONArray("output_shape")
            require(input.length() == 3 && input.getInt(0) == 1 && input.getInt(2) == 225)
            require(input.getInt(1) == 32 || input.getInt(1) == 48)
            require(output.length() == 2 && output.getInt(0) == 1 && output.getInt(1) == 14)
            val modelFile = manifest.getString("model_file")
            val labelsFile = manifest.getString("labels_file")
            val modelAsset = "$ASSET_ROOT/$modelFile"
            val labelsAsset = "$ASSET_ROOT/$labelsFile"
            val modelBytes = context.assets.open(modelAsset).use { it.readBytes() }
            val labelsBytes = context.assets.open(labelsAsset).use { it.readBytes() }
            require(modelBytes.sha256() == manifest.getString("model_sha256")) { "model SHA-256 mismatch" }
            require(labelsBytes.sha256() == manifest.getString("labels_sha256")) { "label SHA-256 mismatch" }
            val labelsJson = JSONObject(labelsBytes.toString(Charsets.UTF_8))
            require(labelsJson.getString("profile_id") == ID)
            require(labelsJson.getInt("class_count") == 14)
            val classes = labelsJson.getJSONArray("classes")
            val labels = List(classes.length()) { index ->
                val entry = classes.getJSONObject(index)
                require(entry.getInt("index") == index)
                entry.getString("id")
            }
            require(labels == EXPECTED_LABELS) { "unexpected Mapua-14 label order" }
            return Mapua14RescueProfile(input.getInt(1), labels, modelAsset, labelsAsset, MANIFEST_ASSET)
        }

        private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
            .digest(this).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class Mapua14RescueTfliteRuntime(
    context: Context,
    val profile: Mapua14RescueProfile = Mapua14RescueProfile.load(context.applicationContext)
) : Closeable {
    private val appContext = context.applicationContext
    private val interpreter = TfliteModelLoader(appContext).loadInterpreterWithOptions(
        Interpreter.Options().setNumThreads(2), profile.modelAsset
    )

    init {
        interpreter.allocateTensors()
        require(interpreter.getInputTensor(0).shape().contentEquals(profile.inputShape))
        require(interpreter.getOutputTensor(0).shape().contentEquals(profile.outputShape))
        require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
        require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
    }

    fun infer(window: Array<FloatArray>): StandardFslInference {
        require(window.size == profile.sequenceLength)
        require(window.all { it.size == 225 && it.all(Float::isFinite) })
        val output = Array(1) { FloatArray(14) }
        val started = SystemClock.elapsedRealtimeNanos()
        interpreter.run(arrayOf(window), output)
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        val probabilities = output[0]
        require(probabilities.all(Float::isFinite))
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        return StandardFslInference(
            probabilities.copyOf(),
            StandardFslRankedPrediction(ranked[0], profile.labels[ranked[0]], probabilities[ranked[0]]),
            StandardFslRankedPrediction(ranked[1], profile.labels[ranked[1]], probabilities[ranked[1]]),
            ranked.take(5).map { StandardFslRankedPrediction(it, profile.labels[it], probabilities[it]) },
            latencyMs
        )
    }

    fun goldenParity(): StandardFslParityCheck {
        return try {
            val windowBytes = appContext.assets.open("${Mapua14RescueProfile.ASSET_ROOT}/golden_fullsign225_window_f32.bin").use { it.readBytes() }
            require(windowBytes.size == profile.sequenceLength * 225 * 4)
            val floats = FloatArray(windowBytes.size / 4)
            val buffer = ByteBuffer.wrap(windowBytes).order(ByteOrder.LITTLE_ENDIAN)
            floats.indices.forEach { floats[it] = buffer.float }
            val window = Array(profile.sequenceLength) { frame ->
                floats.copyOfRange(frame * 225, (frame + 1) * 225)
            }
            val expected = JSONObject(
                appContext.assets.open("${Mapua14RescueProfile.ASSET_ROOT}/golden_fullsign225_window_expected.json")
                    .bufferedReader().use { it.readText() }
            )
            val expectedProbabilitiesJson = expected.getJSONArray("expected_probabilities")
            val expectedProbabilities = FloatArray(14) { expectedProbabilitiesJson.getDouble(it).toFloat() }
            val actual = infer(window)
            val maxDifference = actual.probabilities.indices.maxOf { abs(actual.probabilities[it] - expectedProbabilities[it]) }
            val passed = actual.top1.index == expected.getInt("expected_index") && maxDifference <= 1e-5f
            StandardFslParityCheck(
                if (passed) StandardFslParityStatus.PASS else StandardFslParityStatus.FAIL,
                "MAPUA14_TFLITE_PARITY",
                "top1=${actual.top1.index}:${actual.top1.label} expected=${expected.getInt("expected_index")}:${expected.getString("expected_label")} max_probability_difference=$maxDifference threshold=1.0E-5"
            )
        } catch (error: FileNotFoundException) {
            StandardFslParityCheck(StandardFslParityStatus.BLOCKED, "MAPUA14_TFLITE_PARITY", "missing golden fixture: ${error.message}")
        } catch (error: Throwable) {
            StandardFslParityCheck(StandardFslParityStatus.FAIL, "MAPUA14_TFLITE_PARITY", "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    override fun close() = interpreter.close()
}

class Mapua14RescueWindow(private val capacity: Int) {
    private val frames = ArrayDeque<StandardFullSign225Frame>(capacity)
    val isReady: Boolean get() = frames.size == capacity

    fun clear() = frames.clear()

    fun add(frame: LandmarkFrame) {
        val feature = StandardFullSign225FeatureBuilder.build(frame, inputMirrored = false)
        if (frames.size == capacity) frames.removeFirst()
        frames.addLast(feature.copy(vector = feature.vector.copyOf()))
    }

    fun snapshot(): Array<FloatArray>? = if (!isReady) null else frames.map { it.vector.copyOf() }.toTypedArray()

    fun quality(): StandardFullSign225WindowQuality = StandardFullSign225WindowQuality(
        frames.size,
        frames.count { it.quality.posePresent },
        frames.count { it.quality.leftHandPresent },
        frames.count { it.quality.rightHandPresent },
        frames.count { it.quality.anyHandPresent },
        frames.count { it.quality.bothHandsPresent }
    )

    fun timing(nowMs: Long): StandardFullSign225WindowTiming {
        val timestamps = frames.map { it.timestampMs }
        if (timestamps.size < 2 || timestamps.any { it <= 0L }) return StandardFullSign225WindowTiming(false, false, 0, 0, 0, 0)
        val gaps = timestamps.zipWithNext { before, after -> after - before }
        val sorted = gaps.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
        return StandardFullSign225WindowTiming(
            true, gaps.all { it > 0 }, (timestamps.last() - timestamps.first()).coerceAtLeast(0),
            (nowMs - timestamps.first()).coerceAtLeast(0), median.coerceAtLeast(0), (gaps.maxOrNull() ?: 0).coerceAtLeast(0)
        )
    }
}

class Mapua14RescueGate(private val sequenceLength: Int) {
    private var candidate = ""
    private var stableWindows = 0
    private var lastAccepted = ""

    fun reset() {
        candidate = ""
        stableWindows = 0
        lastAccepted = ""
    }

    fun cancelCandidate() {
        candidate = ""
        stableWindows = 0
    }

    fun evaluate(inference: StandardFslInference, quality: StandardFullSign225WindowQuality, timing: StandardFullSign225WindowTiming, currentFrameUsable: Boolean): StandardFslGateDecision {
        fun reject(reason: String, reset: Boolean = true): StandardFslGateDecision {
            if (reset) cancelCandidate()
            return StandardFslGateDecision(false, reason, StandardFslRejectionGate.HOLD_SIGN_CLEARLY, stableWindows, false)
        }
        if (quality.frameCount != sequenceLength) return reject("BUFFER_NOT_READY")
        if (quality.posePresenceRatio < 0.65f) return reject("LOW_POSE_PRESENCE")
        if (quality.anyHandPresenceRatio < 0.65f) return reject("LOW_HAND_PRESENCE")
        if (!currentFrameUsable) return reject("CURRENT_FRAME_MISSING")
        if (!timing.available || !timing.chronological) return reject("WINDOW_TIMING_INVALID")
        val maxDuration = if (sequenceLength == 48) 8_000L else 6_000L
        if (timing.windowDurationMs > maxDuration || timing.oldestFrameAgeMs > maxDuration + 500L) return reject("STALE_WINDOW")
        if (timing.medianFrameGapMs > 350L || timing.maxFrameGapMs > 700L) return reject("LANDMARK_GAP_EXCEEDED")
        if (inference.top1.probability < 0.70f) return reject("LOW_CONFIDENCE")
        if (inference.margin < 0.20f) return reject("LOW_MARGIN")
        if (inference.top1.label == lastAccepted) return reject("DUPLICATE_REQUIRES_RELEASE")
        if (candidate == inference.top1.label) stableWindows++ else {
            candidate = inference.top1.label
            stableWindows = 1
        }
        if (stableWindows < 2) return reject("TEMPORAL_STABILITY", reset = false)
        val acceptedCount = stableWindows
        lastAccepted = inference.top1.label
        cancelCandidate()
        return StandardFslGateDecision(true, "ACCEPTED", inference.top1.label, acceptedCount, false)
    }
}

/** Debug builds require both diagnostics=true and an exact profile extra. */
object DeveloperRecognitionOverride {
    @Volatile private var requested = CameraRecognitionRuntime.STANDARD_FSL105

    fun configure(debugBuild: Boolean, diagnosticsEnabled: Boolean, profileId: String?) {
        requested = if (debugBuild && diagnosticsEnabled && profileId == Mapua14RescueProfile.ID) {
            CameraRecognitionRuntime.MAPUA14_RESCUE_V1
        } else {
            CameraRecognitionRuntime.STANDARD_FSL105
        }
    }

    fun runtime(): CameraRecognitionRuntime = requested
}
