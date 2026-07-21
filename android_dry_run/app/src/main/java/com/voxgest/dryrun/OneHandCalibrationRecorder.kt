package com.voxgest.dryrun

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class OneHandCalibrationSample(
    val label: String,
    val exportMode: CalibrationExportMode = CalibrationExportMode.ASL,
    val signerId: String = "",
    val deviceSessionTag: String = "",
    val timestamp: Long,
    val deviceModel: String,
    val activeProfile: String,
    val featureProfile: String,
    val inputShape: IntArray,
    val sequenceLengthAtExport: Int,
    val dominantHand: String,
    val mirroredInput: Boolean,
    val selectedHandSlot: String,
    val handPresenceRatio: Float,
    val missingPoseCount: Int,
    val missingHandCount: Int,
    val motionScore: Float,
    val wristPath: Float,
    val featureArray: Array<FloatArray>
)

data class OneHandCalibrationSaveResult(
    val fileName: String,
    val relativePath: String,
    val counter: Int
)

class OneHandCalibrationRecorder(private val context: Context) {
    private val appContext = context.applicationContext
    private val counter = AtomicInteger(0)

    fun save(sample: OneHandCalibrationSample): OneHandCalibrationSaveResult {
        val label = sample.label.uppercase(Locale.US)
        val count = counter.incrementAndGet()
        val timestampText = FILE_STAMP.format(Date(sample.timestamp))
        val deviceName = sanitizeDeviceName(sample.deviceModel)
        val fileName = "${label}_${deviceName}_${timestampText}_${count}.json"
        val calibrationFolder = calibrationFolderFor(sample.exportMode)
        val relativePath = "Download/$calibrationFolder/$label"
        val body = sample.toJson().toString(2)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed for $relativePath/$fileName")
            appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open MediaStore stream for $fileName")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$calibrationFolder/$label")
            dir.mkdirs()
            File(dir, fileName).writeText(body, Charsets.UTF_8)
        }

        Log.i(TAG, "calibration_saved path=/sdcard/$relativePath/$fileName label=$label mode=${sample.exportMode} frames=${sample.featureArray.size}")
        return OneHandCalibrationSaveResult(fileName, relativePath, count)
    }

    private fun OneHandCalibrationSample.toJson(): JSONObject {
        val body = JSONObject()
            .put("label", label)
            .put("timestamp", timestamp)
            .put("timestamp_iso", ISO_STAMP.format(Date(timestamp)))
            .put("device_model", deviceModel)
            .put("active_profile", activeProfile)
            .put("feature_profile", featureProfile)
            .put("input_shape", JSONArray(inputShape.toList()))
            .put("sequence_length_at_export", sequenceLengthAtExport)
            .put("dominant_hand", dominantHand)
            .put("mirrored_input", mirroredInput)
            .put("selected_hand_slot", selectedHandSlot)
            .put("hand_presence_ratio", handPresenceRatio)
            .put("missing_pose_count", missingPoseCount)
            .put("missing_hand_count", missingHandCount)
            .put("motion_score", motionScore)
            .put("wrist_path", wristPath)
            .put("feature_array", featureArray.toJsonArray())
        if (exportMode == CalibrationExportMode.FSL) {
            body
                .put("signer_id", signerId)
                .put("device_session_tag", deviceSessionTag)
                .put("fsl_mode", true)
        }
        return body
    }

    private fun Array<FloatArray>.toJsonArray(): JSONArray {
        val outer = JSONArray()
        forEach { frame ->
            val inner = JSONArray()
            frame.forEach { value -> inner.put(value.toDouble()) }
            outer.put(inner)
        }
        return outer
    }

    private fun sanitizeDeviceName(value: String): String {
        val clean = value.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return clean.ifBlank { "ANDROID" }
    }

    private fun calibrationFolderFor(exportMode: CalibrationExportMode): String {
        return when (exportMode) {
            CalibrationExportMode.ASL -> ASL_CALIBRATION_FOLDER
            CalibrationExportMode.FSL -> FSL_CALIBRATION_FOLDER
        }
    }

    companion object {
        private const val TAG = "VoxGestRecognition"
        private const val ASL_CALIBRATION_FOLDER = "VoxGestCalibration/onehand162_phrase_v1"
        private const val FSL_CALIBRATION_FOLDER = "VoxGestCalibration/fsl_phrase_v1"
        private val FILE_STAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        private val ISO_STAMP = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    }
}
