package com.voxgest.dryrun

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.util.Locale

class VoxGestTfliteRecognizer(private val context: Context) : AutoCloseable {
    private var interpreter: Interpreter? = null
    private var profile: RecognitionProfile? = null
    private var labels: List<String> = emptyList()
    private var loadStatus: String = "Recognizer not loaded"

    fun load(profileId: String = RecognitionProfile.activeRecognitionProfileId(context)): RecognitionProfile {
        val loadedProfile = RecognitionProfile.load(context, profileId)
        val loadedLabels = loadLabels(loadedProfile)
        validateOneHandRuntime(loadedProfile, loadedLabels)
        val options = Interpreter.Options().setNumThreads(2)
        val modelLoader = TfliteModelLoader(context)
        Log.i(TAG, "startup active_profile=${loadedProfile.id}")
        Log.i(TAG, "startup model_path=${loadedProfile.modelAsset}")
        Log.i(TAG, "startup model_exists=${modelLoader.assetExists(loadedProfile.modelAsset)}")
        Log.i(TAG, "startup label_file_path=${loadedProfile.labelsAsset}")
        Log.i(TAG, "startup label_file_exists=${assetExists(loadedProfile.labelsAsset)}")
        Log.i(TAG, "startup runtime_manifest_path=${loadedProfile.manifestAsset}")
        Log.i(TAG, "startup runtime_manifest_exists=${assetExists(loadedProfile.manifestAsset)}")
        val loadedInterpreter = modelLoader.loadInterpreterWithOptions(options, loadedProfile.modelAsset)
        loadedInterpreter.resizeInput(0, loadedProfile.inputShape)
        loadedInterpreter.allocateTensors()
        validateInterpreterShape(loadedInterpreter, loadedProfile)

        interpreter?.close()
        interpreter = loadedInterpreter
        profile = loadedProfile
        labels = loadedLabels
        loadStatus = "Loaded ${loadedProfile.id} ${loadedProfile.shapeText()}"
        logStartupValidation(loadedProfile, loadedInterpreter, loadedLabels)
        return loadedProfile
    }

    fun status(): String = loadStatus

    fun recognize(sequence: Array<FloatArray>): RecognitionResult {
        val loadedProfile = profile ?: return RecognitionResult.inactive(loadStatus)
        val localInterpreter = interpreter ?: return RecognitionResult.inactive(loadStatus)
        val shapeStatus = validateSequenceShape(sequence, loadedProfile)
        val actualInputShape = "[1,${sequence.size},${sequence.firstOrNull()?.size ?: 0}]"
        Log.i(TAG, "attempt profile=${loadedProfile.id} model=${loadedProfile.modelAsset} actual_input_shape=$actualInputShape expected_input_shape=${loadedProfile.shapeText()}")
        if (shapeStatus != null) {
            Log.e(TAG, "attempt rejected reason=SHAPE_MISMATCH detail=$shapeStatus")
            return RecognitionResult.inactive("SHAPE_MISMATCH: $shapeStatus")
        }
        if (labels.isEmpty()) return RecognitionResult.inactive("Labels not loaded")

        val input = Array(1) {
            Array(loadedProfile.sequenceLength) { frameIndex ->
                sequence[frameIndex].copyOf()
            }
        }
        val output = Array(1) { FloatArray(labels.size) }
        localInterpreter.run(input, output)

        val probabilities = output[0]
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = ranked.firstOrNull() ?: return RecognitionResult.inactive("Empty output tensor")
        val second = ranked.getOrNull(1)
        val confidence = probabilities[best]
        val margin = confidence - (second?.let { probabilities[it] } ?: 0f)
        val top3 = ranked.take(3).map { index ->
            RecognitionResult.TopPrediction(labels.getOrElse(index) { "" }, probabilities[index])
        }
        val label = labels.getOrElse(best) { "" }.uppercase(Locale.US)
        val top3Text = top3.joinToString(prefix = "[", postfix = "]") { "${it.label}:${String.format(Locale.US, "%.3f", it.confidence)}" }
        Log.i(
            TAG,
            "attempt profile=${loadedProfile.id} model=${loadedProfile.modelAsset} labels=$labels input_shape=$actualInputShape top3=$top3Text predicted=$label confidence=${String.format(Locale.US, "%.3f", confidence)} margin=${String.format(Locale.US, "%.3f", margin)}"
        )
        return RecognitionResult(
            label,
            confidence,
            margin,
            false,
            "raw_tflite",
            top3
        )
    }

    private fun validateInterpreterShape(interpreter: Interpreter, profile: RecognitionProfile) {
        val actual = interpreter.getInputTensor(0).shape()
        if (!actual.contentEquals(profile.inputShape)) {
            throw IOException("TFLite input shape ${actual.contentToString()} does not match ${profile.shapeText()}")
        }
        val output = interpreter.getOutputTensor(0).shape()
        if (output.size < 2 || output[1] != profile.labels.size) {
            throw IOException("TFLite output shape ${output.contentToString()} does not match ${profile.labels.size} labels")
        }
    }

    private fun validateSequenceShape(sequence: Array<FloatArray>, profile: RecognitionProfile): String? {
        if (sequence.size != profile.sequenceLength) {
            return "Wrong input shape: expected ${profile.sequenceLength} frames"
        }
        for (frame in sequence) {
            if (frame.size != profile.featureSize) {
                return "Wrong input shape: expected ${profile.featureSize} features"
            }
        }
        return null
    }

    private fun validateOneHandRuntime(profile: RecognitionProfile, loadedLabels: List<String>) {
        if (profile.id != RecognitionProfile.ACTIVE_RECOGNITION_PROFILE &&
            profile.id != OneHandCalibrationConfig.CALIBRATED_PROFILE_ID
        ) {
            throw IOException("Only onehand162 camera recognition is enabled")
        }
        if (!profile.inputShape.contentEquals(RecognitionProfile.ONEHAND162_INPUT_SHAPE)) {
            throw IOException("SHAPE_MISMATCH: ${profile.shapeText()} != [1, 30, 162]")
        }
        if (loadedLabels != RecognitionProfile.ONEHAND162_LABELS) {
            throw IOException("Model/labels mismatch: $loadedLabels")
        }
        if (profile.labels != RecognitionProfile.ONEHAND162_LABELS) {
            throw IOException("Model/labels mismatch: manifest=${profile.labels}")
        }
        if (profile.featureProfile != "onehand162" || profile.featureSize != 162 || profile.sequenceLength != 30) {
            throw IOException("Model/labels mismatch: feature=${profile.featureProfile} sequence=${profile.sequenceLength} featureSize=${profile.featureSize}")
        }
    }

    private fun logStartupValidation(profile: RecognitionProfile, interpreter: Interpreter, loadedLabels: List<String>) {
        Log.i(TAG, "startup expected_input_shape=${profile.shapeText()}")
        Log.i(TAG, "startup interpreter_input_shape=${interpreter.getInputTensor(0).shape().contentToString()}")
        Log.i(TAG, "startup feature_profile=${profile.featureProfile}")
        Log.i(TAG, "startup dominant_hand=${profile.dominantHand}")
        Log.i(TAG, "startup mirrored_input=${profile.mirroredInput}")
        Log.i(TAG, "startup labels=$loadedLabels")
        Log.i(TAG, "feature_check onehand162=pose99+selected_hand63 sequence=30x162 expected_model_input=[1,30,162]")
        Log.w(TAG, "MIRRORING_UNVERIFIED profile=${profile.id} mirrored_input=${profile.mirroredInput}")
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun loadLabels(profile: RecognitionProfile): List<String> {
        val text = context.assets.open(profile.labelsAsset).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val out = MutableList(json.length()) { "" }
        val keys = json.keys()
        while (keys.hasNext()) {
            val label = keys.next()
            val index = json.optInt(label, -1)
            if (index in out.indices) out[index] = label
        }
        return if (out.any { it.isNotBlank() }) out else profile.labels
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        profile = null
    }

    companion object {
        private const val TAG = "VoxGestRecognition"
    }
}
