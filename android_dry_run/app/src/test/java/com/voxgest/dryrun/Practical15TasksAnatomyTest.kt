package com.voxgest.dryrun

import org.junit.Assert.*
import org.junit.Test

class Practical15TasksAnatomyTest {
    private val pose = List(33) { LandmarkPoint(.5f + it * .001f, .3f, .1f) }
    private val left = List(21) { LandmarkPoint(.2f + it * .01f, .4f, .05f) }
    private val right = List(21) { LandmarkPoint(.6f + it * .01f, .5f, .03f) }
    private fun raw(vararg hands: Practical15TasksAnatomy.Detection) = Practical15TasksAnatomy.frame(pose, hands.toList(), 100)
    private fun vector(frame: LandmarkFrame) = StandardFullSign225FeatureBuilder.build(frame).vector
    private fun leftOnly() = raw(Practical15TasksAnatomy.Detection("Left", left, .99f))
    private fun rightOnly() = raw(Practical15TasksAnatomy.Detection("Right", right, .99f))
    @Test fun physicalLeftPopulatesOnlyLeftFeatureBlock() {
        val f = leftOnly(); assertEquals(left, f.leftHandLandmarks)
        val v = vector(f); assertTrue(v.sliceArray(99..161).any { it != 0f }); assertTrue(v.sliceArray(162..224).all { it == 0f })
    }
    @Test fun physicalRightPopulatesOnlyRightFeatureBlock() {
        val f = rightOnly(); assertEquals(right, f.rightHandLandmarks)
        val v = vector(f); assertTrue(v.sliceArray(162..224).any { it != 0f }); assertTrue(v.sliceArray(99..161).all { it == 0f })
    }
    @Test fun bothHandsIndependentOfDetectionOrder() {
        val f = raw(Practical15TasksAnatomy.Detection("Right", right), Practical15TasksAnatomy.Detection("Left", left))
        assertArrayEquals(vector(leftOnly()).sliceArray(99..161), vector(f).sliceArray(99..161), 0f)
        assertArrayEquals(vector(rightOnly()).sliceArray(162..224), vector(f).sliceArray(162..224), 0f)
    }
    @Test fun missingLeftKeepsPoseAndRight() {
        val f = rightOnly(); assertFalse(f.hasLeftHand); assertTrue(f.hasPose && f.hasRightHand)
        assertTrue(vector(f).sliceArray(99..161).all { it == 0f })
    }
    @Test fun missingRightKeepsPoseAndLeft() {
        val f = leftOnly(); assertFalse(f.hasRightHand); assertTrue(f.hasPose && f.hasLeftHand)
        assertTrue(vector(f).sliceArray(162..224).all { it == 0f })
    }
    @Test fun coordinatesNotMirroredOrMutated() {
        assertFalse(Practical15TasksAnatomy.ANALYSIS_MIRRORED)
        val f = leftOnly(); assertEquals(.2f, f.leftHandLandmarks!![0].x, 0f)
        PreviewOverlayMapper.xForPreview(f.leftHandLandmarks[0].x, false, true)
        assertEquals(.2f, f.leftHandLandmarks[0].x, 0f)
    }
    @Test fun fullsignShapeOrderAndResampleUnchanged() {
        val v = vector(leftOnly()); assertEquals(225, v.size)
        assertArrayEquals(vector(raw()).sliceArray(0..98), v.sliceArray(0..98), 0f)
        assertEquals(48, CompleteEventResampler48.resample(listOf(v, v)).size)
    }
    @Test fun unknownHandNeverUsesImageX() {
        val f = raw(Practical15TasksAnatomy.Detection("unknown", left))
        assertFalse(f.hasAnyHand); assertEquals("unassigned", f.handObservations.single().slot)
    }
    @Test(expected = IllegalArgumentException::class) fun mirroredInputFailsClosed() {
        Practical15TasksAnatomy.frame(pose, emptyList(), 1, analysisMirrored = true)
    }
}
