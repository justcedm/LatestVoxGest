package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkOcclusionDiagnosticsTest {
    @Test
    fun reportsPresenceDropoutsReacquisitionsAndRawGeometry() {
        val diagnostics = LandmarkOcclusionDiagnostics(
            selectedSide = AnatomicalHandSide.RIGHT,
            windowCapacity = 6
        )
        val left = hand(0f)
        val overlappingRight = hand(0.1f)
        val touchingRight = hand(0.2f)

        diagnostics.observeAll(
            listOf(
                frame(pose = true),
                frame(pose = true, left = left),
                frame(pose = false, left = left, right = overlappingRight),
                frame(pose = true, right = overlappingRight),
                frame(pose = true),
                frame(pose = true, left = left, right = touchingRight)
            )
        )

        val report = diagnostics.snapshot()
        assertEquals(6, report.frameCount)
        assertEquals(5, report.posePresentFrames)
        assertEquals(3, report.leftHandPresentFrames)
        assertEquals(3, report.rightHandPresentFrames)
        assertEquals(2, report.bothHandsPresentFrames)
        assertEquals(5f / 6f, report.posePresenceRatio, 0f)
        assertEquals(0.5f, report.leftHandPresenceRatio, 0f)
        assertEquals(0.5f, report.rightHandPresenceRatio, 0f)
        assertEquals(2f / 6f, report.bothHandsPresenceRatio, 0f)
        assertEquals(0.5f, report.selectedSidePresenceRatio, 0f)

        assertEquals(3, report.leftDropoutFrameCount)
        assertEquals(2, report.leftLongestDropoutStreak)
        assertEquals(2, report.leftReacquisitionCount)
        assertEquals(3, report.rightDropoutFrameCount)
        assertEquals(2, report.rightLongestDropoutStreak)
        assertEquals(2, report.rightReacquisitionCount)

        assertEquals(6, report.handBoundingBoxIouObservations.size)
        assertNull(report.handBoundingBoxIouObservations[0])
        assertEquals(1f / 3f, report.handBoundingBoxIouObservations[2]!!, 1.0e-5f)
        assertEquals(0f, report.latestHandBoundingBoxIou!!, 0f)
        assertEquals(0.1f, report.wristDistanceObservations[2]!!, 1.0e-6f)
        assertEquals(0.2f, report.latestWristDistance!!, 1.0e-6f)
    }

    @Test
    fun countsResolvedSlotCollisionsAndOnlyComparableSpatialCategoryFlips() {
        val diagnostics = LandmarkOcclusionDiagnostics(AnatomicalHandSide.LEFT, 4)
        diagnostics.observe(
            frame(
                pose = true,
                observations = listOf(
                    observation(slot = "left", category = "Left", averageX = 0.2f),
                    observation(slot = "left", category = "Right", averageX = 0.8f)
                )
            )
        )
        diagnostics.observe(
            frame(
                pose = true,
                observations = listOf(
                    observation(slot = "right", category = "Right", averageX = 0.2f),
                    observation(slot = "left", category = "Left", averageX = 0.8f)
                )
            )
        )
        diagnostics.observe(
            frame(
                pose = true,
                observations = listOf(observation(slot = "unassigned", category = "unknown", averageX = 0.5f))
            )
        )

        val report = diagnostics.snapshot()
        assertEquals(1, report.resolvedSlotCollisionCount)
        assertEquals(2, report.handednessCategoryFlipCount)
        assertEquals(2, report.handednessComparableCategoryCount)
    }

    @Test
    fun rollingWindowEvictsOldEvidenceAndResetReturnsDeterministicEmptyReport() {
        val diagnostics = LandmarkOcclusionDiagnostics(AnatomicalHandSide.RIGHT, 3)
        diagnostics.observe(frame(pose = true, right = hand(0.4f)))
        diagnostics.observe(frame(pose = true, right = hand(0.4f)))
        diagnostics.observe(frame(pose = true))
        diagnostics.observe(frame(pose = false))

        val rolled = diagnostics.observe(frame(pose = true, right = hand(0.4f)))
        assertEquals(3, rolled.frameCount)
        assertEquals(1, rolled.rightHandPresentFrames)
        assertEquals(2, rolled.rightDropoutFrameCount)
        assertEquals(2, rolled.rightLongestDropoutStreak)
        assertEquals(1, rolled.rightReacquisitionCount)

        diagnostics.reset()
        val empty = diagnostics.snapshot()
        assertEquals(0, empty.frameCount)
        assertEquals(0f, empty.posePresenceRatio, 0f)
        assertEquals(0f, empty.selectedSidePresenceRatio, 0f)
        assertEquals(0, empty.leftDropoutFrameCount)
        assertEquals(0, empty.rightDropoutFrameCount)
        assertTrue(empty.handBoundingBoxIouObservations.isEmpty())
        assertTrue(empty.wristDistanceObservations.isEmpty())
        assertNull(empty.latestHandBoundingBoxIou)
        assertNull(empty.latestWristDistance)
    }

    @Test
    fun observationDoesNotMutateOrRetainSourceLandmarkCollections() {
        val left = hand(0f).toMutableList()
        val right = hand(0.1f).toMutableList()
        val leftBefore = left.toList()
        val rightBefore = right.toList()
        val source = frame(pose = true, left = left, right = right)
        val diagnostics = LandmarkOcclusionDiagnostics(AnatomicalHandSide.RIGHT)

        diagnostics.observe(source)

        assertEquals(leftBefore, left)
        assertEquals(rightBefore, right)
        assertSame(left, source.leftHandLandmarks)
        assertSame(right, source.rightHandLandmarks)
        left.clear()
        right.clear()
        val reportAfterCallerMutation = diagnostics.snapshot()
        assertEquals(1, reportAfterCallerMutation.leftHandPresentFrames)
        assertEquals(1, reportAfterCallerMutation.rightHandPresentFrames)
        assertEquals(1f / 3f, reportAfterCallerMutation.latestHandBoundingBoxIou!!, 1.0e-5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveWindowCapacity() {
        LandmarkOcclusionDiagnostics(AnatomicalHandSide.RIGHT, 0)
    }

    private fun frame(
        pose: Boolean,
        left: List<LandmarkPoint>? = null,
        right: List<LandmarkPoint>? = null,
        observations: List<HandObservation> = emptyList()
    ): LandmarkFrame {
        return LandmarkFrame(
            poseLandmarks = if (pose) List(33) { LandmarkPoint(0f, 0f, 0f) } else null,
            leftHandLandmarks = left,
            rightHandLandmarks = right,
            timestampMs = 0L,
            handObservations = observations
        )
    }

    private fun hand(minX: Float): List<LandmarkPoint> {
        return List(21) { index ->
            LandmarkPoint(
                x = minX + if (index % 2 == 0) 0f else 0.2f,
                y = if ((index / 2) % 2 == 0) 0f else 0.2f,
                z = 0f
            )
        }
    }

    private fun observation(slot: String, category: String, averageX: Float): HandObservation {
        return HandObservation(
            slot = slot,
            mediaPipeHandedness = category,
            averageX = averageX,
            physicalSideEstimate = "test"
        )
    }
}
