package com.voxgest.app.ml

import android.content.Context
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import kotlin.math.pow
import kotlin.math.sqrt

data class LstmClassificationResult(
    val accepted: Boolean,
    val label: String,
    val confidence: Float,
    val margin: Float,
    val motionEnergy: Float,
    val wristPathLength: Float,
    val rejectReason: String,
    val debugText: String
)

class LstmClassifier(
    context: Context,
    private val profile: WordProfile,
    private val interpreter: Interpreter = WordProfileManager.loadProfile(context, profile),
    private val labelMap: Map<Int, String> = WordProfileManager.labelMap(context, profile),
    private val thresholds: Map<String, PredictionGate.WordThreshold> = WordProfileManager.thresholds(context, profile)
) : AutoCloseable {
    private val gate = PredictionGate(thresholds, profile.defaultThreshold)
    private val smoothingWindow = ArrayDeque<PredictionGate.GateResult>()
    private var stableLabel: String = ""
    private var stableCount: Int = 0
    private var lastEmittedLabel: String = ""
    private var cooldownUntilMs: Long = 0L

    fun classify(frameWindow: List<FloatArray>, wristLandmarkIndex: Int = RIGHT_WRIST_INDEX): LstmClassificationResult {
        val shapeError = validateWindow(frameWindow)
        if (shapeError != null) {
            return rejected("?", 0f, 0f, 0f, 0f, shapeError)
        }

        val rawOutput = runInference(frameWindow)
        val motionEnergy = computeMotionEnergy(frameWindow, wristLandmarkIndex)
        val wristPath = computeWristPath(frameWindow, wristLandmarkIndex)
        val gateResult = gate.evaluate(rawOutput, labelMap, motionEnergy, wristPath)
        val gated = applySmoothingAndCooldown(gateResult)
        val debug = if (gated.accepted) {
            "gate: ${gated.label} c=${gated.confidence.format2()} m=${gated.margin.format2()} mot=${motionEnergy.format2()} path=${wristPath.format2()} -> ACCEPTED"
        } else {
            "gate: REJECTED - ${gated.rejectReason}"
        }
        return LstmClassificationResult(
            accepted = gated.accepted,
            label = gated.label,
            confidence = gated.confidence,
            margin = gated.margin,
            motionEnergy = motionEnergy,
            wristPathLength = wristPath,
            rejectReason = gated.rejectReason,
            debugText = debug
        )
    }

    fun reset() {
        smoothingWindow.clear()
        stableLabel = ""
        stableCount = 0
        lastEmittedLabel = ""
        cooldownUntilMs = 0L
    }

    override fun close() {
        interpreter.close()
    }

    private fun runInference(frameWindow: List<FloatArray>): FloatArray {
        val input = Array(1) {
            Array(profile.inputShape[1]) { index -> frameWindow[index].copyOf() }
        }
        val output = Array(1) { FloatArray(labelMap.size) }
        interpreter.run(input, output)
        return output[0]
    }

    private fun applySmoothingAndCooldown(result: PredictionGate.GateResult): PredictionGate.GateResult {
        if (!result.accepted) {
            pushGateResult(result)
            stableLabel = ""
            stableCount = 0
            return result
        }

        pushGateResult(result)
        val majority = smoothingWindow
            .filter { it.accepted }
            .groupingBy { it.label.uppercase() }
            .eachCount()
            .filterValues { it >= SMOOTHING_MAJORITY }
            .maxByOrNull { it.value }
            ?.key
            ?: return result.copy(accepted = false, rejectReason = "smoothing_wait")

        stableCount = if (majority == stableLabel) stableCount + 1 else 1
        stableLabel = majority

        val requiredStableFrames = thresholds[majority]?.stableFrames ?: profile.defaultThreshold.stableFrames
        if (stableCount < requiredStableFrames) {
            return result.copy(accepted = false, label = majority, rejectReason = "stable_${stableCount}_<_$requiredStableFrames")
        }

        val now = SystemClock.elapsedRealtime()
        if (majority == lastEmittedLabel && now < cooldownUntilMs) {
            return result.copy(accepted = false, label = majority, rejectReason = "cooldown")
        }

        lastEmittedLabel = majority
        cooldownUntilMs = now + COOLDOWN_MS
        stableCount = 0
        smoothingWindow.clear()
        return result.copy(accepted = true, label = majority, rejectReason = "")
    }

    private fun pushGateResult(result: PredictionGate.GateResult) {
        if (smoothingWindow.size == SMOOTHING_SIZE) smoothingWindow.removeFirst()
        smoothingWindow.addLast(result)
    }

    private fun validateWindow(frameWindow: List<FloatArray>): String? {
        val expectedFrames = profile.inputShape.getOrElse(1) { 30 }
        val expectedFeatures = profile.inputShape.getOrElse(2) { 162 }
        if (frameWindow.size != expectedFrames) return "frames_${frameWindow.size}_<_$expectedFrames"
        if (frameWindow.any { it.size != expectedFeatures }) return "wrong_feature_shape"
        return null
    }

    private fun computeMotionEnergy(frameWindow: List<FloatArray>, wristLandmarkIndex: Int): Float {
        val pairs = frameWindow.zipWithNext().takeLast(10)
        if (pairs.isEmpty()) return 0f
        val offset = wristLandmarkIndex * 3
        val energy = pairs.map { (a, b) ->
            (a[offset] - b[offset]).toDouble().pow(2.0) +
                (a[offset + 1] - b[offset + 1]).toDouble().pow(2.0)
        }.average()
        return energy.toFloat()
    }

    private fun computeWristPath(frameWindow: List<FloatArray>, wristLandmarkIndex: Int): Float {
        val offset = wristLandmarkIndex * 3
        return frameWindow.zipWithNext().sumOf { (a, b) ->
            val dx = a[offset] - b[offset]
            val dy = a[offset + 1] - b[offset + 1]
            sqrt(dx * dx + dy * dy).toDouble()
        }.toFloat()
    }

    private fun rejected(
        label: String,
        confidence: Float,
        margin: Float,
        motionEnergy: Float,
        wristPath: Float,
        reason: String
    ): LstmClassificationResult {
        return LstmClassificationResult(false, label, confidence, margin, motionEnergy, wristPath, reason, "gate: REJECTED - $reason")
    }

    private fun Float.format2(): String = String.format("%.2f", this)

    companion object {
        private const val RIGHT_WRIST_INDEX = 15
        private const val SMOOTHING_SIZE = 5
        private const val SMOOTHING_MAJORITY = 3
        private const val COOLDOWN_MS = 1250L
    }
}
