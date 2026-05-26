package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt

data class LetterPrediction(
    val letter: Char,
    val confidence: Float
)

class AlphabetClassifier(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var statusText: String = "Alphabet recognizer not loaded"
    private val smoothedScores = mutableMapOf<String, Float>()
    private var topLabel: String = ""
    private var topFrames: Int = 0
    private var rawLabel: String = ""
    private var rawFramesAboveThreshold: Int = 0
    private var cooldownUntilMs: Long = 0L

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
        reset()
    }

    fun status(): String = statusText

    fun classify(frame: LandmarkFrame, flipLandmarksHorizontal: Boolean): LetterPrediction? {
        val points = frame.rightHandLandmarks ?: frame.leftHandLandmarks
        if (points?.size != HAND_LANDMARK_COUNT) {
            reset()
            return null
        }
        val inputVector = normalizeHand(points, flipLandmarksHorizontal) ?: run {
            reset()
            return null
        }
        return classify(inputVector)
    }

    fun reset() {
        smoothedScores.clear()
        topLabel = ""
        topFrames = 0
        rawLabel = ""
        rawFramesAboveThreshold = 0
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        labels = emptyList()
        reset()
    }

    private fun classify(features63: FloatArray): LetterPrediction? {
        val localInterpreter = interpreter ?: return null
        if (SystemClock.elapsedRealtime() < cooldownUntilMs) return null

        val input = arrayOf(features63)
        val output = Array(1) { FloatArray(labels.size) }
        localInterpreter.run(input, output)
        val probabilities = output[0]
        val topIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val label = labels.getOrElse(topIndex) { "" }.uppercase(Locale.US)
        val rawConfidence = probabilities[topIndex]

        for (index in probabilities.indices) {
            val candidate = labels.getOrElse(index) { "" }.uppercase(Locale.US)
            if (candidate.isBlank()) continue
            val previous = smoothedScores[candidate] ?: 0f
            smoothedScores[candidate] = EMA_PREVIOUS_WEIGHT * previous + EMA_CURRENT_WEIGHT * probabilities[index]
        }

        if (label == topLabel) {
            topFrames += 1
        } else {
            topLabel = label
            topFrames = 1
        }

        if (label == rawLabel && rawConfidence >= RAW_CONFIDENCE_THRESHOLD) {
            rawFramesAboveThreshold += 1
        } else {
            rawLabel = label
            rawFramesAboveThreshold = if (rawConfidence >= RAW_CONFIDENCE_THRESHOLD) 1 else 0
        }

        if (!isOutputLetter(label)) return null
        val smoothed = smoothedScores[label] ?: rawConfidence
        val accepted = smoothed >= SMOOTHED_CONFIDENCE_THRESHOLD &&
            topFrames >= REQUIRED_TOP_FRAMES &&
            rawFramesAboveThreshold >= REQUIRED_RAW_FRAMES
        if (!accepted) return null

        cooldownUntilMs = SystemClock.elapsedRealtime() + ACCEPTED_COOLDOWN_MS
        val prediction = LetterPrediction(label[0], smoothed.coerceAtLeast(rawConfidence))
        reset()
        return prediction
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

    private fun isOutputLetter(label: String): Boolean {
        return label.length == 1 && label[0] in 'A'..'Z'
    }

    private fun loadLabels(): List<String> {
        val text = appContext.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val out = MutableList(json.length()) { "" }
        val keys = json.keys()
        while (keys.hasNext()) {
            val label = keys.next()
            val index = json.optInt(label, -1)
            if (index in out.indices) out[index] = label
        }
        return out
    }

    companion object {
        private const val MODEL_ASSET = "models/asl_alphabet.tflite"
        private const val LABELS_ASSET = "models/asl_alphabet_labels.json"
        private const val FEATURE_SIZE = 63
        private const val HAND_LANDMARK_COUNT = 21
        private const val RAW_CONFIDENCE_THRESHOLD = 0.80f
        private const val SMOOTHED_CONFIDENCE_THRESHOLD = 0.75f
        private const val EMA_PREVIOUS_WEIGHT = 0.60f
        private const val EMA_CURRENT_WEIGHT = 0.40f
        private const val REQUIRED_TOP_FRAMES = 3
        private const val REQUIRED_RAW_FRAMES = 2
        private const val ACCEPTED_COOLDOWN_MS = 400L
        private const val MIN_SCALE = 1.0e-4f
    }
}
