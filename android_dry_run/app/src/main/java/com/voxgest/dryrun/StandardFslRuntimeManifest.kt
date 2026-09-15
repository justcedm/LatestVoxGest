package com.voxgest.dryrun

import android.content.Context
import org.json.JSONObject

data class StandardFslRuntimeConfig(
    val profileId: String,
    val featureVersion: String,
    val sequenceLength: Int,
    val featureSize: Int,
    val classCount: Int,
    val inputShape: IntArray,
    val outputShape: IntArray,
    val modelAsset: String,
    val labelsAsset: String,
    val labels: List<String>,
    val modelInputOrientation: String,
    val rejectionRequired: Boolean
) {
    val runtimeBanner: String
        get() = "$profileId | ${Fsl105LiveSegmentProfile.ID} | " +
            "${sequenceLength}x$featureSize | $classCount classes"
}

/** The single parser for Standard runtime dimensions, files, and ordered labels. */
object StandardFslRuntimeManifest {
    fun load(context: Context): StandardFslRuntimeConfig {
        val root = GradingRecognitionProfiles.STANDARD_ASSET_ROOT
        val manifestAsset = "$root/runtime_manifest.json"
        val manifestText = context.assets.open(manifestAsset).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val manifest = JSONObject(manifestText)
        val labelsFilename = manifest.getString("labels_filename")
        val labelsAsset = "$root/$labelsFilename"
        val labelsText = context.assets.open(labelsAsset).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return parse(manifestText, labelsText)
    }

    fun parse(manifestText: String, labelsText: String): StandardFslRuntimeConfig {
        val root = GradingRecognitionProfiles.STANDARD_ASSET_ROOT
        val manifest = JSONObject(manifestText)
        val labelsJson = JSONObject(labelsText)
        val sequenceLength = manifest.getInt("sequence_length")
        val featureSize = manifest.getInt("feature_size")
        val classCount = manifest.getInt("class_count")
        val inputShape = manifest.getJSONArray("input_shape").toIntArray()
        val outputShape = manifest.getJSONArray("output_shape").toIntArray()
        val labels = labelsJson.getJSONArray("labels").toStringList()
        val classOrder = manifest.getJSONArray("class_order").toStringList()

        require(manifest.getString("profile_id") == "STANDARD_FSL_FULLSIGN225")
        require(manifest.getString("active_profile") == "STANDARD_FSL_FULLSIGN225")
        require(sequenceLength == 20)
        require(featureSize == 225)
        require(classCount == 105)
        require(manifest.getInt("num_classes") == classCount)
        require(inputShape.contentEquals(intArrayOf(1, sequenceLength, featureSize)))
        require(outputShape.contentEquals(intArrayOf(1, classCount)))
        require(labelsJson.getInt("class_count") == classCount)
        require(labelsJson.getString("feature_version") == manifest.getString("feature_version"))
        require(labels.size == classCount && labels.distinct().size == classCount)
        require(labels.none(String::isBlank))
        require(classOrder == labels)
        require(manifest.getString("input_dtype") == "float32")
        require(manifest.getString("output_dtype") == "float32")
        require(manifest.getString("model_input_orientation") == "unmirrored")
        require(manifest.getBoolean("rejection_required"))
        require(!manifest.getBoolean("negative_or_nothing_class"))

        return StandardFslRuntimeConfig(
            profileId = manifest.getString("profile_id"),
            featureVersion = manifest.getString("feature_version"),
            sequenceLength = sequenceLength,
            featureSize = featureSize,
            classCount = classCount,
            inputShape = inputShape,
            outputShape = outputShape,
            modelAsset = "$root/${manifest.getString("model_filename")}",
            labelsAsset = "$root/${manifest.getString("labels_filename")}",
            labels = labels,
            modelInputOrientation = manifest.getString("model_input_orientation"),
            rejectionRequired = manifest.getBoolean("rejection_required")
        )
    }

    private fun org.json.JSONArray.toIntArray(): IntArray =
        IntArray(length()) { getInt(it) }

    private fun org.json.JSONArray.toStringList(): List<String> =
        List(length()) { getString(it) }
}
