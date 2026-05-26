package com.voxgest.dryrun

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt

data class LetterPrediction(
    val letter: Char,
    val confidence: Float
)

data class AlphabetRawPrediction(
    val label: String,
    val confidence: Float,
    val margin: Float
)

class AlphabetClassifier(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var statusText: String = "Alphabet recognizer not loaded"

    fun load() {
        val options = Interpreter.Options().setNumThreads(2)
        val loaded = TfliteModelLoader(appContext).loadInterpreterWithOptions(options, MODEL_ASSET)
        val inputShape = loaded.getInputTensor(0).shape()
        if (!(inputShape.size == 2 && inputShape[0] == 1 && inputShape[1] == FEATURE_SIZE)) {
            loaded.close()
            throw IOException("Alphabet input shape ${inputShape.contentToString()} != [1, $FEATURE_SIZE]")
        }
        val loadedLabels = loadLabels()
        if (loadedLabels.isEmpty()) {
            loaded.close()
            throw IOException("Alphabet labels are empty")
        }
        interpreter?.close()
        interpreter = loaded
        labels = loadedLabels
        statusText = "Alphabet recognizer loaded"
        Log.i(TAG, "Loaded $MODEL_ASSET input=${inputShape.contentToString()} labels=$loadedLabels")
    }

    fun status(): String = statusText

    fun predict(frame: LandmarkFrame, flipLandmarksHorizontal: Boolean): AlphabetRawPrediction? {
        val points = frame.rightHandLandmarks ?: frame.leftHandLandmarks
        if (points?.size != HAND_LANDMARK_COUNT) return null
        val inputVector = normalizeHand(points, flipLandmarksHorizontal) ?: return null
        return predict(inputVector)
    }

    fun reset() {
        // Stateless classifier; gates hold temporal state.
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        labels = emptyList()
    }

    private fun predict(features63: FloatArray): AlphabetRawPrediction? {
        val localInterpreter = interpreter ?: return null
        val input = arrayOf(features63)
        val output = Array(1) { FloatArray(labels.size) }
        localInterpreter.run(input, output)
        val probabilities = output[0]
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        val label = labels.getOrElse(best) { "" }.normalizeAlphabetLabel()
        val confidence = probabilities[best]
        val margin = confidence - (second?.let { probabilities[it] } ?: 0f)
        return AlphabetRawPrediction(label, confidence, margin)
    }

    private fun normalizeHand(points: List<LandmarkPoint>, flipLandmarksHorizontal: Boolean): FloatArray? {
        val wrist = points[0].withOptionalFlip(flipLandmarksHorizontal)
        val middleMcp = points[9].withOptionalFlip(flipLandmarksHorizontal)
        val scale = distance(wrist, middleMcp)
        if (scale <= MIN_SCALE) return null

        val out = FloatArray(FEATURE_SIZE)
        for (index in points.indices) {
            val point = points[index].withOptionalFlip(flipLandmarksHorizontal)
            val dest = index * 3
            out[dest] = (point.x - wrist.x) / scale
            out[dest + 1] = (point.y - wrist.y) / scale
            out[dest + 2] = (point.z - wrist.z) / scale
        }
        return out
    }

    private fun LandmarkPoint.withOptionalFlip(flip: Boolean): LandmarkPoint {
        return if (flip) copy(x = 1.0f - x) else this
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun loadLabels(): List<String> {
        val text = appContext.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val out = MutableList(json.length()) { "" }
        val keys = json.keys()
        while (keys.hasNext()) {
            val label = keys.next()
            val index = json.optInt(label, -1)
            if (index in out.indices) out[index] = label.normalizeAlphabetLabel()
        }
        return out
    }

    private fun String.normalizeAlphabetLabel(): String {
        return when (trim().uppercase(Locale.US)) {
            "DEL", "DELETE" -> "DEL"
            "SPACE" -> "SPACE"
            "NOTHING" -> "NOTHING"
            else -> trim().uppercase(Locale.US)
        }
    }

    companion object {
        private const val TAG = "VoxGestRecognition"
        private const val MODEL_ASSET = "models/asl_alphabet.tflite"
        private const val LABELS_ASSET = "models/asl_alphabet_labels.json"
        private const val FEATURE_SIZE = 63
        private const val HAND_LANDMARK_COUNT = 21
        private const val MIN_SCALE = 1.0e-4f
    }
}
