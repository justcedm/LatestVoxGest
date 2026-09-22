package com.voxgest.dryrun.fullsign

import android.content.Context
import android.content.Intent
import android.os.Build
import com.voxgest.dryrun.GradingRecognitionProfiles
import com.voxgest.dryrun.StandardFullSign225Contract
import com.voxgest.dryrun.StandardFullSign225WindowQuality
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Debug-only evidence writer for a manually armed Standard FullSign225 trial.
 * It never chooses a label, changes a threshold, or touches model assets.
 */
internal class FullSign225LiveTrialRecorder private constructor(
    private val context: Context,
    val request: Request
) {
    data class Request(
        val expectedLabel: String,
        val mode: Mode,
        val sessionId: String,
        val trialId: String,
        val signerId: String?,
        val sourceVideoPath: String?,
        val sourceVideoSha256: String?
    )

    data class Observation(
        val predictedLabel: String,
        val accepted: Boolean,
        val confidence: Float,
        val top2Label: String,
        val top2Confidence: Float,
        val margin: Float,
        val rejectionReason: String?,
        val stableWindows: Int,
        val posePresent: Boolean,
        val leftHandPresent: Boolean,
        val rightHandPresent: Boolean,
        val quality: StandardFullSign225WindowQuality,
        val inferenceLatencyMs: Double,
        val durationMs: Long
    )

    enum class Mode(val outputFilename: String) {
        REFERENCE_VIDEO("LIVE_REFERENCE_VIDEO_RESULTS.json"),
        PERSON("LIVE_PERSON_RESULTS.json");

        companion object {
            fun fromIntent(value: String): Mode = when (value.trim().lowercase(Locale.US)) {
                "reference_video" -> REFERENCE_VIDEO
                "person" -> PERSON
                else -> throw IllegalArgumentException("Unsupported FullSign225 trial mode: $value")
            }
        }
    }

    val outputFile: File by lazy {
        val directory = context.getExternalFilesDir(DIRECTORY_NAME) ?: File(context.filesDir, DIRECTORY_NAME)
        require(directory.exists() || directory.mkdirs()) { "Cannot create ${directory.absolutePath}" }
        File(directory, request.mode.outputFilename)
    }

    fun record(observation: Observation): RecordResult {
        val root = loadOrCreate()
        require(root.getString("session_id") == request.sessionId) {
            "Existing ${outputFile.name} belongs to another session; use a unique session id"
        }
        val trials = root.getJSONArray("trials")
        trials.put(observation.toJson())
        root.put("updated_epoch_ms", System.currentTimeMillis())
        root.put("summary", summary(trials))
        outputFile.writeText(root.toString(2), Charsets.UTF_8)
        return RecordResult(outputFile, trials.length(), root.getJSONObject("summary"))
    }

    private fun loadOrCreate(): JSONObject {
        if (outputFile.isFile) return JSONObject(outputFile.readText(Charsets.UTF_8))
        val manifest = context.assets.open(
            "${GradingRecognitionProfiles.STANDARD_ASSET_ROOT}/runtime_manifest.json"
        ).bufferedReader().use { JSONObject(it.readText()) }
        val artifacts = manifest.getJSONObject("artifacts")
        return JSONObject()
            .put("schema_version", 1)
            .put("status", "IN_PROGRESS")
            .put("test_mode", request.mode.name.lowercase(Locale.US))
            .put("session_id", request.sessionId)
            .put("profile", manifest.getString("active_profile"))
            .put("feature_version", manifest.getString("feature_version"))
            .put("input_shape", JSONArray(listOf(1, 20, 225)))
            .put("input_dtype", "float32")
            .put("class_count", manifest.getInt("class_count"))
            .put("model_filename", artifacts.getString("model"))
            .put("model_sha256", artifacts.getString("model_sha256"))
            .put("model_input_orientation", manifest.getString("model_input_orientation"))
            .put("analysis_mirrored", false)
            .put("hand_slot_policy", manifest.getString("hand_slot_policy"))
            .put("created_epoch_ms", System.currentTimeMillis())
            .put("trials", JSONArray())
    }

    private fun Observation.toJson(): JSONObject {
        val predictionCorrect = predictedLabel == request.expectedLabel
        val acceptedCorrect = accepted && predictionCorrect
        return JSONObject()
            .put("trial_id", request.trialId)
            .put("expected_label", request.expectedLabel)
            .put("predicted_label", predictedLabel)
            .put("accepted", accepted)
            .put("prediction_correct", predictionCorrect)
            .put("accepted_correct", acceptedCorrect)
            .put("confidence", confidence)
            .put("top2_label", top2Label)
            .put("top2_confidence", top2Confidence)
            .put("top1_top2_margin", margin)
            .put("pose_present", posePresent)
            .put("left_hand_present", leftHandPresent)
            .put("right_hand_present", rightHandPresent)
            .put("pose_presence_ratio", quality.posePresenceRatio)
            .put("left_hand_presence_ratio", quality.leftHandPresenceRatio)
            .put("right_hand_presence_ratio", quality.rightHandPresenceRatio)
            .put("any_hand_presence_ratio", quality.anyHandPresenceRatio)
            .put("both_hands_presence_ratio", quality.bothHandsPresenceRatio)
            .put("rejection_reason", rejectionReason ?: JSONObject.NULL)
            .put("stable_windows", stableWindows)
            .put("inference_latency_ms", inferenceLatencyMs)
            .put("trial_duration_ms", durationMs)
            .put("capture_mode", request.mode.name.lowercase(Locale.US))
            .put("signer_id", request.signerId ?: JSONObject.NULL)
            .put("source_video_path", request.sourceVideoPath ?: JSONObject.NULL)
            .put("source_video_sha256", request.sourceVideoSha256 ?: JSONObject.NULL)
            .put("device_model", Build.MODEL ?: "UNKNOWN")
            .put("device_serial", Build.SERIAL ?: "UNKNOWN")
            .put("recorded_epoch_ms", System.currentTimeMillis())
    }

    private fun summary(trials: JSONArray): JSONObject {
        var accepted = 0
        var predictionCorrect = 0
        var acceptedCorrect = 0
        val perClass = JSONObject()
        repeat(trials.length()) { index ->
            val trial = trials.getJSONObject(index)
            val expected = trial.getString("expected_label")
            val current = perClass.optJSONObject(expected) ?: JSONObject().also {
                perClass.put(expected, it)
            }
            current.put("trials", current.optInt("trials") + 1)
            current.put("accepted", current.optInt("accepted") + if (trial.getBoolean("accepted")) 1 else 0)
            current.put(
                "prediction_correct",
                current.optInt("prediction_correct") + if (trial.getBoolean("prediction_correct")) 1 else 0
            )
            current.put(
                "accepted_correct",
                current.optInt("accepted_correct") + if (trial.getBoolean("accepted_correct")) 1 else 0
            )
            if (trial.getBoolean("accepted")) accepted += 1
            if (trial.getBoolean("prediction_correct")) predictionCorrect += 1
            if (trial.getBoolean("accepted_correct")) acceptedCorrect += 1
        }
        val count = trials.length()
        return JSONObject()
            .put("trial_count", count)
            .put("accepted_count", accepted)
            .put("rejected_count", count - accepted)
            .put("prediction_top1_accuracy", ratio(predictionCorrect, count))
            .put("accepted_correct_accuracy", ratio(acceptedCorrect, count))
            .put("per_class", perClass)
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator.toDouble()

    data class RecordResult(
        val file: File,
        val totalTrials: Int,
        val summary: JSONObject
    )

    companion object {
        const val EXTRA_EXPECTED_LABEL = "com.voxgest.dryrun.fullsign.extra.EXPECTED_LABEL"
        const val EXTRA_MODE = "com.voxgest.dryrun.fullsign.extra.MODE"
        const val EXTRA_SESSION_ID = "com.voxgest.dryrun.fullsign.extra.SESSION_ID"
        const val EXTRA_TRIAL_ID = "com.voxgest.dryrun.fullsign.extra.TRIAL_ID"
        const val EXTRA_SIGNER_ID = "com.voxgest.dryrun.fullsign.extra.SIGNER_ID"
        const val EXTRA_SOURCE_VIDEO_PATH = "com.voxgest.dryrun.fullsign.extra.SOURCE_VIDEO_PATH"
        const val EXTRA_SOURCE_VIDEO_SHA256 = "com.voxgest.dryrun.fullsign.extra.SOURCE_VIDEO_SHA256"
        private const val DIRECTORY_NAME = "fullsign225_live_trials"

        fun fromIntent(context: Context, intent: Intent): FullSign225LiveTrialRecorder? {
            val rawExpected = intent.getStringExtra(EXTRA_EXPECTED_LABEL)?.trim().orEmpty()
            if (rawExpected.isBlank()) return null
            val expected = rawExpected.uppercase(Locale.US)
            val mode = Mode.fromIntent(intent.getStringExtra(EXTRA_MODE).orEmpty())
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.trim().orEmpty()
            val trialId = intent.getStringExtra(EXTRA_TRIAL_ID)?.trim().orEmpty()
            require(sessionId.isNotBlank()) { "FullSign225 trial session id is required" }
            require(trialId.isNotBlank()) { "FullSign225 trial id is required" }
            val sourceSha = intent.getStringExtra(EXTRA_SOURCE_VIDEO_SHA256)?.trim()?.lowercase(Locale.US)
            if (mode == Mode.REFERENCE_VIDEO) {
                require(!intent.getStringExtra(EXTRA_SOURCE_VIDEO_PATH).isNullOrBlank()) {
                    "Reference-video trial needs exact source_video_path"
                }
                require(sourceSha?.matches(Regex("[0-9a-f]{64}")) == true) {
                    "Reference-video trial needs exact source_video_sha256"
                }
            }
            return FullSign225LiveTrialRecorder(
                context.applicationContext,
                Request(
                    expectedLabel = expected,
                    mode = mode,
                    sessionId = sessionId,
                    trialId = trialId,
                    signerId = intent.getStringExtra(EXTRA_SIGNER_ID)?.trim()?.takeIf { it.isNotBlank() },
                    sourceVideoPath = intent.getStringExtra(EXTRA_SOURCE_VIDEO_PATH)?.trim()?.takeIf { it.isNotBlank() },
                    sourceVideoSha256 = sourceSha
                )
            )
        }
    }
}
