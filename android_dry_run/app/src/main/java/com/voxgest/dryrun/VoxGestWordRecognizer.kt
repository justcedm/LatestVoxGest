package com.voxgest.dryrun

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.IOException

class VoxGestWordRecognizer(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var status: String = "Recognizer not loaded"
    private val gate = RecognitionGate()

    fun load() {
        try {
            labels = loadLabels()
            interpreter = TfliteModelLoader(context).loadInterpreter(MODEL_ASSET)
            status = "Loaded fullsign225_manual5_team_v2 TCN"
        } catch (missingRuntime: NoClassDefFoundError) {
            status = "TFLite runtime not packaged in UI build"
        } catch (missingRuntime: UnsatisfiedLinkError) {
            status = "TFLite runtime unavailable on this device build"
        } catch (exc: IOException) {
            status = "Recognizer asset unavailable: ${exc.message}"
        } catch (exc: Exception) {
            status = "Recognizer unavailable: ${exc.message}"
        }
    }

    fun status(): String = status

    fun recognize(sequence30x225: Array<FloatArray>, handPresence: Float = 1f): RecognitionResult {
        val localInterpreter = interpreter ?: return RecognitionResult.inactive(status)
        if (sequence30x225.size != LandmarkSequenceBuffer.SEQUENCE_LENGTH) {
            return RecognitionResult.inactive("Dynamic sequence length mismatch")
        }
        if (labels.isEmpty()) {
            return RecognitionResult.inactive("Labels not loaded")
        }

        val input = Array(1) {
            Array(LandmarkSequenceBuffer.SEQUENCE_LENGTH) { frameIndex ->
                val frame = sequence30x225[frameIndex]
                if (frame.size == LandmarkSequenceBuffer.FULLSIGN225_FEATURE_SIZE) {
                    frame.copyOf()
                } else {
                    return RecognitionResult.inactive("Dynamic feature size mismatch")
                }
            }
        }
        val output = Array(1) { FloatArray(labels.size) }
        localInterpreter.run(input, output)

        var best = 0
        var second = 0
        for (i in output[0].indices) {
            if (output[0][i] > output[0][best]) {
                second = best
                best = i
            } else if (i != best && output[0][i] > output[0][second]) {
                second = i
            }
        }

        val label = labels.getOrElse(best) { "" }
        val confidence = output[0][best]
        val margin = confidence - output[0][second]
        val gateResult = gate.evaluate(
            GateInput(
                label = label,
                confidence = confidence,
                margin = margin,
                handPresence = handPresence
            )
        )
        return RecognitionResult(label, confidence, margin, gateResult.accepted, gateResult.reason)
    }

    private fun loadLabels(): List<String> {
        val text = context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val out = MutableList(json.length()) { "" }
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val index = json.optInt(key, -1)
            if (index in out.indices) {
                out[index] = key
            }
        }
        return out
    }

    companion object {
        const val MODEL_ASSET = "model/voxgest_tcn_fullsign225_manual5_team_v2.tflite"
        const val LABELS_ASSET = "model/class_labels_tcn_fullsign225_manual5_team_v2.json"
        const val MANIFEST_ASSET = "model/runtime_manifest_fullsign225_manual5_team_v2.json"
    }
}
