package com.voxgest.dryrun.fsl

import com.voxgest.dryrun.LandmarkFrame
import com.voxgest.dryrun.LandmarkPoint
import kotlin.math.sqrt

data class FslCanonicalFrame(
    val vector: FloatArray,
    val posePresent: Boolean,
    val handPresent: Boolean,
    val selectedHand: String = FslContract.SELECTED_HAND,
    val wristMcpScale: Float = 0f
)

object FslCanonicalFeatureBuilder {
    private const val POSE_COUNT = 33
    private const val HAND_COUNT = 21
    private const val POSE_SIZE = 99
    private const val HAND_OFFSET = 99
    private const val WRIST = 0
    private const val MIDDLE_MCP = 9

    fun build(frame: LandmarkFrame?): FslCanonicalFrame {
        return build(frame?.poseLandmarks, frame?.rightHandLandmarks)
    }

    fun build(
        poseLandmarks: List<LandmarkPoint>?,
        anatomicalRightHandLandmarks: List<LandmarkPoint>?
    ): FslCanonicalFrame {
        val validPose = poseLandmarks?.takeIf { validLandmarks(it, POSE_COUNT) }
        val validHand = anatomicalRightHandLandmarks?.takeIf { validLandmarks(it, HAND_COUNT) }
        if (validPose == null) {
            return FslCanonicalFrame(
                vector = FloatArray(FslContract.FEATURE_SIZE),
                posePresent = false,
                handPresent = validHand != null
            )
        }

        val output = FloatArray(FslContract.FEATURE_SIZE)
        val nose = validPose[0]
        validPose.forEachIndexed { index, point ->
            val offset = index * 3
            output[offset] = point.x - nose.x
            output[offset + 1] = point.y - nose.y
            output[offset + 2] = point.z - nose.z
        }
        output[0] = 0f
        output[1] = 0f
        output[2] = 0f

        var scale = 0f
        if (validHand != null) {
            validHand.forEachIndexed { index, point ->
                val offset = HAND_OFFSET + index * 3
                output[offset] = point.x - nose.x
                output[offset + 1] = point.y - nose.y
                output[offset + 2] = point.z - nose.z
            }
            val wristOffset = HAND_OFFSET + WRIST * 3
            val mcpOffset = HAND_OFFSET + MIDDLE_MCP * 3
            val dx = output[wristOffset] - output[mcpOffset]
            val dy = output[wristOffset + 1] - output[mcpOffset + 1]
            val dz = output[wristOffset + 2] - output[mcpOffset + 2]
            scale = sqrt(dx * dx + dy * dy + dz * dz)
            if (scale > FslContract.MIN_WRIST_MCP_SCALE) {
                for (index in 0 until HAND_COUNT * 3) {
                    output[HAND_OFFSET + index] /= scale
                }
            }
        }

        var zIndex = 2
        while (zIndex < output.size) {
            output[zIndex] *= FslContract.Z_DAMPING
            zIndex += 3
        }
        return FslCanonicalFrame(
            vector = output,
            posePresent = true,
            handPresent = validHand != null,
            wristMcpScale = scale
        )
    }

    private fun validLandmarks(points: List<LandmarkPoint>, expected: Int): Boolean {
        return points.size == expected && points.all {
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }
    }
}
