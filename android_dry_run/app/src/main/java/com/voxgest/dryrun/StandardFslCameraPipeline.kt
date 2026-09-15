package com.voxgest.dryrun

import android.content.Context

/** Factory kept separate from the existing OneHand controller. */
object StandardFslCameraPipeline {
    val profile: GradingProfileSpec = GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225

    fun createLandmarkExtractor(
        context: Context,
        lens: GradingCameraLens = GradingCameraLens.FRONT,
        onMetrics: (LandmarkExtractionMetrics) -> Unit = {},
        handIdentityStabilizer: TemporalAnatomicalHandIdentityStabilizer? = null,
        onHandIdentityDiagnostics: (TemporalHandIdentityDiagnostics) -> Unit = {}
    ): MediaPipeLandmarkExtractor {
        val policy = GradingCameraPolicy.framePolicy(profile, lens)
        check(!policy.analysisMirrorHorizontally) {
            "STANDARD_FSL_FULLSIGN225 analysis must remain unmirrored"
        }
        return MediaPipeLandmarkExtractor(
            context = context,
            mirrorCameraFrame = false,
            reportedHandednessPolicy = policy.handednessPolicy,
            onMetrics = onMetrics,
            handIdentityStabilizer = handIdentityStabilizer,
            onHandIdentityDiagnostics = onHandIdentityDiagnostics
        )
    }
}
