package com.voxgest.app.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Core3FramePacingTrackerTest {
    @Test
    fun `measures smooth 60 hertz cadence without jank`() {
        val tracker = Core3FramePacingTracker(sampleCapacity = 16)
        tracker.reset(60f)
        var frameTime = 1_000_000_000L
        repeat(11) {
            tracker.recordFrame(frameTime, rendered = true)
            frameTime += 16_666_667L
        }

        val snapshot = tracker.snapshot()
        assertEquals(10L, snapshot.measuredFrames)
        assertEquals(10L, snapshot.renderedFrames)
        assertTrue(snapshot.averageFps in 59.9f..60.1f)
        assertTrue(snapshot.frameTimeP50Ms in 16.6f..16.8f)
        assertTrue(snapshot.frameTimeP95Ms in 16.6f..16.8f)
        assertEquals(0L, snapshot.jankyFrames)
        assertEquals(0f, snapshot.jankPercent)
        assertEquals(0L, snapshot.estimatedDroppedFrames)
    }

    @Test
    fun `counts a missed vsync and renderer skip`() {
        val tracker = Core3FramePacingTracker(sampleCapacity = 8)
        tracker.reset(60f)
        tracker.recordFrame(1_000_000_000L, rendered = true)
        tracker.recordFrame(1_016_666_667L, rendered = true)
        tracker.recordFrame(1_050_000_001L, rendered = false)

        val snapshot = tracker.snapshot()
        assertEquals(2L, snapshot.measuredFrames)
        assertEquals(1L, snapshot.renderedFrames)
        assertEquals(1L, snapshot.jankyFrames)
        assertEquals(50f, snapshot.jankPercent)
        assertEquals(1L, snapshot.estimatedDroppedFrames)
        assertEquals(1L, snapshot.renderSkippedFrames)
    }

    @Test
    fun `pause excludes background time from frame metrics`() {
        val tracker = Core3FramePacingTracker(sampleCapacity = 8)
        tracker.reset(120f)
        tracker.recordFrame(1_000_000_000L, rendered = true)
        tracker.recordFrame(1_008_333_333L, rendered = true)
        tracker.pause()
        tracker.recordFrame(9_000_000_000L, rendered = true)
        tracker.recordFrame(9_008_333_333L, rendered = true)

        val snapshot = tracker.snapshot()
        assertEquals(2L, snapshot.measuredFrames)
        assertEquals(0L, snapshot.jankyFrames)
        assertTrue(snapshot.averageFps in 119.9f..120.1f)
    }

    @Test
    fun `measures animation update cadence and action switch latency`() {
        val tracker = Core3FramePacingTracker(sampleCapacity = 16)
        tracker.reset(60f)
        tracker.beginAction()
        var frameTime = 2_000_000_000L
        repeat(7) {
            tracker.recordAnimationUpdate(frameTime)
            frameTime += 16_666_667L
        }
        tracker.recordActionSwitchLatency(7_250_000L)

        val snapshot = tracker.snapshot()
        assertEquals(7L, snapshot.animationUpdates)
        assertTrue(snapshot.animationUpdateFps in 59.9f..60.1f)
        assertEquals(7.25f, snapshot.actionSwitchLatencyMs)
    }
}
