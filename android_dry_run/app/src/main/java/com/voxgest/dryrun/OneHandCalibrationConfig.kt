package com.voxgest.dryrun

enum class CalibrationExportMode {
    ASL,
    FSL
}

object OneHandCalibrationConfig {
    const val ENABLE_ONEHAND_CALIBRATION_RECORDING: Boolean = true
    const val USE_ANDROID_CALIBRATED_ONEHAND_MODEL: Boolean = true
    const val DEBUG_ACCEPT_CALIBRATED_HIGH_CONFIDENCE: Boolean = true
    const val ORIGINAL_PROFILE_ID: String = "onehand162_phrase_v1"
    const val CALIBRATED_PROFILE_ID: String = "onehand162_android_calibrated_v1"

    val CALIBRATION_LABELS: List<String> = listOf("MY", "WHAT", "YOUR", "NAME", "NOTHING")
    val FSL_EXPORT_LABELS: List<String> = listOf(
        "WHAT",
        "YOUR",
        "NAME",
        "MY",
        "NOTHING",
        "HELLO",
        "THANKYOU",
        "WATER",
        "EAT",
        "HELP",
        "STOP",
        "YES",
        "NO",
        "PLEASE",
        "SORRY",
        "DOCTOR",
        "SICK",
        "PAIN",
        "BATHROOM",
        "SLEEP",
        "WHO",
        "WHERE",
        "HOW",
        "WHEN",
        "UNDERSTAND"
    )
    val MODEL_LABELS: List<String> = listOf("WHAT", "YOUR", "NAME", "MY", "NOTHING")
}
