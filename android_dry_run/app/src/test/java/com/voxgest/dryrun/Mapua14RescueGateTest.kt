package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mapua14RescueGateTest {
    @Test
    fun dynamic32FrameGateNeedsStabilityAndSuppressesHeldDuplicate() {
        val gate = Mapua14RescueGate(32)
        val first = gate.evaluate(inference("HELLO", 0.92f, 0.08f), quality(32), timing(), true)
        val accepted = gate.evaluate(inference("HELLO", 0.93f, 0.07f), quality(32), timing(), true)
        val duplicate = gate.evaluate(inference("HELLO", 0.94f, 0.06f), quality(32), timing(), true)

        assertFalse(first.accepted)
        assertEquals("TEMPORAL_STABILITY", first.reason)
        assertTrue(accepted.accepted)
        assertEquals("ACCEPTED", accepted.reason)
        assertFalse(duplicate.accepted)
        assertEquals("DUPLICATE_REQUIRES_RELEASE", duplicate.reason)
    }

    @Test
    fun rawPredictionCannotPassWithLowConfidenceOrNoHandEvidence() {
        val lowConfidence = Mapua14RescueGate(48).evaluate(
            inference("YES", 0.60f, 0.20f), quality(48), timing(), true
        )
        val noHands = Mapua14RescueGate(48).evaluate(
            inference("YES", 0.95f, 0.01f), quality(48, anyHands = 0), timing(), true
        )

        assertEquals("LOW_CONFIDENCE", lowConfidence.reason)
        assertEquals("LOW_HAND_PRESENCE", noHands.reason)
    }

    @Test
    fun experimentalOverrideRequiresDebugDiagnosticsAndExactId() {
        DeveloperRecognitionOverride.configure(true, true, Mapua14RescueProfile.ID)
        assertEquals(CameraRecognitionRuntime.MAPUA14_RESCUE_V1, DeveloperRecognitionOverride.runtime())
        DeveloperRecognitionOverride.configure(true, false, Mapua14RescueProfile.ID)
        assertEquals(CameraRecognitionRuntime.STANDARD_FSL105, DeveloperRecognitionOverride.runtime())
        DeveloperRecognitionOverride.configure(true, true, "mapua14_rescue_v1")
        assertEquals(CameraRecognitionRuntime.STANDARD_FSL105, DeveloperRecognitionOverride.runtime())
    }

    private fun inference(label: String, top1: Float, top2: Float): StandardFslInference {
        val second = if (label == "YES") "NO" else "YES"
        return StandardFslInference(
            floatArrayOf(top1, top2),
            StandardFslRankedPrediction(0, label, top1),
            StandardFslRankedPrediction(1, second, top2),
            listOf(StandardFslRankedPrediction(0, label, top1), StandardFslRankedPrediction(1, second, top2)),
            1.0
        )
    }

    private fun quality(frames: Int, anyHands: Int = frames) = StandardFullSign225WindowQuality(
        frames, frames, anyHands, 0, anyHands, 0
    )

    private fun timing() = StandardFullSign225WindowTiming(true, true, 3_000L, 3_000L, 100L, 150L)
}
