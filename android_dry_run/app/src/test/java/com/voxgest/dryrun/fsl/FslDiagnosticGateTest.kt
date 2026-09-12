package com.voxgest.dryrun.fsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FslDiagnosticGateTest {
    private val contract = FslRuntimeContract(
        profileId = FslContract.PROFILE_ID,
        featureVersion = FslContract.FEATURE_VERSION,
        labels = listOf("A", "B"),
        androidTokens = listOf("A", "B"),
        confidenceThreshold = 0.5f,
        marginThreshold = 0.1f,
        cooldownFrames = 10,
        deploymentEligible = false,
        modelSha256 = "model",
        labelsSha256 = "labels",
        manifestSha256 = "manifest"
    )

    @Test
    fun usesValidationOnlyConfidenceAndMarginWithoutChangingStableGate() {
        val gate = FslDiagnosticGate(contract)
        assertFalse(gate.evaluate(inference("A", 0.49f, "B", 0.1f)).accepted)
        assertFalse(gate.evaluate(inference("A", 0.55f, "B", 0.50f)).accepted)
        val provisional = gate.evaluate(inference("A", 0.70f, "B", 0.20f))
        assertFalse(provisional.accepted)
        assertEquals("TEMPORAL_STABILITY", provisional.reason)
        val accepted = gate.evaluate(inference("A", 0.70f, "B", 0.20f))
        assertTrue(accepted.accepted)
        assertEquals("ACCEPTED", accepted.reason)
        assertEquals(2, accepted.stableWindowCount)
    }

    @Test
    fun appliesManifestFrameCooldownOnlyInsideExperimentalGate() {
        val gate = FslDiagnosticGate(contract)
        assertFalse(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
        assertTrue(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
        repeat(9) {
            val decision = gate.evaluate(inference("A", 0.8f, "B", 0.1f))
            assertFalse(decision.accepted)
            assertEquals("PROVISIONAL_COOLDOWN", decision.reason)
        }
        assertFalse(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
        assertTrue(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
    }

    @Test
    fun rejectsLowPoseOrSelectedHandPresenceBeforeClosedSetOutput() {
        val gate = FslDiagnosticGate(contract)
        val lowPose = gate.evaluate(
            inference("A", 0.9f, "B", 0.05f),
            FslWindowQuality(posePresenceRatio = 0.60f, selectedHandPresenceRatio = 1f)
        )
        assertEquals("LOW_POSE_PRESENCE", lowPose.reason)

        val lowHand = gate.evaluate(
            inference("A", 0.9f, "B", 0.05f),
            FslWindowQuality(posePresenceRatio = 1f, selectedHandPresenceRatio = 0.60f)
        )
        assertEquals("LOW_SELECTED_HAND_PRESENCE", lowHand.reason)
    }

    @Test
    fun changingTop1RestartsTemporalAgreement() {
        val gate = FslDiagnosticGate(contract)
        assertEquals("TEMPORAL_STABILITY", gate.evaluate(inference("A", 0.9f, "B", 0.05f)).reason)
        assertEquals("TEMPORAL_STABILITY", gate.evaluate(inference("B", 0.9f, "A", 0.05f)).reason)
        assertTrue(gate.evaluate(inference("B", 0.9f, "A", 0.05f)).accepted)
    }

    private fun inference(
        firstLabel: String,
        first: Float,
        secondLabel: String,
        second: Float
    ): FslInference {
        return FslInference(
            probabilities = floatArrayOf(first, second),
            top1 = FslRankedPrediction(0, firstLabel, first),
            top2 = FslRankedPrediction(1, secondLabel, second),
            margin = first - second,
            latencyMs = 1.0
        )
    }
}
