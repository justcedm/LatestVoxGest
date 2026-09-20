package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

data class FslPractical15Profile(val labels: List<String>, val modelAsset: String, val labelsAsset: String) {
    val inputShape = intArrayOf(1, SEQUENCE_LENGTH, 225)
    val outputShape = intArrayOf(1, labels.size)

    companion object {
        const val ID = "FSL_PRACTICAL15_V1"
        const val ASSET_ROOT = "model/fsl_practical15_fullsign225_48f_v1"
        const val MANIFEST_ASSET = "$ASSET_ROOT/runtime_manifest.json"
        const val SEQUENCE_LENGTH = 48
        val EXPECTED_LABELS = listOf(
            "HELLO", "THANK_YOU", "YES", "NO", "PLEASE", "HOW_MUCH", "CASH", "CARD",
            "RECEIPT", "WAIT", "HOW_MANY", "AGAIN", "PROBLEM", "COIN", "DISCOUNT"
        )

        fun load(context: Context): FslPractical15Profile {
            val manifestBytes = context.assets.open(MANIFEST_ASSET).use { it.readBytes() }
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            require(manifest.getString("profile_id") == ID)
            require(manifest.getString("status") == "experimental_non_default_requires_samsung_qualification")
            require(!manifest.getBoolean("android_default_changed"))
            require(manifest.getString("feature_version") == "fullsign225_frame_v1_complete_trajectory_v1")
            require(manifest.getString("temporal_contract") == "complete_event_resample48")
            require(manifest.getString("coordinate_orientation") == "canonical_unmirrored")
            require(!manifest.getBoolean("anatomical_slot_swap"))
            val gate = manifest.getJSONObject("development_gate_preparation")
            require(gate.getDouble("minimum_confidence") == 0.95)
            require(gate.getDouble("minimum_margin") == 0.05)
            require(gate.getDouble("minimum_trajectory_motion_mean_l2") == 0.02)
            require(!gate.getBoolean("live_approved"))
            val input = manifest.getJSONArray("input_shape")
            val output = manifest.getJSONArray("output_shape")
            require(input.length() == 3 && input.getInt(0) == 1 && input.getInt(1) == 48 && input.getInt(2) == 225)
            require(output.length() == 2 && output.getInt(0) == 1 && output.getInt(1) == 15)
            val modelAsset = "$ASSET_ROOT/" + manifest.getString("model_file")
            val labelsAsset = "$ASSET_ROOT/" + manifest.getString("labels_file")
            val modelBytes = context.assets.open(modelAsset).use { it.readBytes() }
            val labelsBytes = context.assets.open(labelsAsset).use { it.readBytes() }
            require(modelBytes.sha256() == manifest.getString("model_sha256"))
            require(labelsBytes.sha256() == manifest.getString("labels_sha256"))
            val labelsJson = JSONObject(labelsBytes.toString(Charsets.UTF_8))
            require(labelsJson.getString("profile_id") == ID)
            require(labelsJson.getInt("class_count") == 15)
            require(labelsJson.getInt("temporal_length") == 48)
            val classes = labelsJson.getJSONArray("classes")
            val labels = List(classes.length()) { index ->
                val entry = classes.getJSONObject(index)
                require(entry.getInt("index") == index)
                entry.getString("id")
            }
            require(labels == EXPECTED_LABELS)
            return FslPractical15Profile(labels, modelAsset, labelsAsset)
        }

        private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
            .digest(this).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class FslPractical15TfliteRuntime(
    context: Context,
    val profile: FslPractical15Profile = FslPractical15Profile.load(context.applicationContext)
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
        require(window.size == 48 && window.all { it.size == 225 && it.all(Float::isFinite) })
        val output = Array(1) { FloatArray(15) }
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

    fun goldenParity(): StandardFslParityCheck = try {
        val bytes = appContext.assets.open("${FslPractical15Profile.ASSET_ROOT}/golden_fullsign225_window_f32.bin").use { it.readBytes() }
        require(bytes.size == 48 * 225 * 4)
        val source = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val window = Array(48) { FloatArray(225) { source.float } }
        val expected = JSONObject(
            appContext.assets.open("${FslPractical15Profile.ASSET_ROOT}/golden_fullsign225_window_expected.json")
                .bufferedReader().use { it.readText() }
        )
        val expectedJson = expected.getJSONArray("expected_probabilities")
        val expectedProbabilities = FloatArray(15) { expectedJson.getDouble(it).toFloat() }
        val actual = infer(window)
        val maxDifference = actual.probabilities.indices.maxOf { abs(actual.probabilities[it] - expectedProbabilities[it]) }
        val passed = actual.top1.index == expected.getInt("expected_index") && maxDifference <= 1e-5f
        StandardFslParityCheck(
            if (passed) StandardFslParityStatus.PASS else StandardFslParityStatus.FAIL,
            "FSL_PRACTICAL15_TFLITE_PARITY",
            "top1=${actual.top1.label} expected=${expected.getString("expected_label")} max_probability_difference=$maxDifference"
        )
    } catch (error: Throwable) {
        StandardFslParityCheck(
            StandardFslParityStatus.FAIL,
            "FSL_PRACTICAL15_TFLITE_PARITY",
            "${error.javaClass.simpleName}: ${error.message}"
        )
    }

    override fun close() = interpreter.close()
}

object CompleteEventResampler48 {
    fun resample(source: List<FloatArray>): Array<FloatArray> {
        require(source.isNotEmpty())
        require(source.all { it.size == 225 && it.all(Float::isFinite) })
        if (source.size == 1) return Array(48) { source[0].copyOf() }
        if (source.size == 48) return Array(48) { source[it].copyOf() }
        return Array(48) { targetIndex ->
            val position = targetIndex.toDouble() * source.lastIndex / 47.0
            val lower = floor(position).toInt()
            val upper = ceil(position).toInt().coerceAtMost(source.lastIndex)
            val alpha = (position - lower).toFloat()
            FloatArray(225) { feature ->
                source[lower][feature] + (source[upper][feature] - source[lower][feature]) * alpha
            }
        }
    }
}

enum class FslPractical15CaptureState { IDLE, PRIMING, SIGN_ACTIVE, CANDIDATE, WAIT_FOR_RELEASE }

data class FslPractical15CaptureConfig(
    val neutralArmFrames: Int = 3,
    val releaseFrames: Int = 3,
    val minimumEventFrames: Int = 8,
    val maximumEventFrames: Int = 180,
    val maximumEventDurationMs: Long = 8_000L,
    val maximumConsecutiveMissingPose: Int = 3
)

data class FslPractical15EventQuality(
    val rawFrameCount: Int,
    val posePresentFrames: Int,
    val leftHandPresentFrames: Int,
    val rightHandPresentFrames: Int,
    val anyHandPresentFrames: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val trajectoryMotionMeanL2: Float
) {
    val posePresenceRatio get() = posePresentFrames.toFloat() / rawFrameCount.coerceAtLeast(1)
    val anyHandPresenceRatio get() = anyHandPresentFrames.toFloat() / rawFrameCount.coerceAtLeast(1)
}

data class FslPractical15Candidate(
    val eventNumber: Int,
    val resampledWindow: Array<FloatArray>,
    val quality: FslPractical15EventQuality
)

data class FslPractical15CaptureUpdate(
    val state: FslPractical15CaptureState,
    val reason: String,
    val candidate: FslPractical15Candidate? = null
)

/** One neutral-to-neutral motion event produces at most one 48-frame candidate. */
class FslPractical15CompleteEventCollector(
    private val config: FslPractical15CaptureConfig = FslPractical15CaptureConfig()
) {
    private val frames = mutableListOf<StandardFullSign225Frame>()
    private val pendingDropout = mutableListOf<StandardFullSign225Frame>()
    private var neutralCount = 0
    private var missingPoseCount = 0
    private var eventCounter = 0
    private var eventStartedAtMs = 0L
    var state = FslPractical15CaptureState.IDLE
        private set

    fun onFrame(frame: LandmarkFrame): FslPractical15CaptureUpdate {
        val neutral = frame.hasPose && !frame.hasAnyHand
        return when (state) {
            FslPractical15CaptureState.IDLE -> {
                neutralCount = if (neutral) neutralCount + 1 else 0
                if (neutralCount >= config.neutralArmFrames) {
                    state = FslPractical15CaptureState.PRIMING
                    update("NEUTRAL_ARMED")
                } else update("WAITING_FOR_NEUTRAL")
            }
            FslPractical15CaptureState.PRIMING -> {
                if (frame.hasPose && frame.hasAnyHand) {
                    clearEvent()
                    eventStartedAtMs = frame.timestampMs
                    append(frame)
                    state = FslPractical15CaptureState.SIGN_ACTIVE
                    update("SIGN_ENTRY")
                } else update(if (neutral) "ARMED_WAITING_FOR_ENTRY" else "PRIMING_TRACKING_MISSING")
            }
            FslPractical15CaptureState.SIGN_ACTIVE -> collect(frame)
            FslPractical15CaptureState.CANDIDATE -> update("CANDIDATE_AWAITING_INFERENCE")
            FslPractical15CaptureState.WAIT_FOR_RELEASE -> {
                neutralCount = if (neutral) neutralCount + 1 else 0
                if (neutralCount >= config.releaseFrames) {
                    state = FslPractical15CaptureState.IDLE
                    neutralCount = 0
                    update("RELEASE_CONFIRMED")
                } else update("WAITING_FOR_RELEASE")
            }
        }
    }

    fun markCandidateHandled() {
        require(state == FslPractical15CaptureState.CANDIDATE)
        state = FslPractical15CaptureState.WAIT_FOR_RELEASE
        neutralCount = 0
        clearEvent()
    }

    fun reset() {
        state = FslPractical15CaptureState.IDLE
        neutralCount = 0
        eventCounter = 0
        clearEvent()
    }

    private fun collect(frame: LandmarkFrame): FslPractical15CaptureUpdate {
        if (frame.timestampMs - eventStartedAtMs > config.maximumEventDurationMs ||
            frames.size + pendingDropout.size >= config.maximumEventFrames
        ) return abort("EVENT_TIMEOUT")
        if (!frame.hasPose) {
            missingPoseCount += 1
            return if (missingPoseCount >= config.maximumConsecutiveMissingPose) {
                abort("POSE_TRACKING_LOST")
            } else update("TRANSIENT_POSE_DROPOUT")
        }
        missingPoseCount = 0
        val canonical = StandardFullSign225FeatureBuilder.build(frame, inputMirrored = false)
        if (frame.hasAnyHand) {
            frames += pendingDropout
            pendingDropout.clear()
            frames += canonical
            return update("SIGN_ACTIVE")
        }
        pendingDropout += canonical
        if (pendingDropout.size < config.releaseFrames) return update("END_CANDIDATE")
        if (frames.size < config.minimumEventFrames) return abort("INCOMPLETE_EVENT_REJECTED")
        eventCounter += 1
        val quality = FslPractical15EventQuality(
            frames.size,
            frames.count { it.quality.posePresent },
            frames.count { it.quality.leftHandPresent },
            frames.count { it.quality.rightHandPresent },
            frames.count { it.quality.anyHandPresent },
            frames.first().timestampMs,
            frames.last().timestampMs,
            frames.zipWithNext { before, after ->
                sqrt(
                    before.vector.indices.sumOf { index ->
                        val difference = after.vector[index] - before.vector[index]
                        (difference * difference).toDouble()
                    }
                ).toFloat()
            }.average().toFloat()
        )
        val candidate = FslPractical15Candidate(
            eventCounter,
            CompleteEventResampler48.resample(frames.map { it.vector }),
            quality
        )
        state = FslPractical15CaptureState.CANDIDATE
        return update("COMPLETE_EVENT_RESAMPLED_48", candidate)
    }

    private fun append(frame: LandmarkFrame) {
        frames += StandardFullSign225FeatureBuilder.build(frame, inputMirrored = false)
    }

    private fun abort(reason: String): FslPractical15CaptureUpdate {
        state = FslPractical15CaptureState.WAIT_FOR_RELEASE
        neutralCount = 0
        clearEvent()
        return update(reason)
    }

    private fun clearEvent() {
        frames.clear()
        pendingDropout.clear()
        missingPoseCount = 0
        eventStartedAtMs = 0L
    }

    private fun update(reason: String, candidate: FslPractical15Candidate? = null) =
        FslPractical15CaptureUpdate(state, reason, candidate)
}

class FslPractical15Gate(
    private val minimumConfidence: Float = 0.95f,
    private val minimumMargin: Float = 0.05f,
    private val minimumTrajectoryMotionMeanL2: Float = 0.02f
) {
    fun evaluate(inference: StandardFslInference, quality: FslPractical15EventQuality): StandardFslGateDecision {
        val reason = when {
            quality.rawFrameCount < 8 -> "INCOMPLETE_EVENT"
            quality.posePresenceRatio < 0.65f -> "LOW_POSE_PRESENCE"
            quality.anyHandPresenceRatio < 0.65f -> "LOW_HAND_PRESENCE"
            quality.trajectoryMotionMeanL2 < minimumTrajectoryMotionMeanL2 -> "LOW_TRAJECTORY_MOTION"
            inference.top1.probability < minimumConfidence -> "LOW_CONFIDENCE"
            inference.margin < minimumMargin -> "LOW_MARGIN"
            else -> "ACCEPTED"
        }
        return StandardFslGateDecision(
            reason == "ACCEPTED",
            reason,
            if (reason == "ACCEPTED") inference.top1.label else StandardFslRejectionGate.HOLD_SIGN_CLEARLY,
            1,
            false
        )
    }
}
