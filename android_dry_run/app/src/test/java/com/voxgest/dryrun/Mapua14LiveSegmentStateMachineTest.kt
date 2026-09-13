package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mapua14LiveSegmentStateMachineTest {
    private val config = Mapua14LiveSegmentConfig(
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
    fun `dynamic event follows complete state path and reports post-end timing`() {
        val machine = Mapua14LiveSegmentStateMachine(config)

        assertEquals(Mapua14LiveSegmentState.IDLE, machine.onFrame(neutral(100L)).state)
        assertEquals(Mapua14LiveSegmentState.ARMING, machine.onFrame(neutral(200L)).state)
        assertEquals(Mapua14LiveSegmentState.CAPTURING, machine.onFrame(sign(300L, 0.20f)).state)
        machine.onFrame(sign(400L, 0.22f))
        machine.onFrame(sign(500L, 0.24f))
        machine.onFrame(sign(600L, 0.24f))
        machine.onFrame(sign(700L, 0.24f))
        val complete = machine.onFrame(sign(800L, 0.24f))

        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        assertEquals(Mapua14SegmentCompletion.DYNAMIC_END, complete.completion)

        val inference = machine.finalizeForInference(850L)
        assertEquals(Mapua14LiveSegmentState.INFERENCE, machine.state)
        assertEquals(48, inference.prepared.modelInput.size)
        assertEquals(225, inference.prepared.modelInput.first().size)
        assertEquals(2, inference.retainedLeadingNeutralFrames)
        assertEquals(500L, inference.estimatedSignEndTimestampMs)
        assertEquals(350L, inference.endToInferenceReadyMs)

        val timing = machine.markInferenceFinished(1_000L)
        assertEquals(Mapua14LiveSegmentState.WAIT_FOR_RELEASE, machine.state)
        assertEquals(500L, timing.endToResultMs)
        assertTrue(timing.withinTarget)
        assertTrue(timing.withinP95Goal)

        assertEquals(
            Mapua14LiveSegmentState.WAIT_FOR_RELEASE,
            machine.onFrame(sign(1_100L, 0.24f)).state
        )
        assertEquals(
            Mapua14LiveSegmentState.WAIT_FOR_RELEASE,
            machine.onFrame(neutral(1_200L)).state
        )
        assertEquals(Mapua14LiveSegmentState.IDLE, machine.onFrame(neutral(1_300L)).state)
    }

    @Test
    fun `static handshape completes through stable location hold`() {
        val machine = Mapua14LiveSegmentStateMachine(config)
        machine.onFrame(neutral(100L))
        machine.onFrame(neutral(200L))

        machine.onFrame(sign(300L, 0.20f))
        machine.onFrame(sign(400L, 0.20f))
        machine.onFrame(sign(500L, 0.20f))
        val complete = machine.onFrame(sign(600L, 0.20f))

        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        assertEquals(Mapua14SegmentCompletion.STATIC_HOLD, complete.completion)
        val inference = machine.finalizeForInference(650L)
        assertEquals(300L, inference.estimatedSignEndTimestampMs)
        assertEquals(48, inference.prepared.modelInput.size)
    }

    @Test
    fun `return to neutral retains a three-frame trailing boundary`() {
        val machine = Mapua14LiveSegmentStateMachine(
            config.copy(dynamicEndStableFrames = 8, staticHoldFrames = 8)
        )
        machine.onFrame(neutral(100L))
        machine.onFrame(neutral(200L))
        machine.onFrame(sign(300L, 0.20f))
        machine.onFrame(sign(400L, 0.22f))
        machine.onFrame(sign(500L, 0.30f))

        machine.onFrame(neutral(600L))
        machine.onFrame(neutral(700L))
        val complete = machine.onFrame(neutral(800L))

        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        assertEquals(Mapua14SegmentCompletion.NEUTRAL_RETURN, complete.completion)
        val inference = machine.finalizeForInference(810L)
        assertTrue(inference.prepared.sourceTimestampsMs.contains(800L))
    }

    @Test
    fun `out-of-order sample is ignored and never enters captured chronology`() {
        val machine = Mapua14LiveSegmentStateMachine(config)
        machine.onFrame(neutral(100L))
        machine.onFrame(neutral(200L))
        val started = machine.onFrame(sign(300L, 0.20f))

        val ignored = machine.onFrame(sign(250L, 0.90f))

        assertEquals("NON_CHRONOLOGICAL_FRAME_IGNORED", ignored.reason)
        assertEquals(started.capturedFrameCount, ignored.capturedFrameCount)
        machine.onFrame(sign(400L, 0.20f))
        machine.onFrame(sign(500L, 0.20f))
        val complete = machine.onFrame(sign(600L, 0.20f))
        assertEquals(Mapua14LiveSegmentState.FINALIZING, complete.state)
        val timestamps = machine.finalizeForInference(650L).prepared.sourceTimestampsMs
        assertFalse(timestamps.contains(250L))
        assertTrue(timestamps.isStrictlyChronological())
    }

    private fun neutral(timestampMs: Long): LandmarkFrame = LandmarkFrame(
        poseLandmarks = pose(raised = false),
        leftHandLandmarks = null,
        rightHandLandmarks = null,
        timestampMs = timestampMs
    )

    private fun sign(timestampMs: Long, x: Float): LandmarkFrame = LandmarkFrame(
        poseLandmarks = pose(raised = true),
        leftHandLandmarks = List(21) { index ->
            LandmarkPoint(x + index * 0.001f, 0.25f + index * 0.002f, index * 0.0005f)
        },
        rightHandLandmarks = null,
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

    private fun LongArray.isStrictlyChronological(): Boolean =
        (1 until size).all { index -> this[index] > this[index - 1] }
}
