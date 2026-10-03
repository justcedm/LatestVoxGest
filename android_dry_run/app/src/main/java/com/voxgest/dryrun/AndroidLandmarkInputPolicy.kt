/**
 * @file AndroidLandmarkInputPolicy.kt
 * @description Android camera landmark parity switches for the VoxGest runtime recognizer.
 * @author VoxGest Team
 * @version 1.0.0
 */
package com.voxgest.dryrun

object AndroidLandmarkInputPolicy {
    const val MIRROR_FRONT_CAMERA_FOR_TRAINING_PARITY: Boolean = true
    const val USE_PROFILE_HANDEDNESS_SWAP: Boolean = true
    const val USE_VELOCITY_DELTA_FEATURES: Boolean = false

    fun shouldMirrorFrameBeforeLandmarkExtraction(profile: RecognitionProfile): Boolean {
        return MIRROR_FRONT_CAMERA_FOR_TRAINING_PARITY && profile.mirroredInput
    }

    fun runtimeFeatureSize(profile: RecognitionProfile): Int {
        return if (profile.featureProfile == "onehand162" && USE_VELOCITY_DELTA_FEATURES) {
            LandmarkSequenceBuffer.FULLSIGN_WITH_DELTA_FEATURE_SIZE
        } else {
            profile.featureSize
        }
    }

    fun describe(profile: RecognitionProfile): String {
        val cameraMirror = shouldMirrorFrameBeforeLandmarkExtraction(profile)
        return "profile=${profile.id}, feature=${profile.featureProfile}, cameraMirror=$cameraMirror, handednessSwap=${profile.mirroredInput}, velocityDelta=$USE_VELOCITY_DELTA_FEATURES, runtimeFeatureSize=${runtimeFeatureSize(profile)}"
    }
}
