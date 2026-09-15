package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fsl105LiveSegmentRuntimeTest {
    private val temporalProfile = CompleteSignTemporalProfiles.standardFsl105(20, 225)
    private val eventConfig = Mapua14LiveSegmentConfig(
        armingNeutralFrames = 2,
        releaseNeutralFrames = 2,
        dynamicMotionFrames = 2,
        minimumDynamicPath = 0.015f,
        dynamicEndStableFrames = 3,
        staticHoldFrames = 4,
        minimumSignFrames = 3,
        maximumCaptureFrames = 100
    )

    @Test
    fun `complete event is resampled to exactly 20 instead of first or latest 20`() {
        val complete = (0 until 80).map { frame -> FloatArray(225) { frame.toFloat() } }

        val result = CompleteSignTrajectoryFinalizer.resampleComplete(complete, 20)

        assertEquals(20, result.size)
        assertEquals(0f, result.first()[0], 0f)
        assertEquals(79f, result.last()[224], 0f)
        assertEquals(10f * 79f / 19f, result[10][100], 1e-5f)
    }

    @Test
    fun `Standard profile preserves anatomical slots and observed missing-hand zero fill`() {
        val leftOnly = CompleteSignTrajectoryFinalizer.prepare(
            listOf(frame(100L, leftX = 0.2f, rightX = null)),
            temporalProfile
        )
        val rightOnly = CompleteSignTrajectoryFinalizer.prepare(
            listOf(frame(100L, leftX = null, rightX = 0.8f)),
            temporalProfile
        )

        assertEquals(20, leftOnly.modelInput.size)
        assertTrue(leftOnly.modelInput.all { vector ->
            vector.copyOfRange(99, 162).any { it != 0f } &&
                vector.copyOfRange(162, 225).all { it == 0f }
        })
        assertTrue(rightOnly.modelInput.all { vector ->
            vector.copyOfRange(99, 162).all { it == 0f } &&
                vector.copyOfRange(162, 225).any { it != 0f }
        })
        assertEquals(0, leftOnly.interpolatedLeftFrames)
        assertEquals(0, rightOnly.interpolatedRightFrames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed landmark dimensions fail before an input can be fabricated`() {
        val malformed = frame(100L, 0.2f, null).copy(
            leftHandLandmarks = List(20) { LandmarkPoint(0.2f, 0.3f, 0f) }
        )
        CompleteSignTrajectoryFinalizer.prepare(listOf(malformed), temporalProfile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonchronological complete event fails closed`() {
        CompleteSignTrajectoryFinalizer.prepare(
            listOf(frame(200L, 0.2f, null), frame(100L, 0.3f, null)),
            temporalProfile
        )
    }

    @Test
    fun `state path performs one inference then requires release before same sign can rearm`() {
        val machine = Fsl105LiveSegmentStateMachine(temporalProfile, eventConfig)
        machine.onFrame(neutral(100L))
        assertEquals(Mapua14LiveSegmentState.ARMING, machine.onFrame(neutral(200L)).state)
        machine.onFrame(frame(300L, 0.20f, null))
        machine.onFrame(frame(400L, 0.22f, null))
        machine.onFrame(frame(500L, 0.24f, null))
        machine.onFrame(frame(600L, 0.24f, null))
        machine.onFrame(frame(700L, 0.24f, null))
        val complete = machine.onFrame(frame(800L, 0.24f, null))

        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        val trajectory = machine.finalizeForInference(850L)
        assertEquals(20, trajectory.prepared.modelInput.size)
        machine.markInferenceFinished(900L)
        assertEquals(
            Mapua14LiveSegmentState.WAIT_FOR_RELEASE,
            machine.onFrame(frame(1_000L, 0.24f, null)).state
        )
        machine.onFrame(neutral(1_100L))
        assertEquals(Mapua14LiveSegmentState.IDLE, machine.onFrame(neutral(1_200L)).state)
        machine.onFrame(neutral(1_300L))
        assertEquals(Mapua14LiveSegmentState.ARMING, machine.onFrame(neutral(1_400L)).state)
        assertEquals(
            Mapua14LiveSegmentState.CAPTURING,
            machine.onFrame(frame(1_500L, 0.20f, null)).state
        )
    }

    @Test
    fun `camera timestamp offset cannot break monotonic process timing at finalization`() {
        val machine = Fsl105LiveSegmentStateMachine(temporalProfile, eventConfig)
        val cameraBase = 2_000_000L
        machine.onFrame(neutral(cameraBase + 100L), 100L)
        machine.onFrame(neutral(cameraBase + 200L), 200L)
        machine.onFrame(frame(cameraBase + 300L, 0.20f, null), 300L)
        machine.onFrame(frame(cameraBase + 400L, 0.22f, null), 400L)
        machine.onFrame(frame(cameraBase + 500L, 0.24f, null), 500L)
        machine.onFrame(frame(cameraBase + 600L, 0.24f, null), 600L)
        machine.onFrame(frame(cameraBase + 700L, 0.24f, null), 700L)
        val complete = machine.onFrame(frame(cameraBase + 800L, 0.24f, null), 800L)

        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        val trajectory = machine.finalizeForInference(850L)
        assertEquals(300L, trajectory.signStartTimestampMs)
        assertEquals(800L, trajectory.completionDetectedTimestampMs)
        assertTrue(trajectory.prepared.sourceTimestampsMs.first() > 1_000_000L)

        val evaluation = Fsl105SegmentGate.evaluate(inference(), trajectory, 900L)
        assertEquals(600L, evaluation.timing.oldestFrameAgeMs)
        assertEquals(400L, machine.markInferenceFinished(900L).endToResultMs)
    }

    @Test
    fun `missing pose capture limit and ambiguous identity reject despite confident raw output`() {
        val missingPose = Fsl105SegmentGate.evaluate(
            inference(),
            trajectory(poseFrames = 9),
            1_200L
        )
        val captureLimit = Fsl105SegmentGate.evaluate(
            inference(),
            trajectory(completion = Mapua14SegmentCompletion.CAPTURE_LIMIT),
            1_200L
        )
        val ambiguous = Fsl105SegmentGate.evaluate(
            inference(),
            trajectory(),
            1_200L,
            "AMBIGUOUS_ANATOMICAL_COLLISION"
        )

        assertFalse(missingPose.decision.accepted)
        assertEquals("MISSING_REQUIRED_POSE_REFERENCE", missingPose.decision.reason)
        assertEquals("CAPTURE_LIMIT_WITHOUT_SIGN_END", captureLimit.decision.reason)
        assertEquals("AMBIGUOUS_ANATOMICAL_COLLISION", ambiguous.decision.reason)
    }

    @Test
    fun `confidence margin and raw hand presence remain fail closed`() {
        assertEquals(
            "LOW_CONFIDENCE",
            Fsl105SegmentGate.evaluate(inference(0.69f, 0.1f), trajectory(), 1_200L)
                .decision.reason
        )
        assertEquals(
            "LOW_MARGIN",
            Fsl105SegmentGate.evaluate(inference(0.80f, 0.61f), trajectory(), 1_200L)
                .decision.reason
        )
        assertEquals(
            "LOW_HAND_PRESENCE",
            Fsl105SegmentGate.evaluate(inference(), trajectory(anyHandFrames = 6), 1_200L)
                .decision.reason
        )
    }

    private fun inference(confidence: Float = 0.91f, second: Float = 0.10f): StandardFslInference {
        val top1 = StandardFslRankedPrediction(41, "HELLO", confidence)
        val top2 = StandardFslRankedPrediction(102, "YES", second)
        return StandardFslInference(
            floatArrayOf(second, confidence),
            top1,
            top2,
            listOf(top1, top2),
            1.0
        )
    }

    private fun trajectory(
        poseFrames: Int = 10,
        anyHandFrames: Int = 10,
        completion: Mapua14SegmentCompletion = Mapua14SegmentCompletion.DYNAMIC_END
    ): Fsl105InferenceTrajectory {
        val timestamps = LongArray(10) { 100L + it * 100L }
        val prepared = CompleteSignPreparedTrajectory(
            temporalProfileId = Fsl105LiveSegmentProfile.ID,
            modelInput = Array(20) { FloatArray(225) },
            capturedFrameCount = 10,
            motionStartCaptureIndex = 0,
            motionEndCaptureIndex = 9,
            completeTrajectoryFrameCount = 10,
            interpolatedLeftFrames = 0,
            interpolatedRightFrames = 0,
            quality = StandardFullSign225WindowQuality(
                10,
                poseFrames,
                anyHandFrames,
                0,
                anyHandFrames,
                0
            ),
            sourceTimestampsMs = timestamps
        )
        return Fsl105InferenceTrajectory(
            prepared,
            completion,
            100L,
            1_000L,
            1_000L,
            1_000L,
            0L,
            2_000L,
            3_000L,
            0
        )
    }

    private fun neutral(timestampMs: Long): LandmarkFrame = LandmarkFrame(
        poseLandmarks = pose(false),
        leftHandLandmarks = null,
        rightHandLandmarks = null,
        timestampMs = timestampMs
    )

    private fun frame(timestampMs: Long, leftX: Float?, rightX: Float?): LandmarkFrame = LandmarkFrame(
        poseLandmarks = pose(leftX != null || rightX != null),
        leftHandLandmarks = leftX?.let(::hand),
        rightHandLandmarks = rightX?.let(::hand),
        timestampMs = timestampMs
    )

    private fun pose(raised: Boolean): List<LandmarkPoint> = List(33) { index ->
        when (index) {
            0 -> LandmarkPoint(0.5f, 0.2f, 0f)
            15, 16 -> LandmarkPoint(0.5f, if (raised) 0.35f else 0.8f, 0f)
            23, 24 -> LandmarkPoint(0.5f, 0.7f, 0f)
            else -> LandmarkPoint(0.5f + index * 0.001f, 0.5f, index * 0.0001f)
        }
    }

    private fun hand(x: Float): List<LandmarkPoint> = List(21) { index ->
        LandmarkPoint(x + index * 0.001f, 0.25f + index * 0.002f, index * 0.0005f)
    }
}
