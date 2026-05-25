package com.voxgest.dryrun

class FullSign225FeatureBuilder(private val profile: RecognitionProfile) {
    val featureSize: Int = LandmarkSequenceBuffer.FULLSIGN225_FEATURE_SIZE

    fun build(frame: LandmarkFrame): FloatArray? {
        val pose = frame.poseLandmarks ?: return null
        if (pose.size != POSE_LANDMARK_COUNT) return null
        val nose = pose[0]

        val poseValues = FloatArray(POSE_SIZE)
        for (index in 0 until POSE_LANDMARK_COUNT) {
            val out = index * 3
            poseValues[out] = pose[index].x - nose.x
            poseValues[out + 1] = pose[index].y - nose.y
            poseValues[out + 2] = pose[index].z - nose.z
        }

        val leftSource = if (profile.mirroredInput) frame.rightHandLandmarks else frame.leftHandLandmarks
        val rightSource = if (profile.mirroredInput) frame.leftHandLandmarks else frame.rightHandLandmarks
        val leftHand = handValues(leftSource, nose)
        val rightHand = handValues(rightSource, nose)

        return FloatArray(featureSize).also { output ->
            System.arraycopy(poseValues, 0, output, 0, POSE_SIZE)
            System.arraycopy(leftHand, 0, output, POSE_SIZE, HAND_SIZE)
            System.arraycopy(rightHand, 0, output, POSE_SIZE + HAND_SIZE, HAND_SIZE)
            output[0] = 0f
            output[1] = 0f
            output[2] = 0f
        }
    }

    fun hasRequiredLandmarks(frame: LandmarkFrame): Boolean {
        return frame.hasPose && frame.hasAnyHand
    }

    private fun handValues(points: List<LandmarkPoint>?, nose: LandmarkPoint): FloatArray {
        val out = FloatArray(HAND_SIZE)
        if (points?.size != HAND_LANDMARK_COUNT) return out
        for (index in 0 until HAND_LANDMARK_COUNT) {
            val dest = index * 3
            out[dest] = points[index].x - nose.x
            out[dest + 1] = points[index].y - nose.y
            out[dest + 2] = points[index].z - nose.z
        }
        return out
    }

    companion object {
        private const val POSE_LANDMARK_COUNT = 33
        private const val HAND_LANDMARK_COUNT = 21
        private const val POSE_SIZE = 99
        private const val HAND_SIZE = 63
    }
}
