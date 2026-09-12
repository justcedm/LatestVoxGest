package com.voxgest.dryrun

/**
 * Profile identities used by the September grading path.
 *
 * These are deliberately separate from the legacy demo10 and *_v1 phrase
 * profiles. Declaring the target slot here does not activate it: the standard
 * profile remains blocked until its real 105-class artifact bundle exists and
 * passes the numeric Android parity harness.
 */
enum class GradingProfileId {
    STANDARD_FSL_FULLSIGN225,
    ACCESSIBLE_ONEHAND162
}

enum class ModelInputOrientation {
    UNMIRRORED,
    LEGACY_MIRRORED_FOR_TRAINING_PARITY
}

data class GradingProfileSpec(
    val id: GradingProfileId,
    val featureVersion: String,
    val modelAsset: String,
    val labelsAsset: String,
    val manifestAsset: String,
    val inputShape: IntArray,
    val outputShape: IntArray,
    val modelInputOrientation: ModelInputOrientation,
    val previewMirrorAllowed: Boolean,
    val requiresNumericParityBeforeActivation: Boolean
) {
    val sequenceLength: Int get() = inputShape[1]
    val featureSize: Int get() = inputShape[2]
    val classCount: Int get() = outputShape[1]
}

object GradingRecognitionProfiles {
    const val STANDARD_ASSET_ROOT = "model/fsl_fullsign225_20f_105_v1"
    const val ACCESSIBLE_ASSET_ROOT = "model/fsl_onehand162_20f_rdtcn_v2"

    val STANDARD_FSL_FULLSIGN225 = GradingProfileSpec(
        id = GradingProfileId.STANDARD_FSL_FULLSIGN225,
        featureVersion = StandardFullSign225Contract.FEATURE_VERSION,
        modelAsset = "$STANDARD_ASSET_ROOT/voxgest_fsl_fullsign225_105_float32.tflite",
        labelsAsset = "$STANDARD_ASSET_ROOT/class_labels_fsl105_fullsign225_v1.json",
        manifestAsset = "$STANDARD_ASSET_ROOT/runtime_manifest.json",
        inputShape = intArrayOf(1, 20, 225),
        outputShape = intArrayOf(1, 105),
        modelInputOrientation = ModelInputOrientation.UNMIRRORED,
        previewMirrorAllowed = true,
        requiresNumericParityBeforeActivation = true
    )

    /** Authoritative runtime for the normal Sign screen. Legacy Demo is explicit opt-in only. */
    val NORMAL_SIGN_PROFILE: GradingProfileSpec = STANDARD_FSL_FULLSIGN225

    val ACCESSIBLE_ONEHAND162 = GradingProfileSpec(
        id = GradingProfileId.ACCESSIBLE_ONEHAND162,
        featureVersion = "onehand162_20f_nose_mcp_z03_v2",
        modelAsset = "$ACCESSIBLE_ASSET_ROOT/voxgest_fsl_rdtcn_v2_float16.tflite",
        labelsAsset = "$ACCESSIBLE_ASSET_ROOT/class_labels_fsl_v2.json",
        manifestAsset = "$ACCESSIBLE_ASSET_ROOT/runtime_manifest.json",
        inputShape = intArrayOf(1, 20, 162),
        outputShape = intArrayOf(1, 64),
        // The 64-class extraction reads the original video frame without a
        // horizontal flip and selects the signer's anatomical right hand.
        modelInputOrientation = ModelInputOrientation.UNMIRRORED,
        previewMirrorAllowed = true,
        requiresNumericParityBeforeActivation = true
    )

    /** Experimental FullSign lane. This declaration never makes it the launcher default. */
    val TARGET_GRADING_PROFILE: GradingProfileSpec = STANDARD_FSL_FULLSIGN225

    /** Existing 64-class diagnostic lane; its assets and runtime are not replaced. */
    val EMERGENCY_FALLBACK_PROFILE: GradingProfileSpec = ACCESSIBLE_ONEHAND162

    fun get(id: GradingProfileId): GradingProfileSpec = when (id) {
        GradingProfileId.STANDARD_FSL_FULLSIGN225 -> STANDARD_FSL_FULLSIGN225
        GradingProfileId.ACCESSIBLE_ONEHAND162 -> ACCESSIBLE_ONEHAND162
    }
}

enum class GradingCameraLens {
    FRONT,
    BACK
}

object GradingCameraPolicy {
    val DEFAULT_LENS: GradingCameraLens = GradingCameraLens.FRONT

    data class FramePolicy(
        val previewMirrorAllowed: Boolean,
        val analysisMirrorHorizontally: Boolean,
        val handednessPolicy: ReportedHandednessPolicy
    )

    fun mirrorModelInput(profile: GradingProfileSpec, lens: GradingCameraLens): Boolean {
        if (lens == GradingCameraLens.BACK) return false
        return profile.modelInputOrientation == ModelInputOrientation.LEGACY_MIRRORED_FOR_TRAINING_PARITY
    }

    fun handednessPolicy(profile: GradingProfileSpec): ReportedHandednessPolicy {
        return when (profile.modelInputOrientation) {
            ModelInputOrientation.UNMIRRORED ->
                ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
            ModelInputOrientation.LEGACY_MIRRORED_FOR_TRAINING_PARITY ->
                ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        }
    }

    fun framePolicy(
        profile: GradingProfileSpec,
        lens: GradingCameraLens = DEFAULT_LENS
    ): FramePolicy {
        return FramePolicy(
            previewMirrorAllowed = lens == GradingCameraLens.FRONT && profile.previewMirrorAllowed,
            analysisMirrorHorizontally = mirrorModelInput(profile, lens),
            handednessPolicy = if (lens == GradingCameraLens.FRONT) {
                handednessPolicy(profile)
            } else {
                ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
            }
        )
    }
}
