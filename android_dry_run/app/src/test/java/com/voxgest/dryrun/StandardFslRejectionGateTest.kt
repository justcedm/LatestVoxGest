package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardFslRejectionGateTest {
    private val config = StandardFslRejectionConfig(
        confidenceThreshold = 0.7f,
        marginThreshold = 0.2f,
        minimumPosePresenceRatio = 0.65f,
        minimumAnyHandPresenceRatio = 0.65f,
        requiredStableWindows = 2,
        cooldownMs = 1_000L
    )

    @Test
    fun everyUncertainStateRejectsWithHoldSignClearlyAndNoNothingAssumption() {
        val gate = StandardFslRejectionGate(config)
        val cases = listOf(
            gate.evaluate(null, quality(frames = 19), 0L),
            gate.evaluate(prediction(), quality(pose = 0.5f), 0L),
            gate.evaluate(prediction(), quality(hand = 0.5f), 0L),
            gate.evaluate(prediction(confidence = 0.69f), quality(), 0L),
            gate.evaluate(prediction(confidence = 0.8f, top2 = 0.65f), quality(), 0L)
        )
        cases.forEach { decision ->
            assertFalse(decision.accepted)
            assertEquals(StandardFslRejectionGate.HOLD_SIGN_CLEARLY, decision.displayText)
        }
    }

    @Test
    fun requiresTemporalStabilityThenAppliesCooldown() {
        val gate = StandardFslRejectionGate(config)
        val first = gate.evaluate(prediction(), quality(), 1_000L)
        assertFalse(first.accepted)
        assertEquals("TEMPORAL_STABILITY", first.reason)

        val second = gate.evaluate(prediction(), quality(), 1_010L)
        assertTrue(second.accepted)
        assertEquals("HELLO", second.displayText)

        gate.evaluate(prediction(label = "YES"), quality(), 1_020L)
        val cooldown = gate.evaluate(prediction(label = "YES"), quality(), 1_030L)
        assertFalse(cooldown.accepted)
        assertTrue(cooldown.cooldownActive)
        assertEquals("COOLDOWN", cooldown.reason)
        assertEquals(StandardFslRejectionGate.HOLD_SIGN_CLEARLY, cooldown.displayText)

        val heldAfterCooldown = gate.evaluate(prediction(), quality(), 2_020L)
        assertFalse(heldAfterCooldown.accepted)
        assertEquals("DUPLICATE_REQUIRES_RELEASE", heldAfterCooldown.reason)

        gate.reset()
        assertEquals("TEMPORAL_STABILITY", gate.evaluate(prediction(), quality(), 2_030L).reason)
        assertTrue(gate.evaluate(prediction(), quality(), 2_040L).accepted)
    }

    @Test
    fun rejectsHighConfidenceWindowWhenCurrentLandmarksAreMissing() {
        val gate = StandardFslRejectionGate(config)
        val result = gate.evaluate(
            prediction = prediction(confidence = 0.99f, top2 = 0.01f),
            window = quality(),
            nowMs = 1_000L,
            currentFrameUsable = false
        )

        assertFalse(result.accepted)
        assertEquals("CURRENT_FRAME_MISSING", result.reason)
    }

    @Test
    fun rejectsStaleOrGappedWindowsBeforeConsideringPrediction() {
        val gate = StandardFslRejectionGate(config)
        val stale = gate.evaluate(
            prediction(),
            quality(),
            nowMs = 10_000L,
            timing = timing(oldest = 4_001L)
        )
        assertEquals("STALE_WINDOW_OLDEST_FRAME", stale.reason)

        val gapped = gate.evaluate(
            prediction(),
            quality(),
            nowMs = 10_000L,
            timing = timing(maxGap = 601L)
        )
        assertEquals("MAX_FRAME_GAP_EXCEEDED", gapped.reason)
    }

    @Test
    fun diagnosticsContainAllRequiredDeveloperFields() {
        val line = StandardFslDiagnostics(
            posePresent = true,
            leftHandPresent = true,
            rightHandPresent = false,
            bufferFrames = 20,
            activeProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
            top1Label = "HELLO",
            top1Confidence = 0.8f,
            top2Label = "YES",
            top2Margin = 0.4f,
            accepted = false,
            rejectionReason = "COOLDOWN",
            inferenceLatencyMs = 12.5,
            eventState = StandardFslEventState.CANDIDATE,
            eventReason = "PREDICTION_CANDIDATE",
            activityScore = 0.02f,
            windowDurationMs = 1_900L,
            oldestFrameAgeMs = 2_000L,
            medianFrameGapMs = 100L,
            maxFrameGapMs = 120L
        ).toLogLine()

        listOf(
            "pose_present=true",
            "left_hand_present=true",
            "right_hand_present=false",
            "buffer=20/20",
            "active_profile=STANDARD_FSL_FULLSIGN225",
            "top1=HELLO",
            "confidence=0.8000",
            "top2=YES",
            "margin=0.4000",
            "accepted=false",
            "rejection=COOLDOWN",
            "latency_ms=12.500",
            "event_state=CANDIDATE",
            "event_reason=PREDICTION_CANDIDATE",
            "activity_score=0.0200",
            "WINDOW_DURATION_MS=1900",
            "OLDEST_FRAME_AGE_MS=2000",
            "MEDIAN_FRAME_GAP_MS=100",
            "MAX_FRAME_GAP_MS=120"
        ).forEach { expected -> assertTrue(line.contains(expected)) }
    }

    private fun prediction(
        label: String = "HELLO",
        confidence: Float = 0.8f,
        top2: Float = 0.3f
    ) = StandardFslPrediction(label, confidence, "OTHER", top2, 12.0)

    private fun quality(
        frames: Int = 20,
        pose: Float = 1f,
        hand: Float = 1f
    ): StandardFullSign225WindowQuality {
        return StandardFullSign225WindowQuality(
            frameCount = frames,
            posePresentFrames = (frames * pose).toInt(),
            leftHandPresentFrames = (frames * hand).toInt(),
            rightHandPresentFrames = 0,
            anyHandPresentFrames = (frames * hand).toInt(),
            bothHandsPresentFrames = 0
        )
    }

    private fun timing(
        oldest: Long = 2_000L,
        maxGap: Long = 100L
    ) = StandardFullSign225WindowTiming(
        available = true,
        chronological = true,
        windowDurationMs = 1_900L,
        oldestFrameAgeMs = oldest,
        medianFrameGapMs = 100L,
        maxFrameGapMs = maxGap
    )
}
