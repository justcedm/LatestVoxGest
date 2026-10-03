package com.voxgest.dryrun

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

enum class StandardFslArtifactState {
    BLOCKED,
    READY_FOR_NUMERIC_PARITY
}

data class StandardFslArtifactReadiness(
    val state: StandardFslArtifactState,
    val evidence: String,
    val labels: List<String> = emptyList()
) {
    val canOpenRuntimeForParity: Boolean
        get() = state == StandardFslArtifactState.READY_FOR_NUMERIC_PARITY
}

data class StandardFslArtifactMetadata(
    val activeProfile: String,
    val featureVersion: String,
    val inputShape: IntArray,
    val outputShape: IntArray,
    val classCount: Int,
    val inputDtype: String,
    val outputDtype: String,
    val modelInputOrientation: String,
    val handSlotPolicy: String,
    val hasNegativeOrNothingClass: Boolean,
    val rejectionRequired: Boolean,
    val androidDefaultChanged: Boolean,
    val poseRange: IntArray,
    val leftHandRange: IntArray,
    val rightHandRange: IntArray,
    val normalizationInputOrientation: String,
    val modelFilename: String,
    val labelsFilename: String,
    val modelSha256: String,
    val desktopParityStatus: String,
    val classOrder: List<String>
)

data class StandardFslLabelsMetadata(
    val featureVersion: String,
    val classCount: Int,
    val labels: List<String>
)

/**
 * Fail-closed gate for the future real 105-class bundle. This gate never calls
 * an artifact a parity PASS; it only allows the numeric parity harness to run.
 */
object StandardFslArtifactGate {
    fun inspect(context: Context): StandardFslArtifactReadiness {
        val profile = GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
        val missing = requiredAssetPaths()
            .filterNot { assetExists(context, it) }
        if (missing.isNotEmpty()) {
            return StandardFslArtifactReadiness(
                StandardFslArtifactState.BLOCKED,
                "missing_real_artifacts=${missing.joinToString(",")}"
            )
        }

        return runCatching {
            val manifestText = readText(context, profile.manifestAsset)
            val actualHash = sha256Asset(context, profile.modelAsset)
            val labelsText = readText(context, profile.labelsAsset)
            val labels = validateMetadata(manifestText, labelsText, actualHash)
            val fixtureEvidence = validateManifestArtifacts(context, manifestText)
            val manifestHash = sha256Asset(context, profile.manifestAsset)

            StandardFslArtifactReadiness(
                StandardFslArtifactState.READY_FOR_NUMERIC_PARITY,
                "contract_valid=true runtime_manifest_sha256=$manifestHash model_sha256=$actualHash " +
                    "labels=${labels.size} $fixtureEvidence next=run_numeric_parity",
                labels
            )
        }.getOrElse { error ->
            StandardFslArtifactReadiness(
                StandardFslArtifactState.BLOCKED,
                "contract_error=${error.javaClass.simpleName}:${error.message}"
            )
        }
    }

    fun readLabels(context: Context, asset: String): List<String> {
        return readLabelsPayload(readText(context, asset))
    }

    /** Pure schema validator used by JVM tests and the device artifact gate. */
    fun validateMetadata(
        manifestText: String,
        labelsText: String,
        actualModelSha256: String
    ): List<String> {
        val manifest = JSONObject(manifestText)
        val layout = manifest.getJSONObject("feature_layout")
        val artifacts = manifest.getJSONObject("artifacts")
        val labelsObject = JSONObject(labelsText)
        return validateContract(
            manifest = StandardFslArtifactMetadata(
                activeProfile = manifest.getString("active_profile"),
                featureVersion = manifest.getString("feature_version"),
                inputShape = jsonIntArray(manifest, "input_shape"),
                outputShape = jsonIntArray(manifest, "output_shape"),
                classCount = manifest.getInt("class_count"),
                inputDtype = manifest.getString("input_dtype"),
                outputDtype = manifest.getString("output_dtype"),
                modelInputOrientation = manifest.getString("model_input_orientation"),
                handSlotPolicy = manifest.getString("hand_slot_policy"),
                hasNegativeOrNothingClass = manifest.getBoolean("negative_or_nothing_class"),
                rejectionRequired = manifest.getBoolean("rejection_required"),
                androidDefaultChanged = manifest.getBoolean("android_default_changed"),
                poseRange = jsonIntArray(layout, "pose"),
                leftHandRange = jsonIntArray(layout, "anatomical_left_hand"),
                rightHandRange = jsonIntArray(layout, "anatomical_right_hand"),
                normalizationInputOrientation = manifest.getJSONObject("normalization")
                    .getString("input_orientation"),
                modelFilename = artifacts.getString("model"),
                labelsFilename = artifacts.getString("labels"),
                modelSha256 = artifacts.getString("model_sha256"),
                desktopParityStatus = manifest.getJSONObject("tensorflow_tflite_parity")
                    .getString("status"),
                classOrder = manifest.getJSONArray("class_order").toStringList()
            ),
            labels = StandardFslLabelsMetadata(
                featureVersion = labelsObject.getString("feature_version"),
                classCount = labelsObject.getInt("class_count"),
                labels = readLabelsPayload(labelsText)
            ),
            actualModelSha256 = actualModelSha256
        )
    }

    /** Pure exact-contract gate; arrays use content equality, not identity. */
    fun validateContract(
        manifest: StandardFslArtifactMetadata,
        labels: StandardFslLabelsMetadata,
        actualModelSha256: String
    ): List<String> {
        val profile = GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
        require(manifest.activeProfile == profile.id.name)
        require(manifest.featureVersion == StandardFullSign225Contract.FEATURE_VERSION)
        require(manifest.inputShape.contentEquals(profile.inputShape))
        require(manifest.outputShape.contentEquals(profile.outputShape))
        require(manifest.classCount == profile.classCount)
        require(manifest.inputDtype.lowercase(Locale.US) == "float32")
        require(manifest.outputDtype.lowercase(Locale.US) == "float32")
        require(manifest.modelInputOrientation.lowercase(Locale.US) == "unmirrored")
        require(
            manifest.handSlotPolicy ==
                "pose99_then_anatomical_left63_then_anatomical_right63_never_swapped"
        )
        require(!manifest.hasNegativeOrNothingClass)
        require(manifest.rejectionRequired)
        require(!manifest.androidDefaultChanged)
        require(manifest.poseRange.contentEquals(intArrayOf(0, 99)))
        require(manifest.leftHandRange.contentEquals(intArrayOf(99, 162)))
        require(manifest.rightHandRange.contentEquals(intArrayOf(162, 225)))
        require(manifest.normalizationInputOrientation.lowercase(Locale.US).startsWith("unmirrored"))
        require(manifest.modelFilename == profile.modelAsset.substringAfterLast('/'))
        require(manifest.labelsFilename == profile.labelsAsset.substringAfterLast('/'))
        val expectedHash = manifest.modelSha256.lowercase(Locale.US)
        require(expectedHash.matches(Regex("[0-9a-f]{64}")))
        require(actualModelSha256.lowercase(Locale.US) == expectedHash) { "model SHA-256 mismatch" }
        require(manifest.desktopParityStatus == "PASS") {
            "desktop TensorFlow/TFLite parity is not PASS"
        }
        require(labels.featureVersion == StandardFullSign225Contract.FEATURE_VERSION)
        require(labels.classCount == profile.classCount)
        require(labels.labels.size == profile.classCount && labels.labels.none { it.isBlank() }) {
            "labels must contain exactly ${profile.classCount} indexed entries"
        }
        require(labels.labels.distinct().size == profile.classCount) { "labels must be unique" }
        require(manifest.classOrder.size == profile.classCount) {
            "runtime manifest must contain exactly ${profile.classCount} class-order entries"
        }
        require(manifest.classOrder == labels.labels) {
            "runtime manifest class order does not exactly match labels"
        }
        return labels.labels
    }

    fun readLabelsPayload(labelsText: String): List<String> {
        val raw = labelsText.trim()
        if (raw.startsWith("[")) {
            return JSONArray(raw).toStringList()
        }
        val json = JSONObject(raw)
        json.optJSONArray("labels")?.let { return it.toStringList() }
        val output = MutableList(json.length()) { "" }
        val keys = json.keys()
        while (keys.hasNext()) {
            val label = keys.next()
            val index = json.getInt(label)
            require(index in output.indices) { "invalid label index $index" }
            require(output[index].isEmpty()) { "duplicate label index $index" }
            output[index] = label
        }
        return output
    }

    private fun assetExists(context: Context, asset: String): Boolean = try {
        context.assets.open(asset).close()
        true
    } catch (_: Exception) {
        false
    }

    private fun readText(context: Context, asset: String): String {
        return context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun sha256Asset(context: Context, asset: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(asset).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun jsonIntArray(json: JSONObject, key: String): IntArray {
        val values = json.getJSONArray(key)
        return IntArray(values.length()) { values.getInt(it) }
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> getString(index) }
    }

    /**
     * Verifies every immutable artifact whose SHA-256 is declared by the
     * finalized runtime manifest. The expected-output file additionally binds
     * the label payload and model to the same golden run.
     */
    private fun validateManifestArtifacts(
        context: Context,
        manifestText: String
    ): String {
        val manifest = JSONObject(manifestText)
        val artifacts = manifest.getJSONObject("artifacts")
        val verified = listOf(
            ManifestArtifact(
                expectedFilename = "voxgest_fsl_fullsign225_105_float32.tflite",
                filename = artifacts.getString("model"),
                expectedSha256 = artifacts.getString("model_sha256")
            ),
            artifact(artifacts, "feature_fixture", "golden_fullsign225_feature_fixture_f32.bin"),
            artifact(artifacts, "feature_fixture_metadata", "golden_fullsign225_feature_fixture.json"),
            artifact(artifacts, "tflite_window_fixture", "golden_window_f32.bin"),
            artifact(artifacts, "tflite_expected", "golden_expected.json")
        ).map { item ->
            require(item.filename == item.expectedFilename) {
                "unexpected artifact filename ${item.filename}"
            }
            val actual = sha256Asset(context, "$STANDARD_ASSET_ROOT/${item.filename}")
            require(actual.equals(item.expectedSha256, ignoreCase = true)) {
                "SHA-256 mismatch for ${item.filename}"
            }
            item.expectedFilename
        }

        val expected = JSONObject(readText(context, "$STANDARD_ASSET_ROOT/golden_expected.json"))
        val fixture = expected.getJSONObject("fixture")
        val model = expected.getJSONObject("model")
        val labels = expected.getJSONObject("labels")
        require(fixture.getString("filename") == "golden_window_f32.bin")
        require(model.getString("filename") == "voxgest_fsl_fullsign225_105_float32.tflite")
        require(labels.getString("filename") == "class_labels_fsl105_fullsign225_v1.json")
        require(sha256Asset(context, "$STANDARD_ASSET_ROOT/class_labels_fsl105_fullsign225_v1.json")
            .equals(labels.getString("sha256"), ignoreCase = true)) {
            "labels SHA-256 does not match golden expected metadata"
        }
        require(fixture.getString("sha256").equals(artifacts.getJSONObject("tflite_window_fixture")
            .getString("sha256"), ignoreCase = true))
        require(model.getString("sha256").equals(artifacts.getString("model_sha256"), ignoreCase = true))
        require(labels.getInt("class_count") == GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225.classCount)
        return "fixture_hashes_verified=${verified.joinToString(",")} " +
            "labels_sha256_verified=true class_order_verified=true"
    }

    private fun artifact(
        artifacts: JSONObject,
        key: String,
        expectedFilename: String
    ): ManifestArtifact {
        val node = artifacts.getJSONObject(key)
        return ManifestArtifact(
            expectedFilename = expectedFilename,
            filename = node.getString("filename"),
            expectedSha256 = node.getString("sha256")
        )
    }

    private data class ManifestArtifact(
        val expectedFilename: String,
        val filename: String,
        val expectedSha256: String
    )

    private fun requiredAssetPaths(): List<String> = listOf(
        "$STANDARD_ASSET_ROOT/voxgest_fsl_fullsign225_105_float32.tflite",
        "$STANDARD_ASSET_ROOT/class_labels_fsl105_fullsign225_v1.json",
        "$STANDARD_ASSET_ROOT/runtime_manifest.json",
        "$STANDARD_ASSET_ROOT/golden_fullsign225_feature_fixture_f32.bin",
        "$STANDARD_ASSET_ROOT/golden_fullsign225_feature_fixture.json",
        "$STANDARD_ASSET_ROOT/golden_window_f32.bin",
        "$STANDARD_ASSET_ROOT/golden_expected.json"
    )

    private const val STANDARD_ASSET_ROOT = GradingRecognitionProfiles.STANDARD_ASSET_ROOT

}
