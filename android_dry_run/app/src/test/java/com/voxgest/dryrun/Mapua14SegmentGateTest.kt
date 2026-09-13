package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mapua14SegmentGateTest {
    @Test
    fun completedHighQualityTrajectoryAcceptsOneInferenceWithoutRollingWindowConfirmation() {
        val result = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.91f, second = 0.10f),
            trajectory(),
            resultTimestampMs = 1_200L
        )

        assertTrue(result.decision.accepted)
        assertEquals("ACCEPTED", result.decision.reason)
        assertEquals(1, result.decision.stableWindowCount)
        assertEquals(900L, result.timing.windowDurationMs)
    }

    @Test
    fun confidenceMarginAndTrackingThresholdsRemainFailClosed() {
        val lowConfidence = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.69f, second = 0.10f),
            trajectory(),
            1_200L
        )
        val lowMargin = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.80f, second = 0.61f),
            trajectory(),
            1_200L
        )
        val lowHand = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.91f, second = 0.10f),
            trajectory(anyHandFrames = 6),
            1_200L
        )

        assertFalse(lowConfidence.decision.accepted)
        assertEquals("LOW_CONFIDENCE", lowConfidence.decision.reason)
        assertFalse(lowMargin.decision.accepted)
        assertEquals("LOW_MARGIN", lowMargin.decision.reason)
        assertFalse(lowHand.decision.accepted)
        assertEquals("LOW_HAND_PRESENCE", lowHand.decision.reason)
    }

    @Test
    fun captureLimitAndLandmarkGapsRejectEvenWhenClassifierIsConfident() {
        val captureLimit = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.99f, second = 0.0f),
            trajectory(completion = Mapua14SegmentCompletion.CAPTURE_LIMIT),
            1_200L
        )
        val largeGap = Mapua14SegmentGate.evaluate(
            inference(confidence = 0.99f, second = 0.0f),
            trajectory(timestamps = longArrayOf(100L, 200L, 300L, 1_100L, 1_200L)),
            1_300L
        )

        assertEquals("CAPTURE_LIMIT_WITHOUT_SIGN_END", captureLimit.decision.reason)
        assertEquals("MAX_FRAME_GAP_EXCEEDED", largeGap.decision.reason)
    }

    private fun inference(confidence: Float, second: Float): StandardFslInference {
        val top1 = StandardFslRankedPrediction(3, "HELLO", confidence)
        val top2 = StandardFslRankedPrediction(2, "FOUR", second)
        return StandardFslInference(
            probabilities = floatArrayOf(0f, 0f, second, confidence),
            top1 = top1,
            top2 = top2,
            top5 = listOf(top1, top2),
            latencyMs = 1.0
        )
    }

    private fun trajectory(
        anyHandFrames: Int = 10,
        completion: Mapua14SegmentCompletion = Mapua14SegmentCompletion.DYNAMIC_END,
        timestamps: LongArray = LongArray(10) { index -> 100L + index * 100L }
    ): Mapua14InferenceTrajectory {
        val frameCount = timestamps.size
        val prepared = Mapua14PreparedTrajectory(
            modelInput = Array(48) { FloatArray(225) },
            capturedFrameCount = frameCount,
            motionStartCaptureIndex = 0,
            motionEndCaptureIndex = frameCount - 1,
            completeTrajectoryFrameCount = frameCount,
            interpolatedLeftFrames = 0,
            interpolatedRightFrames = 0,
            quality = StandardFullSign225WindowQuality(
                frameCount = frameCount,
                posePresentFrames = frameCount,
                leftHandPresentFrames = anyHandFrames,
                rightHandPresentFrames = 0,
                anyHandPresentFrames = anyHandFrames,
                bothHandsPresentFrames = 0
            ),
            sourceTimestampsMs = timestamps
        )
        return Mapua14InferenceTrajectory(
            prepared = prepared,
            completion = completion,
            signStartTimestampMs = timestamps.first(),
            estimatedSignEndTimestampMs = timestamps.last(),
            completionDetectedTimestampMs = timestamps.last(),
            inferenceReadyTimestampMs = timestamps.last(),
            endToInferenceReadyMs = 0L,
            targetPostEndResultMs = 2_000L,
            p95PostEndResultGoalMs = 3_000L,
            retainedLeadingNeutralFrames = 0
        )
    }
}
