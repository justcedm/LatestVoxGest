package com.voxgest.dryrun

import kotlin.math.sqrt

object StandardFullSign225Contract {
    const val FEATURE_VERSION = "fullsign225_20f_v1"
    const val SEQUENCE_LENGTH = 20
    const val POSE_LANDMARK_COUNT = 33
    const val HAND_LANDMARK_COUNT = 21
    const val POSE_SIZE = 99
    const val HAND_SIZE = 63
    const val FEATURE_SIZE = 225
    const val LEFT_START = 99
    const val RIGHT_START = 162
    const val MIN_WRIST_MCP_SCALE = 0.001f
    const val Z_DAMPING = 0.3f
}

data class StandardFullSign225FrameQuality(
    val posePresent: Boolean,
    val leftHandPresent: Boolean,
    val rightHandPresent: Boolean,
    val leftHandScale: Float,
    val rightHandScale: Float,
    val leftHandScaleApplied: Boolean,
    val rightHandScaleApplied: Boolean,
    val inputMirrored: Boolean,
    val issues: List<String>
) {
    val anyHandPresent: Boolean get() = leftHandPresent || rightHandPresent
    val bothHandsPresent: Boolean get() = leftHandPresent && rightHandPresent
}

data class StandardFullSign225Frame(
    val vector: FloatArray,
    val quality: StandardFullSign225FrameQuality,
    val timestampMs: Long = 0L
)

/**
 * Canonical Android implementation of Python fullsign225_20f_v1.
 *
 * Layout: pose XYZ [0,99), anatomical LEFT XYZ [99,162), anatomical RIGHT
 * XYZ [162,225). No first-detected/dominant hand swapping is permitted.
 */
object StandardFullSign225FeatureBuilder {
    fun build(frame: LandmarkFrame, inputMirrored: Boolean = false): StandardFullSign225Frame {
        require(!inputMirrored) {
            "${StandardFullSign225Contract.FEATURE_VERSION} requires unmirrored model input"
        }

        val pose = validate(frame.poseLandmarks, StandardFullSign225Contract.POSE_LANDMARK_COUNT, "pose")

        if (pose == null) {
            return StandardFullSign225Frame(
                vector = FloatArray(StandardFullSign225Contract.FEATURE_SIZE),
                quality = StandardFullSign225FrameQuality(
                    posePresent = false,
                    leftHandPresent = frame.leftHandLandmarks != null,
                    rightHandPresent = frame.rightHandLandmarks != null,
                    leftHandScale = 0f,
                    rightHandScale = 0f,
                    leftHandScaleApplied = false,
                    rightHandScaleApplied = false,
                    inputMirrored = false,
                    issues = listOf("missing_pose_zero_frame")
                ),
                timestampMs = frame.timestampMs
            )
        }

        val left = validate(frame.leftHandLandmarks, StandardFullSign225Contract.HAND_LANDMARK_COUNT, "left_hand")
        val right = validate(frame.rightHandLandmarks, StandardFullSign225Contract.HAND_LANDMARK_COUNT, "right_hand")

        val nose = pose[0]
        val poseValues = normalizePose(pose, nose)
        val leftResult = normalizeHand(left, nose, "left")
        val rightResult = normalizeHand(right, nose, "right")
        val output = FloatArray(StandardFullSign225Contract.FEATURE_SIZE)
        System.arraycopy(poseValues, 0, output, 0, StandardFullSign225Contract.POSE_SIZE)
        System.arraycopy(
            leftResult.values,
            0,
            output,
            StandardFullSign225Contract.LEFT_START,
            StandardFullSign225Contract.HAND_SIZE
        )
        System.arraycopy(
            rightResult.values,
            0,
            output,
            StandardFullSign225Contract.RIGHT_START,
            StandardFullSign225Contract.HAND_SIZE
        )
        var zIndex = 2
        while (zIndex < output.size) {
            output[zIndex] *= StandardFullSign225Contract.Z_DAMPING
            zIndex += 3
        }
        require(output.all { it.isFinite() }) { "canonical FullSign225 frame contains NaN or Inf" }

        return StandardFullSign225Frame(
            vector = output,
            quality = StandardFullSign225FrameQuality(
                posePresent = true,
                leftHandPresent = leftResult.present,
                rightHandPresent = rightResult.present,
                leftHandScale = leftResult.scale,
                rightHandScale = rightResult.scale,
                leftHandScaleApplied = leftResult.scaleApplied,
                rightHandScaleApplied = rightResult.scaleApplied,
                inputMirrored = false,
                issues = leftResult.issues + rightResult.issues
            ),
            timestampMs = frame.timestampMs
        )
    }

    private fun validate(
        points: List<LandmarkPoint>?,
        expectedCount: Int,
        name: String
    ): List<LandmarkPoint>? {
        if (points == null) return null
        require(points.size == expectedCount) {
            "$name must contain $expectedCount landmarks, got ${points.size}"
        }
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) {
            "$name contains NaN or Inf"
        }
        return points
    }

    private fun normalizePose(points: List<LandmarkPoint>, nose: LandmarkPoint): FloatArray {
        val out = FloatArray(StandardFullSign225Contract.POSE_SIZE)
        points.forEachIndexed { index, point ->
            val dest = index * 3
            out[dest] = point.x - nose.x
            out[dest + 1] = point.y - nose.y
            out[dest + 2] = point.z - nose.z
        }
        out[0] = 0f
        out[1] = 0f
        out[2] = 0f
        return out
    }

    private fun normalizeHand(
        points: List<LandmarkPoint>?,
        nose: LandmarkPoint,
        side: String
    ): NormalizedHand {
        if (points == null) {
            return NormalizedHand(
                values = FloatArray(StandardFullSign225Contract.HAND_SIZE),
                present = false,
                scale = 0f,
                scaleApplied = false,
                issues = listOf("missing_${side}_hand")
            )
        }
        val values = FloatArray(StandardFullSign225Contract.HAND_SIZE)
        points.forEachIndexed { index, point ->
            val dest = index * 3
            values[dest] = point.x - nose.x
            values[dest + 1] = point.y - nose.y
            values[dest + 2] = point.z - nose.z
        }
        val middleMcp = 9 * 3
        val dx = values[0] - values[middleMcp]
        val dy = values[1] - values[middleMcp + 1]
        val dz = values[2] - values[middleMcp + 2]
        val scale = sqrt(dx * dx + dy * dy + dz * dz)
        val applied = scale > StandardFullSign225Contract.MIN_WRIST_MCP_SCALE
        if (applied) {
            values.indices.forEach { index -> values[index] /= scale }
        }
        return NormalizedHand(
            values = values,
            present = true,
            scale = scale,
            scaleApplied = applied,
            issues = if (applied) emptyList() else listOf("degenerate_${side}_hand_scale")
        )
    }

    private data class NormalizedHand(
        val values: FloatArray,
        val present: Boolean,
        val scale: Float,
        val scaleApplied: Boolean,
        val issues: List<String>
    )
}
