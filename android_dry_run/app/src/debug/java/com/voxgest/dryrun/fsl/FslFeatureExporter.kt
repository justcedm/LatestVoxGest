package com.voxgest.dryrun.fsl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/** Writes complete canonical probe windows to app-internal storage. */
class FslFeatureExporter(
    context: Context,
    rootDirectoryName: String = DEFAULT_ROOT_DIRECTORY
) {
    private val rootDirectory = File(
        context.applicationContext.filesDir,
        safeFileToken(rootDirectoryName, DEFAULT_ROOT_DIRECTORY)
    )

    init {
        requireSafeProbeText("root directory name", rootDirectoryName)
    }

    fun pathsForSession(sessionId: String): FslProbePaths {
        requireSafeProbeText("session id", sessionId)
        val sessionDirectory = File(rootDirectory, safeFileToken(sessionId, "session"))
        return FslProbePaths(
            sessionDirectoryPath = sessionDirectory.absolutePath,
            csvPath = File(sessionDirectory, CSV_FILE_NAME).absolutePath,
            jsonlPath = File(sessionDirectory, JSONL_FILE_NAME).absolutePath
        )
    }

    /**
     * Writes a detailed, self-contained JSON artifact for one 20x162 window.
     *
     * The returned relative path is relative to the session directory and is safe to place in
     * the CSV/JSONL journal. The write uses a temporary file plus rename so a partial window is
     * never presented as a completed export.
     */
    fun exportWindow(
        attempt: FslProbeAttempt,
        attemptForExpected: Int,
        attemptId: String
    ): FslFeatureExportResult {
        require(attemptForExpected > 0) { "Attempt number must be positive" }
        requireSafeProbeText("attempt id", attemptId)

        val paths = pathsForSession(attempt.metadata.sessionId)
        val sessionDirectory = File(paths.sessionDirectoryPath)
        val expectedDirectory = File(
            File(sessionDirectory, WINDOWS_DIRECTORY_NAME),
            safeFileToken(attempt.metadata.expected, "expected")
        )
        ensureDirectory(expectedDirectory)

        val expectedToken = safeFileToken(attempt.metadata.expected, "expected")
        val fileName = String.format(
            Locale.US,
            "attempt_%03d_%s_%d.json",
            attemptForExpected,
            expectedToken,
            attempt.metadata.timestampMs
        )
        val destination = File(expectedDirectory, fileName)
        val document = attempt.toDetailedJson(attemptForExpected, attemptId).toString(2)
        writeAtomically(destination, document)

        val relativePath = destination.relativeTo(sessionDirectory).invariantSeparatorsPath
        return FslFeatureExportResult(destination.absolutePath, relativePath)
    }

    internal fun ensureSessionDirectory(paths: FslProbePaths): File {
        val directory = File(paths.sessionDirectoryPath)
        ensureDirectory(directory)
        return directory
    }

    private fun FslProbeAttempt.toDetailedJson(
        attemptForExpected: Int,
        attemptId: String
    ): JSONObject {
        val frames = JSONArray()
        window.rawFrames.forEach { frame ->
            frames.put(
                JSONObject()
                    .put("timestamp_ms", frame.timestampMs)
                    .put("pose_landmarks", frame.poseLandmarks.toLandmarkArrayOrNull())
                    .put("right_hand_landmarks", frame.rightHandLandmarks.toLandmarkArrayOrNull())
            )
        }

        val features = JSONArray()
        window.canonicalFeatures.forEach { featureFrame ->
            val values = JSONArray()
            featureFrame.forEach { value -> values.put(value.toDouble()) }
            features.put(values)
        }

        return JSONObject()
            .put("schema_version", WINDOW_SCHEMA_VERSION)
            .put("attempt_id", attemptId)
            .put("attempt_for_expected", attemptForExpected)
            .put("metadata", metadataToJson(metadata))
            .put(
                "feature_contract",
                JSONObject()
                    .put("pose_slots", JSONArray(listOf(0, 99)))
                    .put("selected_hand_slots", JSONArray(listOf(99, 162)))
                    .put("pose_landmark_indices", "0..32")
                    .put("selected_hand_landmark_indices", "0..20")
                    .put("selected_hand_policy", "fixed_anatomical_right_mediapipe_tasks_slot")
                    .put("coordinate_reference", "pose_nose_landmark_0")
                    .put("hand_scale", "wrist_0_to_middle_mcp_9_if_gt_0.001")
                    .put("z_damping", 0.3)
                    .put("missing_hand", "63_zero_slots")
                    .put("missing_pose", "entire_162_frame_zero")
            )
            .put(
                "window",
                JSONObject()
                    .put("started_at_ms", window.startedAtMs)
                    .put("ended_at_ms", window.endedAtMs)
                    .put("pose_frame_count", window.poseFrameCount)
                    .put("right_hand_frame_count", window.rightHandFrameCount)
                    .put("raw_frames", frames)
                    .put("canonical_features", features)
            )
    }

    private fun List<FslProbeLandmark>?.toLandmarkArrayOrNull(): Any {
        if (this == null) return JSONObject.NULL
        val landmarks = JSONArray()
        forEach { point ->
            landmarks.put(
                JSONObject()
                    .put("x", point.x.toDouble())
                    .put("y", point.y.toDouble())
                    .put("z", point.z.toDouble())
            )
        }
        return landmarks
    }

    private fun writeAtomically(destination: File, text: String) {
        ensureDirectory(destination.parentFile ?: error("Window export has no parent directory"))
        val temporary = File.createTempFile(".fsl_probe_", ".tmp", destination.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw IllegalStateException("Could not finalize FSL feature export: ${destination.absolutePath}")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create app-internal probe directory: ${directory.absolutePath}")
        }
        require(directory.isDirectory) { "Probe path is not a directory: ${directory.absolutePath}" }
    }

    companion object {
        internal const val CSV_FILE_NAME = "probe_attempts.csv"
        internal const val JSONL_FILE_NAME = "probe_attempts.jsonl"
        private const val DEFAULT_ROOT_DIRECTORY = "fsl_probe"
        private const val WINDOWS_DIRECTORY_NAME = "windows"
        private const val WINDOW_SCHEMA_VERSION = "voxgest_fsl_probe_window_v1"

        internal fun safeFileToken(value: String, fallback: String): String {
            val trimmed = value.trim()
            val stem = trimmed
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('.', '_', '-')
                .take(MAX_TOKEN_STEM_LENGTH)
                .ifBlank { fallback }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(trimmed.toByteArray(Charsets.UTF_8))
                .take(TOKEN_HASH_BYTES)
                .joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
            return "${stem}_$digest"
        }

        internal fun metadataToJson(metadata: FslProbeMetadata): JSONObject {
            return JSONObject()
                .put("feature_version", metadata.featureVersion)
                .put("shape", JSONArray(metadata.shape))
                .put("dtype", metadata.dtype)
                .put("expected", metadata.expected)
                .put("predicted", metadata.predicted)
                .put("top1", metadata.top1.toJson())
                .put("top2", metadata.top2.toJson())
                .put("margin", metadata.margin.toDouble())
                .put("latency_ms", metadata.latencyMs.toDouble())
                .put("accepted", metadata.accepted)
                .put("state", metadata.state)
                .put("timestamp_ms", metadata.timestampMs)
                .put("timestamp_iso_utc", Instant.ofEpochMilli(metadata.timestampMs).toString())
                .put("signer_id", metadata.signerId)
                .put("session_id", metadata.sessionId)
                .put("device_id", metadata.deviceId)
                .put("model_id", metadata.modelId)
                .put("profile_id", metadata.profileId)
        }

        private fun FslProbeRank.toJson(): JSONObject {
            return JSONObject()
                .put("label", label)
                .put("score", score.toDouble())
        }

        private const val MAX_TOKEN_STEM_LENGTH = 48
        private const val TOKEN_HASH_BYTES = 4
    }
}
