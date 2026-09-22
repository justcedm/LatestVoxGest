package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicWordAcceptanceGateTest {
    private val calibratedProfile = RecognitionProfile(
        id = OneHandCalibrationConfig.CALIBRATED_PROFILE_ID,
        featureProfile = "onehand162",
        modelAsset = "model.tflite",
        labelsAsset = "labels.json",
        manifestAsset = "manifest.json",
        inputShape = intArrayOf(1, 20, 162),
        labels = OneHandCalibrationConfig.MODEL_LABELS,
        sequenceLength = 20,
        featureSize = 162,
        mirroredInput = true,
        dominantHand = "right",
        singleHandPose = true
    )
    private val oneHandDecision = RouterDecision(
        route = RecognitionRoute.ONEHAND,
        statusText = "Signing",
        reason = "test",
        handPresence = 1f,
        motion = 0.02f,
        shapeMotion = 0.02f,
        stableFrames = 0,
        movingFrames = 3,
        bothHandsPresent = false
    )

    @Test
    fun calibratedHighConfidenceCannotBypassDuplicateCooldown() {
        val gate = DynamicWordAcceptanceGate()
        val raw = RecognitionResult("WHAT", 0.99f, 0.90f, false, "raw")

        val first = gate.evaluate(raw, calibratedProfile, oneHandDecision, 1f, consecutiveMatches = 2)
        assertTrue(first.accepted)
        assertEquals("HIGH_CONFIDENCE", first.reason)

        val duplicate = gate.evaluate(raw, calibratedProfile, oneHandDecision, 1f, consecutiveMatches = 3)
        assertFalse(duplicate.accepted)
        assertTrue(duplicate.duplicateBlocked)
        assertEquals("DUPLICATE_COOLDOWN", duplicate.reason)
    }

    @Test
    fun noOutputResetMakesSameLabelEligibleAgain() {
        val gate = DynamicWordAcceptanceGate()
        val raw = RecognitionResult("WHAT", 0.99f, 0.90f, false, "raw")
        assertTrue(gate.evaluate(raw, calibratedProfile, oneHandDecision, 1f, 2).accepted)

        gate.noteNoOutputState()
        val afterReset = gate.evaluate(raw, calibratedProfile, oneHandDecision, 1f, 2)
        // Wall-clock cooldown still protects against immediate re-emission.
        assertFalse(afterReset.accepted)
        assertEquals("DUPLICATE_COOLDOWN", afterReset.reason)
    }
}
