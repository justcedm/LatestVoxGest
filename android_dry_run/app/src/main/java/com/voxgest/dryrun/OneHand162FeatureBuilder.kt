package com.voxgest.dryrun

import android.util.Log
import kotlin.math.sqrt

class OneHand162FeatureBuilder(private val profile: RecognitionProfile) {
    val featureSize: Int = LandmarkSequenceBuffer.ONEHAND162_FEATURE_SIZE

    fun build(frame: LandmarkFrame): FloatArray? {
        val parts = buildParts(frame) ?: return null
        return buildOutput162(parts)
    }

    fun buildWithDelta(current: LandmarkFrame, previous: LandmarkFrame?): FloatArray? {
        val currentParts = buildParts(current) ?: return null
        val output162 = buildOutput162(currentParts)
        val output225 = output162.copyOf(LandmarkSequenceBuffer.FULLSIGN_WITH_DELTA_FEATURE_SIZE)
        if (previous == null) return output225

        val previousParts = buildParts(previous) ?: return null
        for (index in 0 until HAND_SIZE) {
            output225[POSE_SIZE + HAND_SIZE + index] = currentParts.handValues[index] - previousParts.handValues[index]
        }
        return output225
    }

    private fun buildParts(frame: LandmarkFrame): FeatureParts? {
        val pose = frame.poseLandmarks ?: return null
        if (pose.size != POSE_LANDMARK_COUNT) return null

        val selectedSide = mediaPipeSideForPreference(profile.dominantHand, profile.mirroredInput)
        val selectedHand = when (selectedSide) {
            "left" -> frame.leftHandLandmarks
            "right" -> frame.rightHandLandmarks
            else -> frame.rightHandLandmarks ?: frame.leftHandLandmarks
        }
        if (selectedHand?.size != HAND_LANDMARK_COUNT) return null

        val nose = pose[0]
        val poseValues = FloatArray(POSE_SIZE)
        for (index in 0 until POSE_LANDMARK_COUNT) {
            val shouldKeep = !profile.singleHandPose || poseKeepIndices(selectedSide).contains(index)
            val out = index * 3
            if (shouldKeep) {
                poseValues[out] = pose[index].x - nose.x
                poseValues[out + 1] = pose[index].y - nose.y
                poseValues[out + 2] = pose[index].z - nose.z
            }
        }

        val handValues = FloatArray(HAND_SIZE)
        for (index in 0 until HAND_LANDMARK_COUNT) {
            val out = index * 3
            handValues[out] = selectedHand[index].x - nose.x
            handValues[out + 1] = selectedHand[index].y - nose.y
            handValues[out + 2] = selectedHand[index].z - nose.z
        }
        val scale = normalizeHandByWristToMcp(handValues)

        return FeatureParts(poseValues, handValues, scale)
    }

    private fun buildOutput162(parts: FeatureParts): FloatArray {
        return FloatArray(featureSize).also { output ->
            System.arraycopy(parts.poseValues, 0, output, 0, POSE_SIZE)
            System.arraycopy(parts.handValues, 0, output, POSE_SIZE, HAND_SIZE)
            output[0] = 0f
            output[1] = 0f
            output[2] = 0f
            dampenZValues(output)
            Log.i(
                FEATURE_TAG,
                "scale=${parts.scale} wrist_mcp_normalized=${parts.scale > MIN_WRIST_MCP_SCALE} z_damped=true"
            )
        }
    }

    fun selectedMediaPipeSide(): String {
        return mediaPipeSideForPreference(profile.dominantHand, profile.mirroredInput)
    }

    fun selectedHandLandmarks(frame: LandmarkFrame): List<LandmarkPoint>? {
        return handForSide(frame, selectedMediaPipeSide())
    }

    fun selectedMappingText(): String {
        val side = selectedMediaPipeSide()
        val physical = when {
            profile.dominantHand.lowercase() == "right" -> "physical_right"
            profile.dominantHand.lowercase() == "left" -> "physical_left"
            else -> "physical_auto"
        }
        return "$physical -> mediapipe_$side mirroredInput=${profile.mirroredInput}"
    }

    fun hasRequiredLandmarks(frame: LandmarkFrame): Boolean {
        val selectedSide = mediaPipeSideForPreference(profile.dominantHand, profile.mirroredInput)
        val selectedHand = handForSide(frame, selectedSide)
        return frame.hasPose && selectedHand?.size == HAND_LANDMARK_COUNT
    }

    private fun handForSide(frame: LandmarkFrame, selectedSide: String): List<LandmarkPoint>? {
        return when (selectedSide) {
            "left" -> frame.leftHandLandmarks
            "right" -> frame.rightHandLandmarks
            else -> frame.rightHandLandmarks ?: frame.leftHandLandmarks
        }
    }

    private fun mediaPipeSideForPreference(handPreference: String, mirroredInput: Boolean): String {
        val pref = handPreference.lowercase()
        if (pref != "left" && pref != "right") return "auto"
        return if (mirroredInput) {
            if (pref == "right") "left" else "right"
        } else {
            pref
        }
    }

    private fun poseKeepIndices(handSide: String): Set<Int> {
        return when (handSide) {
            "left" -> (HEAD_LANDMARKS + TORSO_LANDMARKS + LEFT_ARM_LANDMARKS).toSet()
            "right" -> (HEAD_LANDMARKS + TORSO_LANDMARKS + RIGHT_ARM_LANDMARKS).toSet()
            else -> (HEAD_LANDMARKS + TORSO_LANDMARKS + LEFT_ARM_LANDMARKS + RIGHT_ARM_LANDMARKS).toSet()
        }
    }

    private fun normalizeHandByWristToMcp(handValues: FloatArray): Float {
        val dx = handValues[0] - handValues[27]
        val dy = handValues[1] - handValues[28]
        val dz = handValues[2] - handValues[29]
        val scale = sqrt(dx * dx + dy * dy + dz * dz)
        if (scale > MIN_WRIST_MCP_SCALE) {
            for (index in handValues.indices) {
                handValues[index] /= scale
            }
        }
        return scale
    }

    private fun dampenZValues(output: FloatArray) {
        var index = 2
        while (index < output.size) {
            output[index] *= Z_DAMPING
            index += 3
        }
    }

    private data class FeatureParts(
        val poseValues: FloatArray,
        val handValues: FloatArray,
        val scale: Float
    )

    companion object {
        private const val FEATURE_TAG = "VoxGestFeature"
        private const val POSE_LANDMARK_COUNT = 33
        private const val HAND_LANDMARK_COUNT = 21
        private const val POSE_SIZE = 99
        private const val HAND_SIZE = 63
        private const val MIN_WRIST_MCP_SCALE = 0.001f
        private const val Z_DAMPING = 0.3f
        private val HEAD_LANDMARKS = (0..10).toSet()
        private val TORSO_LANDMARKS = setOf(11, 12, 23, 24)
        private val LEFT_ARM_LANDMARKS = setOf(11, 13, 15, 17, 19, 21)
        private val RIGHT_ARM_LANDMARKS = setOf(12, 14, 16, 18, 20, 22)
    }
}
