package com.voxgest.dryrun

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StandardFullSign225FeatureBuilderTest {
    @Test
    fun rightOnlyPopulatesOnlyAnatomicalRightSlot() {
        val result = StandardFullSign225FeatureBuilder.build(frame(left = null, right = hand(0.7f)))

        assertTrue(result.vector.copyOfRange(99, 162).all { it == 0f })
        assertTrue(result.vector.copyOfRange(162, 225).any { it != 0f })
        assertFalse(result.quality.leftHandPresent)
        assertTrue(result.quality.rightHandPresent)
    }

    @Test
    fun leftOnlyPopulatesOnlyAnatomicalLeftSlot() {
        val result = StandardFullSign225FeatureBuilder.build(frame(left = hand(0.2f), right = null))

        assertTrue(result.vector.copyOfRange(99, 162).any { it != 0f })
        assertTrue(result.vector.copyOfRange(162, 225).all { it == 0f })
        assertTrue(result.quality.leftHandPresent)
        assertFalse(result.quality.rightHandPresent)
    }

    @Test
    fun bothHandsStayInFixedSlotsAndOutputIsDeterministicFiniteFloatArray() {
        val input = frame(left = hand(0.2f), right = hand(0.7f))
        val first = StandardFullSign225FeatureBuilder.build(input)
        val second = StandardFullSign225FeatureBuilder.build(input)

        assertEquals(225, first.vector.size)
        assertTrue(first.vector.all { it.isFinite() })
        assertArrayEquals(first.vector, second.vector, 0f)
        assertTrue(first.quality.leftHandScaleApplied)
        assertTrue(first.quality.rightHandScaleApplied)
        assertTrue(first.vector.copyOfRange(99, 162).any { it != 0f })
        assertTrue(first.vector.copyOfRange(162, 225).any { it != 0f })
    }

    @Test
    fun appliesNoseTranslationIndependentHandScalingAndGlobalZDamping() {
        val result = StandardFullSign225FeatureBuilder.build(frame(left = hand(0.2f), right = hand(0.7f)))

        assertEquals(0f, result.vector[0], 0f)
        assertEquals(0f, result.vector[1], 0f)
        assertEquals(0f, result.vector[2], 0f)
        val expectedPoseOneZ = (pose()[1].z - pose()[0].z) * 0.3f
        assertEquals(expectedPoseOneZ, result.vector[5], 1.0e-7f)
        assertEquals(1f, result.quality.leftHandScale, 1.0e-5f)
        assertEquals(1f, result.quality.rightHandScale, 1.0e-5f)
    }

    @Test
    fun missingHandsZeroFillOnlyTheirSlotsAndMissingPoseZeroFillsWholeFrame() {
        val noHands = StandardFullSign225FeatureBuilder.build(frame(left = null, right = null))
        assertTrue(noHands.vector.copyOfRange(99, 225).all { it == 0f })
        assertTrue(noHands.quality.posePresent)

        val noPose = StandardFullSign225FeatureBuilder.build(
            LandmarkFrame(null, hand(0.2f), hand(0.7f), 1L)
        )
        assertTrue(noPose.vector.all { it == 0f })
        assertFalse(noPose.quality.posePresent)
        assertTrue(noPose.quality.leftHandPresent)
        assertTrue(noPose.quality.rightHandPresent)
    }

    @Test
    fun rejectsMirroredMalformedAndNonFiniteInputs() {
        expectIllegalArgument {
            StandardFullSign225FeatureBuilder.build(frame(), inputMirrored = true)
        }
        expectIllegalArgument {
            StandardFullSign225FeatureBuilder.build(
                LandmarkFrame(pose().dropLast(1), hand(0.2f), null, 1L)
            )
        }
        val bad = pose().toMutableList().also { it[3] = LandmarkPoint(Float.NaN, 0f, 0f) }
        expectIllegalArgument {
            StandardFullSign225FeatureBuilder.build(LandmarkFrame(bad, null, null, 1L))
        }
    }

    private fun frame(
        left: List<LandmarkPoint>? = hand(0.2f),
        right: List<LandmarkPoint>? = hand(0.7f)
    ): LandmarkFrame = LandmarkFrame(pose(), left, right, 1L)

    private fun pose(): List<LandmarkPoint> {
        return List(33) { index ->
            LandmarkPoint(
                x = 0.5f + index * 0.002f,
                y = 0.4f + index * 0.003f,
                z = 0.1f + index * 0.004f
            )
        }
    }

    private fun hand(baseX: Float): List<LandmarkPoint> {
        return List(21) { index ->
            when (index) {
                0 -> LandmarkPoint(baseX, 0.5f, 0.1f)
                9 -> LandmarkPoint(baseX + 1f, 0.5f, 0.1f)
                else -> LandmarkPoint(baseX + index * 0.02f, 0.5f + index * 0.01f, 0.1f + index * 0.005f)
            }
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
