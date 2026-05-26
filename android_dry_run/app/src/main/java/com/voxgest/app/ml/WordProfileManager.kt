package com.voxgest.app.ml

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import com.voxgest.dryrun.TfliteModelLoader

data class WordProfile(
    val id: String,
    val displayName: String,
    val modelAsset: String,
    val labelsAsset: String,
    val inputShape: IntArray,
    val confidenceThreshold: Float,
    val marginThreshold: Float,
    val motionThreshold: Float,
    val wristPathThreshold: Float
) {
    val defaultThreshold: PredictionGate.WordThreshold
        get() = PredictionGate.WordThreshold(
            confidence = confidenceThreshold,
            margin = marginThreshold,
            motion = motionThreshold,
            wristPath = wristPathThreshold
        )
}

object WordProfileManager {
    private const val PREFS = "voxgest_word_profiles"
    private const val KEY_PROFILE_ID = "voxgest_word_profile_id"
    private const val MANIFEST_ASSET = "runtime_manifest_v1.json"

    val PROFILES = listOf(
        WordProfile(
            id = "demo10",
            displayName = "Demo 10 Words",
            modelAsset = "voxgest_lstm_v1.tflite",
            labelsAsset = "class_labels_lstm_v1.json",
            inputShape = intArrayOf(1, 30, 162),
            confidenceThreshold = 0.65f,
            marginThreshold = 0.12f,
            motionThreshold = 0.03f,
            wristPathThreshold = 0.35f
        ),
        WordProfile(
            id = "tcn_demo10",
            displayName = "TCN Demo 10",
            modelAsset = "voxgest_tcn_v1.tflite",
            labelsAsset = "class_labels_tcn_v1.json",
            inputShape = intArrayOf(1, 30, 162),
            confidenceThreshold = 0.65f,
            marginThreshold = 0.12f,
            motionThreshold = 0.03f,
            wristPathThreshold = 0.35f
        )
    )

    private var selectedProfile: WordProfile = PROFILES.first()

    fun availableProfiles(context: Context): List<WordProfile> {
        return PROFILES.filter { profile ->
            assetExists(context, profile.modelAsset) && assetExists(context, profile.labelsAsset)
        }
    }

    fun savedOrDefaultProfile(context: Context): WordProfile {
        val available = availableProfiles(context)
        val savedId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE_ID, null)
        selectedProfile = available.firstOrNull { it.id == savedId }
            ?: available.firstOrNull()
            ?: PROFILES.first()
        return selectedProfile
    }

    fun saveCurrentProfile(context: Context, profile: WordProfile) {
        selectedProfile = profile
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE_ID, profile.id)
            .apply()
    }

    fun loadProfile(context: Context, profile: WordProfile): Interpreter {
        val options = Interpreter.Options().setNumThreads(2)
        val interpreter = TfliteModelLoader(context).loadInterpreterWithOptions(options, profile.modelAsset)
        interpreter.resizeInput(0, profile.inputShape)
        interpreter.allocateTensors()
        val actual = interpreter.getInputTensor(0).shape()
        require(actual.contentEquals(profile.inputShape)) {
            "Input shape ${actual.contentToString()} does not match ${profile.inputShape.contentToString()}"
        }
        selectedProfile = profile
        return interpreter
    }

    fun currentProfile(): WordProfile = selectedProfile

    fun labelMap(context: Context, profile: WordProfile): Map<Int, String> {
        val text = context.assets.open(profile.labelsAsset).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val out = linkedMapOf<Int, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val label = keys.next()
            val index = json.optInt(label, -1)
            if (index >= 0) out[index] = label
        }
        return out
    }

    fun wordCount(context: Context, profile: WordProfile): Int {
        return runCatching { labelMap(context, profile).size }.getOrDefault(0)
    }

    fun thresholds(context: Context, profile: WordProfile): Map<String, PredictionGate.WordThreshold> {
        val parsed = parseManifestThresholds(context)
        if (parsed.isNotEmpty()) return parsed
        return hardcodedThresholds()
    }

    private fun parseManifestThresholds(context: Context): Map<String, PredictionGate.WordThreshold> {
        return runCatching {
            val text = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            val manifest = JSONObject(text)
            val rules = manifest
                .optJSONObject("word_thresholds")
                ?.optJSONObject("words_mode_rules")
                ?: return@runCatching emptyMap()
            val out = linkedMapOf<String, PredictionGate.WordThreshold>()
            val keys = rules.keys()
            while (keys.hasNext()) {
                val label = keys.next()
                val item = rules.optJSONObject(label) ?: continue
                out[label.uppercase()] = PredictionGate.WordThreshold(
                    confidence = item.optDouble("confidence", 0.65).toFloat(),
                    margin = item.optDouble("margin", 0.12).toFloat(),
                    motion = item.optDouble("motion", 0.03).toFloat(),
                    wristPath = item.optDouble("wrist_path", 0.35).toFloat(),
                    stableFrames = item.optInt("stable_frames", 8)
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun hardcodedThresholds(): Map<String, PredictionGate.WordThreshold> {
        return mapOf(
            "YES" to PredictionGate.WordThreshold(0.56f, 0.07f, 0.008f, 0.05f, 5),
            "NO" to PredictionGate.WordThreshold(0.55f, 0.06f, 0.008f, 0.04f, 5),
            "WATER" to PredictionGate.WordThreshold(0.56f, 0.06f, 0.006f, 0.04f, 5),
            "PLEASE" to PredictionGate.WordThreshold(0.68f, 0.18f, 0.025f, 0.25f),
            "HELLO" to PredictionGate.WordThreshold(0.70f, 0.18f, 0.030f, 0.30f),
            "HELP" to PredictionGate.WordThreshold(0.60f, 0.10f, 0.025f, 0.20f),
            "STOP" to PredictionGate.WordThreshold(0.62f, 0.10f, 0.025f, 0.20f),
            "DOCTOR" to PredictionGate.WordThreshold(0.70f, 0.15f, 0.020f, 0.20f),
            "NAME" to PredictionGate.WordThreshold(0.55f, 0.05f, 0.020f, 0.20f),
            "THANKYOU" to PredictionGate.WordThreshold(0.52f, 0.03f, 0.010f, 0.08f, 6),
            "NOTHING" to PredictionGate.WordThreshold(0.55f, 0.05f, 0.0f, 0.0f, 4)
        )
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
