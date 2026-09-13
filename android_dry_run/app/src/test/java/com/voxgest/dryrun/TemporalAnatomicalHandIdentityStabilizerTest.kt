package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalAnatomicalHandIdentityStabilizerTest {
    @Test
    fun continuityAndPoseAnchorsOverrideTransientWrongHandednessWithoutUsingImageX() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        )
        val initialLeft = detection(0.25f, "left")
        val initialRight = detection(0.75f, "right")
        val initial = stabilizer.stabilize(
            listOf(initialLeft, initialRight),
            pose(0.25f, 0.75f),
            100L
        )
        assertSame(initialLeft, initial.leftDetection)
        assertSame(initialRight, initial.rightDetection)

        // Detection order and image-X are both non-authoritative. The categories
        // flip here, while wrist continuity and pose anchors remain anatomical.
        val nextRight = detection(0.74f, "left")
        val nextLeft = detection(0.26f, "right")
        val stabilized = stabilizer.stabilize(
            listOf(nextRight, nextLeft),
            pose(0.26f, 0.74f),
            200L
        )

        assertSame(nextLeft, stabilized.leftDetection)
        assertSame(nextRight, stabilized.rightDetection)
        assertEquals(TemporalHandAssignmentStatus.RELIABLE, stabilized.diagnostics.status)
        assertEquals(2, stabilized.diagnostics.slotChanges.size)
        assertTrue(
            stabilized.diagnostics.slotChanges.all {
                it.reason.contains("PRIOR_WRIST_CONTINUITY") ||
                    it.reason.contains("POSE_WRIST_ANCHOR")
            }
        )
    }

    @Test
    fun unknownSingleHandWithoutTemporalOrPoseEvidenceFailsClosed() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
        )

        val result = stabilizer.stabilize(
            listOf(detection(0.1f, null, Float.NaN)),
            poseLandmarks = null,
            timestampMs = 100L
        )

        assertNull(result.leftDetection)
        assertNull(result.rightDetection)
        assertEquals(TemporalHandAssignmentStatus.FAILED_CLOSED, result.diagnostics.status)
        assertTrue(
            result.diagnostics.failClosedReasons.contains(
                "AMBIGUOUS_SINGLE_HAND_ASSIGNMENT"
            )
        )
    }

    @Test
    fun shortDropoutReacquiresPriorAnatomicalTrackWithoutFabricatingMissingFrame() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        )
        val first = detection(0.25f, "left")
        stabilizer.stabilize(listOf(first), pose(0.25f, 0.75f), 100L)

        val missing = stabilizer.stabilize(emptyList(), pose(0.25f, 0.75f), 200L)
        assertNull(missing.leftDetection)
        assertEquals(1, missing.diagnostics.leftDropout.currentFrames)

        val reacquired = detection(0.27f, null, Float.NaN)
        val result = stabilizer.stabilize(
            listOf(reacquired),
            poseLandmarks = null,
            timestampMs = 300L
        )

        assertSame(reacquired, result.leftDetection)
        assertNull(result.rightDetection)
        assertEquals(1, result.diagnostics.leftDropout.totalReacquisitions)
        assertEquals(1, result.diagnostics.leftDropout.lastReacquiredAfterFrames)
        assertTrue(
            result.diagnostics.handedness.single().assignmentEvidence
                .contains("SHORT_REACQUISITION")
        )
    }

    @Test
    fun gradualImageXCrossingPreservesAnatomicalSlotsFromTrajectoryAndPose() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        )
        stabilizer.stabilize(
            listOf(detection(0.25f, "left"), detection(0.75f, "right")),
            pose(0.25f, 0.75f),
            100L
        )
        stabilizer.stabilize(
            listOf(detection(0.40f, "left"), detection(0.60f, "right")),
            pose(0.40f, 0.60f),
            200L
        )

        val anatomicalRightNowOnImageLeft = detection(0.42f, "left")
        val anatomicalLeftNowOnImageRight = detection(0.58f, "right")
        val crossed = stabilizer.stabilize(
            listOf(anatomicalRightNowOnImageLeft, anatomicalLeftNowOnImageRight),
            pose(0.58f, 0.42f),
            300L
        )

        assertSame(anatomicalLeftNowOnImageRight, crossed.leftDetection)
        assertSame(anatomicalRightNowOnImageLeft, crossed.rightDetection)
        assertEquals(TemporalHandAssignmentStatus.RELIABLE, crossed.diagnostics.status)
        assertTrue(crossed.diagnostics.slotChanges.size == 2)
    }

    @Test
    fun exactUnresolvableCollisionFailsClosedInsteadOfOrderingByImageX() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        )

        val collision = stabilizer.stabilize(
            listOf(
                detection(0.50f, null, Float.NaN),
                detection(0.50f, null, Float.NaN)
            ),
            pose(0.50f, 0.50f),
            100L
        )

        assertNull(collision.leftDetection)
        assertNull(collision.rightDetection)
        assertEquals(
            TemporalHandAssignmentStatus.FAILED_CLOSED,
            collision.diagnostics.status
        )
        assertTrue(
            collision.diagnostics.failClosedReasons.contains(
                "AMBIGUOUS_TWO_HAND_ASSIGNMENT"
            )
        )
    }

    @Test
    fun nonMonotonicTimestampFailsClosedWithoutAdvancingTrack() {
        val stabilizer = TemporalAnatomicalHandIdentityStabilizer(
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES
        )
        stabilizer.stabilize(
            listOf(detection(0.25f, "left")),
            pose(0.25f, 0.75f),
            200L
        )

        val rejected = stabilizer.stabilize(
            listOf(detection(0.26f, "left")),
            pose(0.26f, 0.75f),
            100L
        )

        assertNull(rejected.leftDetection)
        assertTrue(
            rejected.diagnostics.failClosedReasons.contains("NON_MONOTONIC_TIMESTAMP")
        )
    }

    private fun detection(
        wristX: Float,
        label: String?,
        confidence: Float = 0.95f
    ): TemporalHandDetection {
        return TemporalHandDetection(
            landmarks = List(21) { index ->
                LandmarkPoint(
                    wristX + index * 0.0005f,
                    0.35f + index * 0.001f,
                    index * 0.0001f
                )
            },
            mediaPipeHandedness = label,
            handednessConfidence = confidence
        )
    }

    private fun pose(leftWristX: Float, rightWristX: Float): List<LandmarkPoint> {
        return List(33) { index ->
            when (index) {
                15 -> LandmarkPoint(leftWristX, 0.35f, 0f)
                16 -> LandmarkPoint(rightWristX, 0.35f, 0f)
                else -> LandmarkPoint(0.5f, 0.5f, 0f)
            }
        }
    }
}
