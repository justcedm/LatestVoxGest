package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardFullSign225RollingBufferTest {
    @Test
    fun exposesProgressZeroThroughTwentyAndRollsAfterCapacity() {
        val buffer = StandardFullSign225RollingBuffer()
        assertEquals(0, buffer.size)
        assertNull(buffer.snapshot())

        repeat(20) { index ->
            val ready = buffer.add(feature(index.toFloat(), pose = true, left = true, right = false))
            assertEquals(index + 1, buffer.size)
            assertEquals(index == 19, ready)
        }
        assertTrue(buffer.isReady)
        assertEquals(20, buffer.capacity)
        assertNotNull(buffer.snapshot())

        buffer.add(feature(20f, pose = true, left = false, right = true))
        assertEquals(20, buffer.size)
        assertEquals(1f, buffer.snapshot()!![0][0], 0f)
        assertEquals(20f, buffer.snapshot()!![19][0], 0f)
    }

    @Test
    fun qualityCountsMissingFramesInsteadOfDroppingThem() {
        val buffer = StandardFullSign225RollingBuffer()
        repeat(10) { buffer.add(feature(0f, pose = true, left = true, right = false)) }
        repeat(5) { buffer.add(feature(0f, pose = true, left = false, right = true)) }
        repeat(5) { buffer.add(feature(0f, pose = false, left = false, right = false)) }

        val quality = buffer.quality()
        assertEquals(20, quality.frameCount)
        assertEquals(15, quality.posePresentFrames)
        assertEquals(10, quality.leftHandPresentFrames)
        assertEquals(5, quality.rightHandPresentFrames)
        assertEquals(15, quality.anyHandPresentFrames)
        assertEquals(0.75f, quality.posePresenceRatio, 0f)
        assertEquals(0.75f, quality.anyHandPresenceRatio, 0f)
    }

    @Test
    fun `timing reports exact freshness and frame gaps`() {
        val buffer = StandardFullSign225RollingBuffer()
        repeat(20) { index ->
            buffer.add(
                feature(0f, pose = true, left = true, right = false, timestampMs = 1_000L + index * 100L)
            )
        }

        val timing = buffer.timing(nowMs = 3_000L)
        assertTrue(timing.available)
        assertTrue(timing.chronological)
        assertEquals(1_900L, timing.windowDurationMs)
        assertEquals(2_000L, timing.oldestFrameAgeMs)
        assertEquals(100L, timing.medianFrameGapMs)
        assertEquals(100L, timing.maxFrameGapMs)
    }

    private fun feature(
        marker: Float,
        pose: Boolean,
        left: Boolean,
        right: Boolean,
        timestampMs: Long = 0L
    ): StandardFullSign225Frame {
        return StandardFullSign225Frame(
            FloatArray(225).also { it[0] = marker },
            StandardFullSign225FrameQuality(
                posePresent = pose,
                leftHandPresent = left,
                rightHandPresent = right,
                leftHandScale = 0f,
                rightHandScale = 0f,
                leftHandScaleApplied = false,
                rightHandScaleApplied = false,
                inputMirrored = false,
                issues = emptyList()
            ),
            timestampMs
        )
    }
}
