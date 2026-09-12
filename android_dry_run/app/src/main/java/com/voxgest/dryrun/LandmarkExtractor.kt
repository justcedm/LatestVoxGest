package com.voxgest.dryrun

import androidx.camera.core.ImageProxy

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

data class HandObservation(
    val slot: String,
    val mediaPipeHandedness: String,
    val averageX: Float,
    val physicalSideEstimate: String
)

data class LandmarkFrame(
    val poseLandmarks: List<LandmarkPoint>?,
    val leftHandLandmarks: List<LandmarkPoint>?,
    val rightHandLandmarks: List<LandmarkPoint>?,
    val timestampMs: Long,
    val handObservations: List<HandObservation> = emptyList(),
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0
) {
    val hasPose: Boolean = poseLandmarks?.size == 33
    val hasLeftHand: Boolean = leftHandLandmarks?.size == 21
    val hasRightHand: Boolean = rightHandLandmarks?.size == 21
    val hasAnyHand: Boolean = hasLeftHand || hasRightHand
    val handPresence: Float = if (hasAnyHand) 1f else 0f
}

/** Display-only envelope. Inference consumes [frame] directly and never this mapping metadata. */
data class LandmarkVisualizationFrame(
    val frame: LandmarkFrame,
    val analysisMirrored: Boolean
)

/** Recognition-only timing evidence; no display or model behavior depends on it. */
data class LandmarkExtractionMetrics(
    val sourceTimestampNanos: Long,
    val acquisitionAgeMs: Double?,
    val conversionMs: Double,
    val handLandmarkerMs: Double,
    val poseLandmarkerMs: Double,
    val totalMs: Double
)

interface LandmarkExtractor : AutoCloseable {
    fun processFrame(imageProxy: ImageProxy): LandmarkFrame?

    override fun close()
}
