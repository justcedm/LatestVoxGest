package com.voxgest.dryrun

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class RecognitionProfile(
    val id: String,
    val featureProfile: String,
    val modelAsset: String,
    val labelsAsset: String,
    val manifestAsset: String,
    val inputShape: IntArray,
    val labels: List<String>,
    val sequenceLength: Int,
    val featureSize: Int,
    val mirroredInput: Boolean,
    val dominantHand: String,
    val singleHandPose: Boolean
) {
    fun shapeText(): String = inputShape.joinToString(prefix = "[", postfix = "]")

    override fun equals(other: Any?): Boolean {
        return other is RecognitionProfile &&
            id == other.id &&
            featureProfile == other.featureProfile &&
            inputShape.contentEquals(other.inputShape)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + featureProfile.hashCode()
        result = 31 * result + inputShape.contentHashCode()
        return result
    }

    companion object {
        const val ACTIVE_RECOGNITION_PROFILE = "onehand162_phrase_v1"

        fun loadDefault(context: Context): RecognitionProfile {
            return load(context, ACTIVE_RECOGNITION_PROFILE)
        }

        fun load(context: Context, profileId: String): RecognitionProfile {
            val manifestAsset = when (profileId) {
                "fullsign225_phrase_v1" -> "model/runtime_manifest_fullsign225_phrase_v1.json"
                else -> "model/runtime_manifest_onehand162_phrase_v1.json"
            }
            val manifestText = context.assets.open(manifestAsset).bufferedReader().use { it.readText() }
            val manifest = JSONObject(manifestText)
            val model = manifest.getJSONObject("models").getJSONObject("tcn")
            val featureProfile = manifest.optString("feature_profile", defaultFeatureProfile(profileId))
            val shapeArray = model.getJSONArray("input_shape")
            val inputShape = IntArray(shapeArray.length()) { index -> shapeArray.getInt(index) }
            val labels = parseLabels(model.optJSONArray("labels"))
            val sequenceLength = model.optInt("sequence_length", inputShape.getOrNull(1) ?: 30)
            val featureSize = model.optInt("feature_size", inputShape.getOrNull(2) ?: defaultFeatureSize(featureProfile))
            val normalizedProfile = profileId.lowercase(Locale.US)
            return RecognitionProfile(
                id = profileId,
                featureProfile = featureProfile,
                modelAsset = model.optString("file", defaultModelAsset(normalizedProfile)),
                labelsAsset = model.optString("labels_file", defaultLabelsAsset(normalizedProfile)),
                manifestAsset = manifestAsset,
                inputShape = inputShape,
                labels = labels,
                sequenceLength = sequenceLength,
                featureSize = featureSize,
                mirroredInput = manifest.optBoolean("mirrored_input", true),
                dominantHand = manifest.optString("dominant_hand", if (featureProfile == "onehand162") "right" else "auto"),
                singleHandPose = manifest.optBoolean("single_hand_pose", featureProfile == "onehand162")
            )
        }

        private fun parseLabels(array: org.json.JSONArray?): List<String> {
            if (array == null) return emptyList()
            return List(array.length()) { index -> array.getString(index) }
        }

        private fun defaultFeatureProfile(profileId: String): String {
            return if (profileId == "fullsign225_phrase_v1") "fullsign225" else "onehand162"
        }

        private fun defaultFeatureSize(featureProfile: String): Int {
            return if (featureProfile == "fullsign225") 225 else 162
        }

        private fun defaultModelAsset(profileId: String): String {
            return if (profileId == "fullsign225_phrase_v1") {
                "model/voxgest_tcn_fullsign225_phrase_v1.tflite"
            } else {
                "model/voxgest_tcn_onehand162_phrase_v1.tflite"
            }
        }

        private fun defaultLabelsAsset(profileId: String): String {
            return if (profileId == "fullsign225_phrase_v1") {
                "model/class_labels_tcn_fullsign225_phrase_v1.json"
            } else {
                "model/class_labels_tcn_onehand162_phrase_v1.json"
            }
        }
    }
}
