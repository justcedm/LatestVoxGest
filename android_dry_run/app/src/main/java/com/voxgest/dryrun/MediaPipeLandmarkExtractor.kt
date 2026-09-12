package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
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
    private val mirrorCameraFrame: Boolean,
    private val reportedHandednessPolicy: ReportedHandednessPolicy =
        ReportedHandednessPolicy.DIRECT_REPORTED_SIDES,
    private val onMetrics: (LandmarkExtractionMetrics) -> Unit = {}
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
        val startedNanos = SystemClock.elapsedRealtimeNanos()
        val sourceTimestampNanos = imageProxy.imageInfo.timestamp
        val rawAgeMs = (startedNanos - sourceTimestampNanos) / NANOS_PER_MS
        val acquisitionAgeMs = rawAgeMs.takeIf { it >= 0.0 && it <= MAX_REASONABLE_ACQUISITION_AGE_MS }
        val timestampMs = nextTimestamp(imageProxy.imageInfo.timestamp / 1_000_000L)
        val bitmap = ImageProxyBitmapConverter.toUprightBitmap(imageProxy, mirrorCameraFrame)
        val convertedNanos = SystemClock.elapsedRealtimeNanos()
        var handFinishedNanos = convertedNanos
        var poseFinishedNanos = convertedNanos
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val handResult = handLandmarker.detectForVideo(mpImage, timestampMs)
            handFinishedNanos = SystemClock.elapsedRealtimeNanos()
            val poseResult = poseLandmarker.detectForVideo(mpImage, timestampMs)
            poseFinishedNanos = SystemClock.elapsedRealtimeNanos()
            val poseLandmarks = poseResult.landmarks().firstOrNull()?.toLandmarkPoints()

            var leftHand: List<LandmarkPoint>? = null
            var rightHand: List<LandmarkPoint>? = null
            val observations = mutableListOf<HandObservation>()
            val hands = handResult.landmarks()
            val handedness = handResult.handedness()
            hands.forEachIndexed { index, landmarks ->
                val reportedLabel = handedness
                    .getOrNull(index)
                    ?.firstOrNull()
                    ?.categoryName()
                    .orEmpty()
                    .lowercase()
                val anatomicalSide = AnatomicalHandedness.resolve(
                    reportedLabel,
                    reportedHandednessPolicy
                )
                val averageX = landmarks.map { it.x() }.average().toFloat()
                when (anatomicalSide) {
                    AnatomicalHandSide.LEFT -> {
                        leftHand = landmarks.toLandmarkPoints()
                        observations.add(handObservation("left", reportedLabel, averageX))
                    }
                    AnatomicalHandSide.RIGHT -> {
                        rightHand = landmarks.toLandmarkPoints()
                        observations.add(handObservation("right", reportedLabel, averageX))
                    }
                    null -> if (reportedHandednessPolicy == ReportedHandednessPolicy.DIRECT_REPORTED_SIDES) {
                        // Retain the legacy image-X fallback only for existing mirrored profiles.
                        if (averageX < 0.5f && leftHand == null) {
                            leftHand = landmarks.toLandmarkPoints()
                            observations.add(handObservation("left", "unknown", averageX))
                        } else if (rightHand == null) {
                            rightHand = landmarks.toLandmarkPoints()
                            observations.add(handObservation("right", "unknown", averageX))
                        }
                    } else {
                        // Standard FullSign225 fails closed instead of guessing anatomy.
                        observations.add(handObservation("unassigned", "unknown", averageX))
                    }
                }
            }

            LandmarkFrame(
                poseLandmarks = poseLandmarks,
                leftHandLandmarks = leftHand,
                rightHandLandmarks = rightHand,
                timestampMs = timestampMs,
                handObservations = observations,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height
            )
        } finally {
            val finishedNanos = SystemClock.elapsedRealtimeNanos()
            onMetrics(
                LandmarkExtractionMetrics(
                    sourceTimestampNanos = sourceTimestampNanos,
                    acquisitionAgeMs = acquisitionAgeMs,
                    conversionMs = (convertedNanos - startedNanos) / NANOS_PER_MS,
                    handLandmarkerMs = (handFinishedNanos - convertedNanos) / NANOS_PER_MS,
                    poseLandmarkerMs = (poseFinishedNanos - handFinishedNanos) / NANOS_PER_MS,
                    totalMs = (finishedNanos - startedNanos) / NANOS_PER_MS
                )
            )
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
        val resolvedAnatomy = when (slot) {
            "left" -> "anatomical_left_resolved"
            "right" -> "anatomical_right_resolved"
            else -> "anatomy_unassigned"
        }
        return HandObservation(
            slot = slot,
            mediaPipeHandedness = label.ifBlank { "unknown" },
            averageX = averageX,
            physicalSideEstimate =
                "$resolvedAnatomy/policy=$reportedHandednessPolicy/analysis_mirrored=$mirrorCameraFrame"
        )
    }

    companion object {
        private const val TAG = "VoxGestLandmarks"
        private const val HAND_LANDMARKER_ASSET = "model/hand_landmarker.task"
        private const val POSE_LANDMARKER_ASSET = "model/pose_landmarker_lite.task"
        private const val NANOS_PER_MS = 1_000_000.0
        private const val MAX_REASONABLE_ACQUISITION_AGE_MS = 60_000.0
    }
}
