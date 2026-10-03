package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable

data class StandardFslRankedPrediction(
    val index: Int,
    val label: String,
    val probability: Float
)

data class StandardFslInference(
    val probabilities: FloatArray,
    val top1: StandardFslRankedPrediction,
    val top2: StandardFslRankedPrediction,
    val top5: List<StandardFslRankedPrediction>,
    val latencyMs: Double
) {
    val margin: Float get() = top1.probability - top2.probability
}

/** Isolated 105-class runtime; it never falls through to a legacy model. */
class StandardFslTfliteRuntime(context: Context) : Closeable {
    private val profile = GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
    private val interpreter: Interpreter
    val labels: List<String>

    init {
        val readiness = StandardFslArtifactGate.inspect(context.applicationContext)
        require(readiness.canOpenRuntimeForParity) {
            "STANDARD_FSL_FULLSIGN225 blocked: ${readiness.evidence}"
        }
        labels = readiness.labels
        interpreter = TfliteModelLoader(context.applicationContext).loadInterpreterWithOptions(
            Interpreter.Options().setNumThreads(2),
            profile.modelAsset
        )
        interpreter.allocateTensors()
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        require(input.shape().contentEquals(profile.inputShape))
        require(output.shape().contentEquals(profile.outputShape))
        require(input.dataType() == DataType.FLOAT32)
        require(output.dataType() == DataType.FLOAT32)
    }

    fun infer(window: Array<FloatArray>): StandardFslInference {
        require(window.size == StandardFullSign225Contract.SEQUENCE_LENGTH)
        require(window.all { frame ->
            frame.size == StandardFullSign225Contract.FEATURE_SIZE && frame.all { it.isFinite() }
        })
        val input = Array(1) {
            Array(StandardFullSign225Contract.SEQUENCE_LENGTH) { index -> window[index].copyOf() }
        }
        val output = Array(1) { FloatArray(profile.classCount) }
        val started = SystemClock.elapsedRealtimeNanos()
        interpreter.run(input, output)
        val latency = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        val probabilities = output[0]
        require(probabilities.all { it.isFinite() }) { "TFLite output contains NaN or Inf" }
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val first = ranked[0]
        val second = ranked[1]
        return StandardFslInference(
            probabilities = probabilities.copyOf(),
            top1 = StandardFslRankedPrediction(first, labels[first], probabilities[first]),
            top2 = StandardFslRankedPrediction(second, labels[second], probabilities[second]),
            top5 = ranked.take(5).map { index ->
                StandardFslRankedPrediction(index, labels[index], probabilities[index])
            },
            latencyMs = latency
        )
    }

    override fun close() {
        interpreter.close()
    }
}
