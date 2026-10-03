package com.voxgest.dryrun

import android.content.Context

/**
 * Camera adapter for the gated 64-class OneHand162 diagnostic profile.
 *
 * The model contract is unmirrored. A front-camera preview may be mirrored by
 * the view for user familiarity, but pixels sent to MediaPipe and the model
 * must never be mirrored. MediaPipe's selfie-oriented handedness category is
 * corrected exactly once to recover the signer's anatomical side.
 */
object ExperimentalOneHand162CameraPipeline {
    val profile: GradingProfileSpec = GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162
    val frontCameraPolicy: GradingCameraPolicy.FramePolicy =
        GradingCameraPolicy.framePolicy(profile, GradingCameraLens.FRONT)

    fun createLandmarkExtractor(context: Context): MediaPipeLandmarkExtractor {
        check(profile.featureVersion == "onehand162_20f_nose_mcp_z03_v2")
        check(!frontCameraPolicy.analysisMirrorHorizontally) {
            "64-class OneHand162 analysis must remain unmirrored"
        }
        check(
            frontCameraPolicy.handednessPolicy ==
                ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
        ) { "Unmirrored OneHand162 handedness must be corrected exactly once" }
        return MediaPipeLandmarkExtractor(
            context = context,
            mirrorCameraFrame = false,
            reportedHandednessPolicy = frontCameraPolicy.handednessPolicy
        )
    }
}
