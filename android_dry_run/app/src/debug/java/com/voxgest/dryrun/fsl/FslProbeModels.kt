package com.voxgest.dryrun.fsl

import java.util.Locale
import kotlin.math.abs

/**
 * Debug-only contract for an FSL probe window.
 *
 * Keeping this model in the debug source set prevents probe/export behavior from becoming part
 * of the stable release runtime. Kotlin [Float] is used deliberately: every canonical feature
 * supplied to this API is a float32 value before it is serialized as a JSON number.
 */
object FslProbeContract {
    const val FEATURE_VERSION = "onehand162_20f_nose_mcp_z03_v2"
    const val PROFILE_ID = "fsl_onehand162_20f_rdtcn_v2"
    const val DTYPE = "float32"
    const val SEQUENCE_LENGTH = 20
    const val FEATURE_SIZE = 162
    const val POSE_LANDMARK_COUNT = 33
    const val HAND_LANDMARK_COUNT = 21

    val INPUT_SHAPE: List<Int> = listOf(1, SEQUENCE_LENGTH, FEATURE_SIZE)
}

data class FslProbeLandmark(
    val x: Float,
    val y: Float,
    val z: Float
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) {
            "Probe landmarks must contain finite float32 coordinates"
        }
    }
}

data class FslProbeRawFrame(
    val timestampMs: Long,
    val poseLandmarks: List<FslProbeLandmark>?,
    val rightHandLandmarks: List<FslProbeLandmark>?
) {
    init {
        require(timestampMs >= 0L) { "Frame timestamp must be non-negative" }
        require(poseLandmarks == null || poseLandmarks.size == FslProbeContract.POSE_LANDMARK_COUNT) {
            "Pose landmarks must be null or contain exactly ${FslProbeContract.POSE_LANDMARK_COUNT} points"
        }
        require(rightHandLandmarks == null || rightHandLandmarks.size == FslProbeContract.HAND_LANDMARK_COUNT) {
            "Right-hand landmarks must be null or contain exactly ${FslProbeContract.HAND_LANDMARK_COUNT} points"
        }
    }
}

data class FslProbeWindow(
    val rawFrames: List<FslProbeRawFrame>,
    val canonicalFeatures: List<FloatArray>
) {
    init {
        require(rawFrames.size == FslProbeContract.SEQUENCE_LENGTH) {
            "A probe attempt must contain exactly ${FslProbeContract.SEQUENCE_LENGTH} raw frames"
        }
        require(canonicalFeatures.size == FslProbeContract.SEQUENCE_LENGTH) {
            "A probe attempt must contain exactly ${FslProbeContract.SEQUENCE_LENGTH} canonical feature frames"
        }
        canonicalFeatures.forEachIndexed { frameIndex, features ->
            require(features.size == FslProbeContract.FEATURE_SIZE) {
                "Canonical frame $frameIndex must contain exactly ${FslProbeContract.FEATURE_SIZE} float32 values"
            }
            require(features.all(Float::isFinite)) {
                "Canonical frame $frameIndex contains a non-finite feature"
            }
        }
        rawFrames.zipWithNext().forEachIndexed { frameIndex, (previous, next) ->
            require(next.timestampMs > previous.timestampMs) {
                "Raw frame timestamps must be strictly increasing (frames $frameIndex and ${frameIndex + 1})"
            }
        }
    }

    val startedAtMs: Long
        get() = rawFrames.first().timestampMs

    val endedAtMs: Long
        get() = rawFrames.last().timestampMs

    val poseFrameCount: Int
        get() = rawFrames.count { it.poseLandmarks != null }

    val rightHandFrameCount: Int
        get() = rawFrames.count { it.rightHandLandmarks != null }
}

data class FslProbeRank(
    val label: String,
    val score: Float
) {
    init {
        requireSafeProbeText("rank label", label)
        require(score.isFinite() && score in 0f..1f) {
            "Rank score must be a finite probability in [0, 1]"
        }
    }
}

data class FslProbeMetadata(
    val expected: String,
    val predicted: String,
    val top1: FslProbeRank,
    val top2: FslProbeRank,
    val margin: Float,
    val latencyMs: Float,
    val accepted: Boolean,
    val state: String,
    val timestampMs: Long,
    val signerId: String,
    val sessionId: String,
    val deviceId: String,
    val modelId: String,
    val profileId: String = FslProbeContract.PROFILE_ID,
    val featureVersion: String = FslProbeContract.FEATURE_VERSION,
    val shape: List<Int> = FslProbeContract.INPUT_SHAPE,
    val dtype: String = FslProbeContract.DTYPE
) {
    init {
        requireSafeProbeText("expected label", expected)
        requireSafeProbeText("predicted label", predicted)
        requireSafeProbeText("state", state)
        requireSafeProbeText("signer id", signerId)
        requireSafeProbeText("session id", sessionId)
        requireSafeProbeText("device id", deviceId)
        requireSafeProbeText("model id", modelId)
        requireSafeProbeText("profile id", profileId)
        require(featureVersion == FslProbeContract.FEATURE_VERSION) {
            "Unexpected feature contract: $featureVersion"
        }
        require(shape == FslProbeContract.INPUT_SHAPE) {
            "Probe input shape must be ${FslProbeContract.INPUT_SHAPE}"
        }
        require(dtype.lowercase(Locale.US) == FslProbeContract.DTYPE) {
            "Probe dtype must be ${FslProbeContract.DTYPE}"
        }
        require(margin.isFinite()) { "Prediction margin must be finite" }
        require(abs(margin - (top1.score - top2.score)) <= SCORE_TOLERANCE) {
            "Prediction margin must equal top1.score - top2.score"
        }
        require(latencyMs.isFinite() && latencyMs >= 0f) {
            "Inference latency must be a finite, non-negative duration"
        }
        require(timestampMs >= 0L) { "Probe timestamp must be non-negative" }
    }

    companion object {
        private const val SCORE_TOLERANCE = 1e-4f
    }
}

data class FslProbeAttempt(
    val metadata: FslProbeMetadata,
    val window: FslProbeWindow
)

data class FslProbePaths(
    val sessionDirectoryPath: String,
    val csvPath: String,
    val jsonlPath: String
)

data class FslFeatureExportResult(
    val absolutePath: String,
    val relativePath: String
)

data class FslProbeRecordResult(
    val recorded: Boolean,
    val rejectionReason: String?,
    val attemptForExpected: Int,
    val totalSessionAttempts: Int,
    val maxAttemptsPerExpected: Int,
    val paths: FslProbePaths,
    val windowJsonPath: String?
)

internal fun requireSafeProbeText(field: String, value: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
    require(value.length <= 256) { "$field must contain at most 256 characters" }
    require(value.none { it.isISOControl() }) { "$field must not contain control characters" }
}
