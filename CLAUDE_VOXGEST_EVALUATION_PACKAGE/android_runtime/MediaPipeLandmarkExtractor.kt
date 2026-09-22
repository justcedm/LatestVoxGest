package com.voxgest.dryrun

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class MediaPipeLandmarkExtractor(
    context: Context,
    private val mirrorCameraFrame: Boolean
) : LandmarkExtractor {
    private val appContext = context.applicationContext
    private val handLandmarker: HandLandmarker
    private val poseLandmarker: PoseLandmarker
    private var lastTimestampMs: Long = 0L

    init {
        val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(HAND_LANDMARKER_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.45f)
            .setMinHandPresenceConfidence(0.45f)
            .setMinTrackingConfidence(0.45f)
            .build()
        val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(POSE_LANDMARKER_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setMinPoseDetectionConfidence(0.45f)
            .setMinPosePresenceConfidence(0.45f)
            .setMinTrackingConfidence(0.45f)
            .build()
        handLandmarker = HandLandmarker.createFromOptions(appContext, handOptions)
        poseLandmarker = PoseLandmarker.createFromOptions(appContext, poseOptions)
        Log.w(TAG, "MediaPipe Android uses Tasks hand+pose landmarks; mirrorCameraFrame=$mirrorCameraFrame; live parity still needs calibration.")
    }

    @Synchronized
    override fun processFrame(imageProxy: ImageProxy): LandmarkFrame? {
        val timestampMs = nextTimestamp(imageProxy.imageInfo.timestamp / 1_000_000L)
        val bitmap = ImageProxyBitmapConverter.toUprightBitmap(imageProxy, mirrorCameraFrame)
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val handResult = handLandmarker.detectForVideo(mpImage, timestampMs)
            val poseResult = poseLandmarker.detectForVideo(mpImage, timestampMs)
            val poseLandmarks = poseResult.landmarks().firstOrNull()?.toLandmarkPoints()

            var leftHand: List<LandmarkPoint>? = null
            var rightHand: List<LandmarkPoint>? = null
            val observations = mutableListOf<HandObservation>()
            val hands = handResult.landmarks()
            val handedness = handResult.handedness()
            hands.forEachIndexed { index, landmarks ->
                val label = handedness
                    .getOrNull(index)
                    ?.firstOrNull()
                    ?.categoryName()
                    .orEmpty()
                    .lowercase()
                val averageX = landmarks.map { it.x() }.average().toFloat()
                when (label) {
                    "left" -> {
                        leftHand = landmarks.toLandmarkPoints()
                        observations.add(handObservation("left", label, averageX))
                    }
                    "right" -> {
                        rightHand = landmarks.toLandmarkPoints()
                        observations.add(handObservation("right", label, averageX))
                    }
                    else -> {
                        if (averageX < 0.5f && leftHand == null) {
                            leftHand = landmarks.toLandmarkPoints()
                            observations.add(handObservation("left", "unknown", averageX))
                        } else if (rightHand == null) {
                            rightHand = landmarks.toLandmarkPoints()
                            observations.add(handObservation("right", "unknown", averageX))
                        }
                    }
                }
            }

            LandmarkFrame(
                poseLandmarks = poseLandmarks,
                leftHandLandmarks = leftHand,
                rightHandLandmarks = rightHand,
                timestampMs = timestampMs,
                handObservations = observations
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Synchronized
    override fun close() {
        handLandmarker.close()
        poseLandmarker.close()
    }

    private fun nextTimestamp(candidateMs: Long): Long {
        val next = if (candidateMs <= lastTimestampMs) lastTimestampMs + 1L else candidateMs
        lastTimestampMs = next
        return next
    }

    private fun List<NormalizedLandmark>.toLandmarkPoints(): List<LandmarkPoint> {
        return map { landmark -> LandmarkPoint(landmark.x(), landmark.y(), landmark.z()) }
    }

    private fun handObservation(slot: String, label: String, averageX: Float): HandObservation {
        val processedSide = if (averageX < 0.5f) "processed_left" else "processed_right"
        val physicalEstimate = if (mirrorCameraFrame) {
            if (averageX < 0.5f) "physical_right_estimated_after_mirror" else "physical_left_estimated_after_mirror"
        } else {
            if (averageX < 0.5f) "physical_left_estimated" else "physical_right_estimated"
        }
        return HandObservation(
            slot = slot,
            mediaPipeHandedness = label.ifBlank { "unknown" },
            averageX = averageX,
            physicalSideEstimate = "$physicalEstimate/$processedSide"
        )
    }

    companion object {
        private const val TAG = "VoxGestLandmarks"
        private const val HAND_LANDMARKER_ASSET = "model/hand_landmarker.task"
        private const val POSE_LANDMARKER_ASSET = "model/pose_landmarker_lite.task"
    }
}
