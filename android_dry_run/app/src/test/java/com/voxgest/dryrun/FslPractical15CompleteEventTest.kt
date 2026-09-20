package com.voxgest.dryrun

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FslPractical15CompleteEventTest {
    @Test
    fun exact48IsCopiedAndEndpointsStayExactForShortAndLongEvents() {
        val exact = (0 until 48).map(::vector)
        val exactOut = CompleteEventResampler48.resample(exact)
        exactOut.indices.forEach { assertArrayEquals(exact[it], exactOut[it], 0f) }
        exactOut[0][0] = -9f
        assertEquals(0f, exact[0][0], 0f)
        listOf(8, 73).forEach { count ->
            val output = CompleteEventResampler48.resample((0 until count).map(::vector))
            assertEquals(48, output.size)
            assertEquals(0f, output.first()[0], 0f)
            assertEquals((count - 1).toFloat(), output.last()[0], 0f)
        }
    }

    @Test
    fun neutralSignNeutralProducesOneCandidateAndRequiresRelease() {
        val collector = FslPractical15CompleteEventCollector()
        repeat(3) { collector.onFrame(frame(it.toLong(), pose = true)) }
        assertEquals(FslPractical15CaptureState.PRIMING, collector.state)
        repeat(12) { collector.onFrame(frame((10 + it).toLong(), pose = true, left = true)) }
        assertNull(collector.onFrame(frame(30, pose = true)).candidate)
        assertNull(collector.onFrame(frame(31, pose = true)).candidate)
        val complete = collector.onFrame(frame(32, pose = true))
        assertEquals(FslPractical15CaptureState.CANDIDATE, complete.state)
        assertNotNull(complete.candidate)
        assertEquals(12, complete.candidate!!.quality.rawFrameCount)
        assertEquals(48, complete.candidate!!.resampledWindow.size)
        collector.markCandidateHandled()
        repeat(2) { assertNull(collector.onFrame(frame((40 + it).toLong(), pose = true)).candidate) }
        assertEquals(FslPractical15CaptureState.WAIT_FOR_RELEASE, collector.state)
        collector.onFrame(frame(42, pose = true))
        assertEquals(FslPractical15CaptureState.IDLE, collector.state)
        repeat(5) { assertNull(collector.onFrame(frame((50 + it).toLong(), pose = true, left = true)).candidate) }
    }

    @Test
    fun leftRightAndBothHandsPreserveAnatomicalQualityCounts() {
        val collector = armedCollector()
        repeat(3) { collector.onFrame(frame((10 + it).toLong(), pose = true, left = true)) }
        repeat(3) { collector.onFrame(frame((20 + it).toLong(), pose = true, right = true)) }
        repeat(3) { collector.onFrame(frame((30 + it).toLong(), pose = true, left = true, right = true)) }
        val candidate = finish(collector, 40)
        assertEquals(6, candidate.quality.leftHandPresentFrames)
        assertEquals(6, candidate.quality.rightHandPresentFrames)
        assertEquals(9, candidate.quality.anyHandPresentFrames)
    }

    @Test
    fun dropoutPoseLossAndIncompleteEventsFailSafely() {
        val collector = armedCollector()
        repeat(8) { collector.onFrame(frame((10 + it).toLong(), pose = true, left = true)) }
        collector.onFrame(frame(20, pose = true))
        collector.onFrame(frame(21, pose = true, left = true))
        val candidate = finish(collector, 30)
        assertEquals(10, candidate.quality.rawFrameCount)
        assertEquals(9, candidate.quality.anyHandPresentFrames)

        val noPose = armedCollector()
        repeat(4) { noPose.onFrame(frame((100 + it).toLong(), pose = true, right = true)) }
        repeat(2) { assertEquals("TRANSIENT_POSE_DROPOUT", noPose.onFrame(frame((110 + it).toLong())).reason) }
        assertEquals("POSE_TRACKING_LOST", noPose.onFrame(frame(112)).reason)
        assertEquals(FslPractical15CaptureState.WAIT_FOR_RELEASE, noPose.state)

        val partial = armedCollector()
        repeat(7) { partial.onFrame(frame((200 + it).toLong(), pose = true, left = true)) }
        repeat(2) { partial.onFrame(frame((210 + it).toLong(), pose = true)) }
        assertEquals("INCOMPLETE_EVENT_REJECTED", partial.onFrame(frame(212, pose = true)).reason)
    }

    @Test
    fun eventTimeoutFailsClosed() {
        val collector = armedCollector()
        collector.onFrame(frame(10, pose = true, left = true))
        val timeout = collector.onFrame(frame(8_011, pose = true, left = true))
        assertEquals("EVENT_TIMEOUT", timeout.reason)
        assertEquals(FslPractical15CaptureState.WAIT_FOR_RELEASE, timeout.state)
    }

    private fun armedCollector() = FslPractical15CompleteEventCollector().also { collector ->
        repeat(3) { collector.onFrame(frame(it.toLong(), pose = true)) }
    }

    private fun finish(collector: FslPractical15CompleteEventCollector, timestamp: Long): FslPractical15Candidate {
        repeat(2) { collector.onFrame(frame(timestamp + it, pose = true)) }
        return requireNotNull(collector.onFrame(frame(timestamp + 2, pose = true)).candidate)
    }

    private fun vector(index: Int) = FloatArray(225) { feature -> index.toFloat() + feature / 1000f }

    private fun frame(timestamp: Long, pose: Boolean = false, left: Boolean = false, right: Boolean = false): LandmarkFrame {
        val posePoints = if (pose) List(33) { index -> LandmarkPoint(index / 100f, 0.8f + index / 1000f, 0f) } else null
        fun hand(offset: Float) = List(21) { index ->
            LandmarkPoint(offset + index / 100f, 0.3f + index / 200f, index / 1000f)
        }
        return LandmarkFrame(
            poseLandmarks = posePoints,
            leftHandLandmarks = if (left) hand(0.1f) else null,
            rightHandLandmarks = if (right) hand(0.6f) else null,
            timestampMs = timestamp
        )
    }
}
