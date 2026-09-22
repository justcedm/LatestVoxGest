package com.voxgest.dryrun.fsl

import android.content.Context
import android.os.SystemClock
import com.voxgest.dryrun.TfliteModelLoader
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable

data class FslRankedPrediction(val index: Int, val label: String, val probability: Float)

data class FslInference(
    val probabilities: FloatArray,
    val top1: FslRankedPrediction,
    val top2: FslRankedPrediction,
    val margin: Float,
    val latencyMs: Double
)

class FslTfliteRuntime(context: Context) : Closeable {
    val contract: FslRuntimeContract = FslContract.load(context.applicationContext)
    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().setNumThreads(2)
        interpreter = TfliteModelLoader(context.applicationContext)
            .loadInterpreterWithOptions(options, FslContract.MODEL_ASSET)
        interpreter.allocateTensors()
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        require(input.shape().contentEquals(FslContract.INPUT_SHAPE)) {
            "Input shape ${input.shape().contentToString()} is not [1,20,162]"
        }
        require(output.shape().contentEquals(FslContract.OUTPUT_SHAPE)) {
            "Output shape ${output.shape().contentToString()} is not [1,64]"
        }
        require(input.dataType() == DataType.FLOAT32) { "Input dtype must be float32" }
        require(output.dataType() == DataType.FLOAT32) { "Output dtype must be float32" }
    }

    fun infer(window: List<FloatArray>): FslInference {
        require(window.size == FslContract.SEQUENCE_LENGTH) { "Expected exactly 20 frames" }
        require(window.all { it.size == FslContract.FEATURE_SIZE }) { "Expected 162 features per frame" }
        val input = Array(1) {
            Array(FslContract.SEQUENCE_LENGTH) { index -> window[index].copyOf() }
        }
        val output = Array(1) { FloatArray(FslContract.CLASS_COUNT) }
        val started = SystemClock.elapsedRealtimeNanos()
        interpreter.run(input, output)
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        val probabilities = output[0]
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val first = ranked[0]
        val second = ranked[1]
        val top1 = FslRankedPrediction(first, contract.labels[first], probabilities[first])
        val top2 = FslRankedPrediction(second, contract.labels[second], probabilities[second])
        return FslInference(
            probabilities = probabilities.copyOf(),
            top1 = top1,
            top2 = top2,
            margin = top1.probability - top2.probability,
            latencyMs = latencyMs
        )
    }

    override fun close() {
        interpreter.close()
    }
}
