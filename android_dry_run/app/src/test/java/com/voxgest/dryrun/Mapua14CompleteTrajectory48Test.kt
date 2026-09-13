package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Mapua14CompleteTrajectory48Test {
    @Test
    fun `complete sequence is resampled to 48 instead of taking first 48`() {
        val complete = (0 until 80).map { frame ->
            FloatArray(Mapua14CompleteTrajectory48.FEATURE_SIZE) { frame.toFloat() }
        }

        val result = Mapua14CompleteTrajectory48.resampleComplete48(complete)

        assertEquals(48, result.size)
        assertEquals(0f, result.first()[0], 0f)
        assertEquals(79f, result.last()[0], 0f)
        assertEquals(23f * 79f / 47f, result[23][100], 1e-5f)
    }

    @Test
    fun `one-frame trajectory repeats independent frame copies exactly 48 times`() {
        val source = FloatArray(Mapua14CompleteTrajectory48.FEATURE_SIZE) { it.toFloat() }

        val result = Mapua14CompleteTrajectory48.resampleComplete48(listOf(source))

        assertEquals(48, result.size)
        assertTrue(result.all { it.contentEquals(source) })
        assertNotSame(result[0], result[1])
    }

    @Test
    fun `preparation applies exact adaptive envelope boundary and short-gap policy`() {
        val positions = listOf<Float?>(
            null, null, null,
            0.20f, 0.30f, 0.40f,
            null,
            0.40f, 0.40f, 0.40f, 0.40f, 0.40f
        )
        val frames = positions.mapIndexed { index, x ->
            frame(
                timestampMs = 100L + index * 10L,
                handX = x
            )
        }

        val result = Mapua14CompleteTrajectory48.prepare(frames)

        assertEquals(48, result.modelInput.size)
        assertEquals(225, result.modelInput.first().size)
        assertEquals(1, result.motionStartCaptureIndex)
        assertEquals(8, result.motionEndCaptureIndex)
        assertEquals(8, result.completeTrajectoryFrameCount)
        assertEquals(1, result.interpolatedLeftFrames)
        assertEquals(0, result.interpolatedRightFrames)
        assertEquals(8, result.quality.frameCount)
        assertEquals(8, result.quality.posePresentFrames)
        assertEquals(5, result.quality.leftHandPresentFrames)
        assertEquals(0, result.quality.rightHandPresentFrames)
        assertEquals(5, result.quality.anyHandPresentFrames)
        assertEquals(0, result.quality.bothHandsPresentFrames)
        assertEquals(1f, result.quality.posePresenceRatio, 0f)
        assertEquals(0.625f, result.quality.anyHandPresenceRatio, 0f)
        assertTrue(result.sourceTimestampsMs.isStrictlyChronological())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preparation fails closed on nonchronological capture`() {
        Mapua14CompleteTrajectory48.prepare(
            listOf(frame(200L, 0.2f), frame(100L, 0.3f))
        )
    }

    private fun frame(timestampMs: Long, handX: Float?): LandmarkFrame = LandmarkFrame(
        poseLandmarks = pose(raised = handX != null),
        leftHandLandmarks = handX?.let(::hand),
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

    private fun hand(x: Float): List<LandmarkPoint> = List(21) { index ->
        LandmarkPoint(x + index * 0.001f, 0.3f + index * 0.002f, index * 0.0005f)
    }

    private fun LongArray.isStrictlyChronological(): Boolean =
        (1 until size).all { index -> this[index] > this[index - 1] }
}
