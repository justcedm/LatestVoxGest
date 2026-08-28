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
        val accepted = gate.evaluate(inference("A", 0.70f, "B", 0.20f))
        assertTrue(accepted.accepted)
        assertEquals("VALIDATION_ONLY_THRESHOLDS", accepted.reason)
    }

    @Test
    fun appliesManifestFrameCooldownOnlyInsideExperimentalGate() {
        val gate = FslDiagnosticGate(contract)
        assertTrue(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
        repeat(9) {
            val decision = gate.evaluate(inference("A", 0.8f, "B", 0.1f))
            assertFalse(decision.accepted)
            assertEquals("PROVISIONAL_COOLDOWN", decision.reason)
        }
        assertFalse(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
        assertTrue(gate.evaluate(inference("A", 0.8f, "B", 0.1f)).accepted)
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
