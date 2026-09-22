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

    fun shouldMirrorFrameBeforeLandmarkExtraction(profile: RecognitionProfile): Boolean {
        return MIRROR_FRONT_CAMERA_FOR_TRAINING_PARITY && profile.mirroredInput
    }

    fun describe(profile: RecognitionProfile): String {
        val cameraMirror = shouldMirrorFrameBeforeLandmarkExtraction(profile)
        return "profile=${profile.id}, feature=${profile.featureProfile}, cameraMirror=$cameraMirror, handednessSwap=${profile.mirroredInput}"
    }
}
