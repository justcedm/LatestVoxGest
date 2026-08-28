package com.voxgest.dryrun.fsl

import android.content.Context
import android.util.Log
import com.voxgest.dryrun.LandmarkPoint
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class FslParityCheck(
    val passed: Boolean,
    val marker: String,
    val evidence: String
)

data class FslParityReport(
    val feature: FslParityCheck,
    val tflite: FslParityCheck
) {
    val passed: Boolean get() = feature.passed && tflite.passed
}

class FslGoldenParityRunner(private val context: Context) {
    fun run(): FslParityReport {
        val feature = runFeatureParity()
        Log.i(TAG, "${feature.marker} ${if (feature.passed) "PASS" else "FAIL"} ${feature.evidence}")
        val tflite = if (feature.passed) {
            runTfliteParity()
        } else {
            FslParityCheck(false, TFLITE_MARKER, "blocked_by_feature_parity")
        }
        Log.i(TAG, "${tflite.marker} ${if (tflite.passed) "PASS" else "FAIL"} ${tflite.evidence}")
        return FslParityReport(feature, tflite)
    }

    private fun runFeatureParity(): FslParityCheck {
        return runCatching {
            val values = readLittleEndianFloats(FslContract.GOLDEN_FEATURE_ASSET, 648)
            val pose = values.copyOfRange(0, 99).toLandmarkPoints(33)
            val hand = values.copyOfRange(99, 162).toLandmarkPoints(21)
            val expectedFull = values.copyOfRange(162, 324)
            val expectedMissingHand = values.copyOfRange(324, 486)
            val expectedMissingPose = values.copyOfRange(486, 648)

            val full = FslCanonicalFeatureBuilder.build(pose, hand)
            val missingHand = FslCanonicalFeatureBuilder.build(pose, null)
            val missingPose = FslCanonicalFeatureBuilder.build(null, hand)
            val errors = listOf(
                maxAbs(full.vector, expectedFull),
                maxAbs(missingHand.vector, expectedMissingHand),
                maxAbs(missingPose.vector, expectedMissingPose)
            )
            val maxError = errors.maxOrNull() ?: Float.POSITIVE_INFINITY
            val flagsMatch = full.posePresent && full.handPresent &&
                missingHand.posePresent && !missingHand.handPresent &&
                !missingPose.posePresent && missingPose.handPresent
            val passed = maxError <= FEATURE_MAX_ABS_ERROR && flagsMatch
            FslParityCheck(
                passed,
                FEATURE_MARKER,
                "fixture=golden_feature_fixture_f32.bin max_abs_error=$maxError flags_match=$flagsMatch layout=[pose99,right_hand63]"
            )
        }.getOrElse { exc ->
            FslParityCheck(false, FEATURE_MARKER, "error=${exc.javaClass.simpleName}:${exc.message}")
        }
    }

    private fun runTfliteParity(): FslParityCheck {
        return runCatching {
            val expectedJson = context.assets.open(FslContract.GOLDEN_EXPECTED_ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
            val expectedNode = expectedJson.getJSONObject("expected_output")
            val expectedArray = expectedNode.getJSONArray("probabilities")
            val expected = FloatArray(expectedArray.length()) { expectedArray.getDouble(it).toFloat() }
            val thresholds = expectedJson.getJSONObject("android_parity_thresholds")
            val maxThreshold = thresholds.getDouble("maximum_absolute_probability_error").toFloat()
            val meanThreshold = thresholds.getDouble("maximum_mean_absolute_probability_error").toFloat()
            val input = readLittleEndianFloats(
                FslContract.GOLDEN_WINDOW_ASSET,
                FslContract.SEQUENCE_LENGTH * FslContract.FEATURE_SIZE
            )
            val window = List(FslContract.SEQUENCE_LENGTH) { frame ->
                input.copyOfRange(
                    frame * FslContract.FEATURE_SIZE,
                    (frame + 1) * FslContract.FEATURE_SIZE
                )
            }
            FslTfliteRuntime(context).use { runtime ->
                val actual = runtime.infer(window)
                val differences = actual.probabilities.indices.map { index ->
                    abs(actual.probabilities[index] - expected[index])
                }
                val maxError = differences.maxOrNull() ?: Float.POSITIVE_INFINITY
                val meanError = differences.average().toFloat()
                val expectedTop1 = expectedNode.getInt("top1_index")
                val top1Match = actual.top1.index == expectedTop1
                val passed = top1Match && maxError <= maxThreshold && meanError <= meanThreshold
                FslParityCheck(
                    passed,
                    TFLITE_MARKER,
                    "fixture=golden_window_f32.bin top1=${actual.top1.label} index=${actual.top1.index} expected_index=$expectedTop1 top1_match=$top1Match max_abs_error=$maxError mean_abs_error=$meanError latency_ms=${"%.3f".format(actual.latencyMs)}"
                )
            }
        }.getOrElse { exc ->
            FslParityCheck(false, TFLITE_MARKER, "error=${exc.javaClass.simpleName}:${exc.message}")
        }
    }

    private fun readLittleEndianFloats(asset: String, count: Int): FloatArray {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        require(bytes.size == count * Float.SIZE_BYTES) {
            "$asset has ${bytes.size} bytes, expected ${count * Float.SIZE_BYTES}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.float }
    }

    private fun FloatArray.toLandmarkPoints(count: Int): List<LandmarkPoint> {
        require(size == count * 3)
        return List(count) { index ->
            LandmarkPoint(this[index * 3], this[index * 3 + 1], this[index * 3 + 2])
        }
    }

    private fun maxAbs(actual: FloatArray, expected: FloatArray): Float {
        require(actual.size == expected.size)
        var maximum = 0f
        actual.indices.forEach { index -> maximum = maxOf(maximum, abs(actual[index] - expected[index])) }
        return maximum
    }

    companion object {
        private const val TAG = "VoxGestFsl"
        private const val FEATURE_MARKER = "ANDROID_FEATURE_PARITY"
        private const val TFLITE_MARKER = "ANDROID_TFLITE_PARITY"
        private const val FEATURE_MAX_ABS_ERROR = 1.0e-6f
    }
}
