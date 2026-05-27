package com.voxgest.dryrun

object OneHandCalibrationConfig {
    const val ENABLE_ONEHAND_CALIBRATION_RECORDING: Boolean = true
    const val USE_ANDROID_CALIBRATED_ONEHAND_MODEL: Boolean = true
    const val ORIGINAL_PROFILE_ID: String = "onehand162_phrase_v1"
    const val CALIBRATED_PROFILE_ID: String = "onehand162_android_calibrated_v1"

    val CALIBRATION_LABELS: List<String> = listOf("MY", "WHAT", "YOUR", "NAME", "NOTHING")
    val MODEL_LABELS: List<String> = listOf("WHAT", "YOUR", "NAME", "MY", "NOTHING")
}
