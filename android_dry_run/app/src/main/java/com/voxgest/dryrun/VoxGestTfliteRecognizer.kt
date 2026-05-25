package com.voxgest.dryrun

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.util.Locale

class VoxGestTfliteRecognizer(private val context: Context) : AutoCloseable {
    private var interpreter: Interpreter? = null
    private var profile: RecognitionProfile? = null
    private var labels: List<String> = emptyList()
    private var loadStatus: String = "Recognizer not loaded"

    fun load(profileId: String = RecognitionProfile.ACTIVE_RECOGNITION_PROFILE): RecognitionProfile {
        val loadedProfile = RecognitionProfile.load(context, profileId)
        val loadedLabels = loadLabels(loadedProfile)
        val options = Interpreter.Options().setNumThreads(2)
        val loadedInterpreter = TfliteModelLoader(context).loadInterpreterWithOptions(options, loadedProfile.modelAsset)
        loadedInterpreter.resizeInput(0, loadedProfile.inputShape)
        loadedInterpreter.allocateTensors()
        validateInterpreterShape(loadedInterpreter, loadedProfile)

        interpreter?.close()
        interpreter = loadedInterpreter
        profile = loadedProfile
        labels = loadedLabels
        loadStatus = "Loaded ${loadedProfile.id} ${loadedProfile.shapeText()}"
        return loadedProfile
    }

    fun status(): String = loadStatus

    fun recognize(sequence: Array<FloatArray>): RecognitionResult {
        val loadedProfile = profile ?: return RecognitionResult.inactive(loadStatus)
        val localInterpreter = interpreter ?: return RecognitionResult.inactive(loadStatus)
        val shapeStatus = validateSequenceShape(sequence, loadedProfile)
        if (shapeStatus != null) return RecognitionResult.inactive(shapeStatus)
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
        return RecognitionResult(
            labels.getOrElse(best) { "" }.uppercase(Locale.US),
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
}
