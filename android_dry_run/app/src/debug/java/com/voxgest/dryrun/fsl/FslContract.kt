package com.voxgest.dryrun.fsl

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class FslRuntimeContract(
    val profileId: String,
    val featureVersion: String,
    val labels: List<String>,
    val androidTokens: List<String>,
    val confidenceThreshold: Float,
    val marginThreshold: Float,
    val cooldownFrames: Int,
    val deploymentEligible: Boolean,
    val modelSha256: String,
    val labelsSha256: String,
    val manifestSha256: String
)

object FslContract {
    const val PROFILE_ID = "fsl_onehand162_20f_rdtcn_v2"
    const val FEATURE_VERSION = "onehand162_20f_nose_mcp_z03_v2"
    const val ASSET_ROOT = "model/fsl_onehand162_20f_rdtcn_v2"
    const val MODEL_ASSET = "$ASSET_ROOT/voxgest_fsl_rdtcn_v2_float16.tflite"
    const val LABELS_ASSET = "$ASSET_ROOT/class_labels_fsl_v2.json"
    const val MANIFEST_ASSET = "$ASSET_ROOT/runtime_manifest.json"
    const val GOLDEN_WINDOW_ASSET = "$ASSET_ROOT/golden_window_f32.bin"
    const val GOLDEN_EXPECTED_ASSET = "$ASSET_ROOT/golden_expected.json"
    const val GOLDEN_FEATURE_ASSET = "$ASSET_ROOT/golden_feature_fixture_f32.bin"
    const val GOLDEN_FEATURE_METADATA_ASSET = "$ASSET_ROOT/golden_feature_fixture.json"
    const val SEQUENCE_LENGTH = 20
    const val FEATURE_SIZE = 162
    const val CLASS_COUNT = 64
    const val SELECTED_HAND = "right"
    const val MIN_WRIST_MCP_SCALE = 0.001f
    const val Z_DAMPING = 0.3f
    val INPUT_SHAPE = intArrayOf(1, SEQUENCE_LENGTH, FEATURE_SIZE)
    val OUTPUT_SHAPE = intArrayOf(1, CLASS_COUNT)

    fun load(context: Context): FslRuntimeContract {
        val manifestText = readText(context, MANIFEST_ASSET)
        val manifest = JSONObject(manifestText)
        require(manifest.getString("feature_version") == FEATURE_VERSION) {
            "Feature version mismatch"
        }
        require(manifest.getInt("sequence_length") == SEQUENCE_LENGTH)
        require(manifest.getInt("feature_size") == FEATURE_SIZE)
        require(manifest.getInt("num_classes") == CLASS_COUNT)
        require(manifest.getString("selected_hand").lowercase(Locale.US) == SELECTED_HAND)
        require(jsonIntArray(manifest, "input_shape").contentEquals(INPUT_SHAPE))
        require(jsonIntArray(manifest, "output_shape").contentEquals(OUTPUT_SHAPE))
        require(manifest.getString("input_dtype") == "float32")
        require(manifest.getString("output_dtype") == "float32")
        require(manifest.getString("rdtcn_model_filename") == MODEL_ASSET.substringAfterLast('/'))

        val classOrderJson = manifest.getJSONArray("class_order")
        val classOrder = List(classOrderJson.length()) { classOrderJson.getString(it) }
        require(classOrder.size == CLASS_COUNT)

        val labelObject = JSONObject(readText(context, LABELS_ASSET))
        val tokenByIndex = MutableList(labelObject.length()) { "" }
        val keys = labelObject.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val index = labelObject.getInt(token)
            require(index in tokenByIndex.indices) { "Invalid label index $index" }
            require(tokenByIndex[index].isEmpty()) { "Duplicate label index $index" }
            tokenByIndex[index] = token
        }
        require(tokenByIndex.size == CLASS_COUNT && tokenByIndex.none { it.isEmpty() })
        classOrder.forEachIndexed { index, displayLabel ->
            require(androidToken(displayLabel) == tokenByIndex[index]) {
                "Label order mismatch at $index: $displayLabel != ${tokenByIndex[index]}"
            }
        }

        val expectedModelHash = manifest
            .getJSONObject("artifact_sha256")
            .getString("rdtcn_float16")
            .lowercase(Locale.US)
        val actualModelHash = sha256Asset(context, MODEL_ASSET)
        require(actualModelHash == expectedModelHash) {
            "FSL model SHA-256 mismatch"
        }

        return FslRuntimeContract(
            profileId = PROFILE_ID,
            featureVersion = FEATURE_VERSION,
            labels = classOrder,
            androidTokens = tokenByIndex,
            confidenceThreshold = manifest.getDouble("confidence_threshold").toFloat(),
            marginThreshold = manifest.getDouble("margin_threshold").toFloat(),
            cooldownFrames = manifest.getInt("cooldown_frames"),
            deploymentEligible = manifest.optBoolean("deployment_eligible", false),
            modelSha256 = actualModelHash,
            labelsSha256 = sha256Asset(context, LABELS_ASSET),
            manifestSha256 = sha256Asset(context, MANIFEST_ASSET)
        )
    }

    fun androidToken(label: String): String {
        val decomposed = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .uppercase(Locale.US)
            .replace("'", "")
        return decomposed.replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    }

    fun sha256Asset(context: Context, path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(path).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readText(context: Context, path: String): String {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun jsonIntArray(json: JSONObject, key: String): IntArray {
        val array = json.getJSONArray(key)
        return IntArray(array.length()) { array.getInt(it) }
    }
}
