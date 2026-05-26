package com.voxgest.app.ml

class PredictionGate(
    private val profileThresholds: Map<String, WordThreshold>,
    private val defaultThreshold: WordThreshold
) {
    data class WordThreshold(
        val confidence: Float,
        val margin: Float,
        val motion: Float,
        val wristPath: Float,
        val stableFrames: Int = 8
    )

    data class GateResult(
        val accepted: Boolean,
        val label: String,
        val confidence: Float,
        val margin: Float,
        val rejectReason: String
    )

    fun evaluate(
        rawOutput: FloatArray,
        labelMap: Map<Int, String>,
        motionEnergy: Float,
        wristPathLength: Float
    ): GateResult {
        if (rawOutput.isEmpty()) {
            return GateResult(false, "?", 0f, 0f, "empty_output")
        }

        val sorted = rawOutput.indices.sortedByDescending { rawOutput[it] }
        val topIdx = sorted[0]
        val secondIdx = sorted.getOrElse(1) { topIdx }
        val label = labelMap[topIdx] ?: "?"
        val conf = rawOutput[topIdx]
        val margin = conf - rawOutput[secondIdx]

        if (label.equals("NOTHING", ignoreCase = true)) {
            return GateResult(false, label, conf, margin, "no_output_class")
        }

        val threshold = profileThresholds[label.uppercase()] ?: defaultThreshold
        if (conf < threshold.confidence) {
            return GateResult(false, label, conf, margin, "conf_${conf}_<_${threshold.confidence}")
        }
        if (margin < threshold.margin) {
            return GateResult(false, label, conf, margin, "margin_${margin}_<_${threshold.margin}")
        }
        if (motionEnergy < threshold.motion) {
            return GateResult(false, label, conf, margin, "motion_${motionEnergy}_<_${threshold.motion}")
        }
        if (wristPathLength < threshold.wristPath) {
            return GateResult(false, label, conf, margin, "path_${wristPathLength}_<_${threshold.wristPath}")
        }

        return GateResult(true, label, conf, margin, "")
    }
}
