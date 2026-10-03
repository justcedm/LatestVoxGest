/**
 * @file RecognitionFeedback.kt
 * @description UI-safe recognition feedback payload for live hand scan overlays.
 * @author VoxGest Team
 * @version 1.0.0
 */
package com.voxgest.dryrun

enum class DetectionStatus {
    SEARCHING,
    DETECTING,
    RECOGNIZED
}

data class OverlayLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

data class RecognitionFeedback(
    val detectionStatus: DetectionStatus,
    val confidence: Float,
    val label: String,
    val handLandmarks: List<OverlayLandmarkPoint>,
    val eventId: Long = 0L
) {
    companion object {
        fun idle(): RecognitionFeedback {
            return RecognitionFeedback(
                detectionStatus = DetectionStatus.SEARCHING,
                confidence = 0f,
                label = "",
                handLandmarks = emptyList()
            )
        }
    }
}
