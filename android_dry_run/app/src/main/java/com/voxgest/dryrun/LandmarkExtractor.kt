package com.voxgest.dryrun

import androidx.camera.core.ImageProxy

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

data class LandmarkFrame(
    val poseLandmarks: List<LandmarkPoint>?,
    val leftHandLandmarks: List<LandmarkPoint>?,
    val rightHandLandmarks: List<LandmarkPoint>?,
    val timestampMs: Long
) {
    val hasPose: Boolean = poseLandmarks?.size == 33
    val hasLeftHand: Boolean = leftHandLandmarks?.size == 21
    val hasRightHand: Boolean = rightHandLandmarks?.size == 21
    val hasAnyHand: Boolean = hasLeftHand || hasRightHand
    val handPresence: Float = if (hasAnyHand) 1f else 0f
}

interface LandmarkExtractor : AutoCloseable {
    fun processFrame(imageProxy: ImageProxy): LandmarkFrame?

    override fun close()
}
