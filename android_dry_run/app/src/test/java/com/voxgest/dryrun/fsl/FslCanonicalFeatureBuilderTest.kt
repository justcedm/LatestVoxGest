package com.voxgest.dryrun.fsl

import com.voxgest.dryrun.LandmarkPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FslCanonicalFeatureBuilderTest {
    @Test
    fun matchesPythonGoldenFixtureIncludingMissingDataCases() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/golden_feature_fixture_f32.bin"))
            .use { it.readBytes() }
        assertEquals(648 * Float.SIZE_BYTES, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val values = FloatArray(648) { buffer.float }
        val pose = values.copyOfRange(0, 99).toPoints(33)
        val rightHand = values.copyOfRange(99, 162).toPoints(21)

        val full = FslCanonicalFeatureBuilder.build(pose, rightHand)
        assertArrayEquals(values.copyOfRange(162, 324), full.vector, 1.0e-6f)
        assertTrue(full.posePresent)
        assertTrue(full.handPresent)

        val missingHand = FslCanonicalFeatureBuilder.build(pose, null)
        assertArrayEquals(values.copyOfRange(324, 486), missingHand.vector, 1.0e-6f)
        assertTrue(missingHand.posePresent)
        assertFalse(missingHand.handPresent)

        val missingPose = FslCanonicalFeatureBuilder.build(null, rightHand)
        assertArrayEquals(values.copyOfRange(486, 648), missingPose.vector, 1.0e-6f)
        assertFalse(missingPose.posePresent)
        assertTrue(missingPose.handPresent)
    }

    @Test
    fun androidTokensPreserveTrainingIndexSemantics() {
        assertEquals("DEAF_BLIND", FslContract.androidToken("DEAF BLIND"))
        assertEquals("DONT_KNOW", FslContract.androidToken("DON’T KNOW"))
        assertEquals("YOURE_WELCOME", FslContract.androidToken("YOURE WELCOME"))
    }

    private fun FloatArray.toPoints(count: Int): List<LandmarkPoint> {
        require(size == count * 3)
        return List(count) { index ->
            LandmarkPoint(this[index * 3], this[index * 3 + 1], this[index * 3 + 2])
        }
    }
}
