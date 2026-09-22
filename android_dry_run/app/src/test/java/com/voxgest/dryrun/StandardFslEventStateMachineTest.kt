package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardFslEventStateMachineTest {
    @Test
    fun `visible held hand and startup jitter cannot arm until neutral release`() {
        val machine = StandardFslEventStateMachine()

        val idle = machine.onFrame(frame(100L, handOffset = 0f))
        assertEquals(StandardFslEventState.IDLE, idle.state)
        assertFalse(idle.collectFrame)
        assertEquals("WAITING_FOR_NEUTRAL_RELEASE", idle.reason)

        val startupJitter = machine.onFrame(frame(200L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.IDLE, startupJitter.state)
        assertFalse(startupJitter.collectFrame)
        assertEquals("WAITING_FOR_NEUTRAL_RELEASE", startupJitter.reason)

        machine.onFrame(missingFrame(300L))
        machine.onFrame(missingFrame(400L))
        val ready = machine.onFrame(missingFrame(500L))
        assertEquals("LANDMARK_RELEASE", ready.reason)
        assertTrue(ready.flushWindow)

        val entry = machine.onFrame(frame(600L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.PRIMING, entry.state)
        assertTrue(entry.collectFrame)
        assertTrue(entry.flushWindow)

        repeat(4) { index -> machine.onFrame(frame(700L + index * 100L, handOffset = 0.08f)) }
        val activeHold = machine.onFrame(frame(1_100L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.SIGN_ACTIVE, activeHold.state)
        assertTrue(activeHold.collectFrame)
        assertTrue(activeHold.allowInference)
        assertEquals("STABLE_SIGN_HOLD", activeHold.reason)
    }

    @Test
    fun `acceptance emits one event and held sign cannot rearm on timer alone`() {
        val machine = primedMachine()
        machine.markCandidate()
        assertEquals(StandardFslEventState.CANDIDATE, machine.state)
        machine.markAccepted()
        assertEquals(StandardFslEventState.ACCEPTED, machine.state)

        repeat(8) { index ->
            val held = machine.onFrame(frame(800L + index * 100L, handOffset = 0.08f))
            assertEquals(StandardFslEventState.WAIT_FOR_RELEASE, held.state)
            assertFalse(held.collectFrame)
            assertFalse(held.allowInference)
            assertFalse(held.flushWindow)
        }
    }

    @Test
    fun `three missing frames release and next hand entry begins a fresh event`() {
        val machine = primedMachine()
        machine.markAccepted()

        machine.onFrame(missingFrame(800L))
        machine.onFrame(missingFrame(900L))
        val release = machine.onFrame(missingFrame(1_000L))
        assertEquals(StandardFslEventState.IDLE, release.state)
        assertEquals("LANDMARK_RELEASE", release.reason)
        assertTrue(release.flushWindow)

        val settledIdle = machine.onFrame(missingFrame(1_050L))
        assertEquals("IDLE_NO_LANDMARKS", settledIdle.reason)
        assertFalse(settledIdle.flushWindow)

        val newEntry = machine.onFrame(frame(1_100L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.PRIMING, newEntry.state)
        assertEquals("HAND_ENTRY_AFTER_RELEASE", newEntry.reason)
        assertTrue(newEntry.collectFrame)
        assertTrue(newEntry.flushWindow)
    }

    @Test
    fun `sustained motion cannot rearm without landmark release`() {
        val machine = primedMachine()
        machine.markAccepted()
        machine.onFrame(frame(800L, handOffset = 0.08f))

        val one = machine.onFrame(frame(1_000L, handOffset = 0.12f))
        assertEquals(StandardFslEventState.WAIT_FOR_RELEASE, one.state)
        assertFalse(one.collectFrame)
        val two = machine.onFrame(frame(1_100L, handOffset = 0.16f))
        assertEquals(StandardFslEventState.WAIT_FOR_RELEASE, two.state)
        val three = machine.onFrame(frame(1_200L, handOffset = 0.20f))
        assertEquals(StandardFslEventState.WAIT_FOR_RELEASE, three.state)
        assertEquals("WAITING_FOR_LANDMARK_RELEASE", three.reason)
        assertFalse(three.flushWindow)
        assertFalse(three.collectFrame)
    }

    @Test
    fun `startup no-landmark warmup does not arm a held palm entry`() {
        val machine = StandardFslEventStateMachine()
        machine.onFrame(missingFrame(100L))
        machine.onFrame(missingFrame(200L))
        val warmup = machine.onFrame(missingFrame(300L))
        assertEquals("STARTUP_NEUTRAL_ARMING", warmup.reason)
        assertFalse(warmup.flushWindow)

        val heldPalm = machine.onFrame(frame(400L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.IDLE, heldPalm.state)
        assertFalse(heldPalm.collectFrame)
        assertEquals("WAITING_FOR_NEUTRAL_RELEASE", heldPalm.reason)
    }

    @Test
    fun `sustained pose-present neutral startup arms the next hand entry`() {
        val machine = StandardFslEventStateMachine()

        repeat(11) { index ->
            val arming = machine.onFrame(missingFrame(100L + index * 100L))
            assertFalse(arming.flushWindow)
        }
        val ready = machine.onFrame(missingFrame(1_200L))
        assertEquals("STARTUP_NEUTRAL_READY", ready.reason)
        assertTrue(ready.flushWindow)

        val entry = machine.onFrame(frame(1_300L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.PRIMING, entry.state)
        assertEquals("HAND_ENTRY_AFTER_RELEASE", entry.reason)
        assertTrue(entry.collectFrame)
    }

    @Test
    fun `frames without pose cannot arm startup`() {
        val machine = StandardFslEventStateMachine()

        repeat(20) { index -> machine.onFrame(noPersonFrame(100L + index * 100L)) }
        val heldPalm = machine.onFrame(frame(2_200L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.IDLE, heldPalm.state)
        assertFalse(heldPalm.collectFrame)
        assertEquals("WAITING_FOR_NEUTRAL_RELEASE", heldPalm.reason)
    }

    @Test
    fun `pose-only frames with a raised wrist cannot arm startup`() {
        val machine = StandardFslEventStateMachine()

        repeat(20) { index -> machine.onFrame(raisedPoseNoHandsFrame(100L + index * 100L)) }
        val heldPalm = machine.onFrame(frame(2_200L, handOffset = 0.08f))
        assertEquals(StandardFslEventState.IDLE, heldPalm.state)
        assertFalse(heldPalm.collectFrame)
        assertEquals("WAITING_FOR_NEUTRAL_RELEASE", heldPalm.reason)
    }

    @Test
    fun `temporary hand dropout with a raised wrist does not release accepted event`() {
        val machine = primedMachine()
        machine.markAccepted()

        repeat(6) { index ->
            val dropout = machine.onFrame(raisedPoseNoHandsFrame(1_600L + index * 100L))
            assertEquals(StandardFslEventState.WAIT_FOR_RELEASE, dropout.state)
            assertFalse(dropout.flushWindow)
        }

        machine.onFrame(missingFrame(2_200L))
        machine.onFrame(missingFrame(2_300L))
        val release = machine.onFrame(missingFrame(2_400L))
        assertEquals(StandardFslEventState.IDLE, release.state)
        assertEquals("LANDMARK_RELEASE", release.reason)
        assertTrue(release.flushWindow)
    }

    private fun primedMachine(): StandardFslEventStateMachine {
        return StandardFslEventStateMachine().also { machine ->
            repeat(12) { index -> machine.onFrame(missingFrame(100L + index * 100L)) }
            machine.onFrame(frame(1_300L, handOffset = 0.08f))
            machine.onFrame(frame(1_400L, handOffset = 0.08f))
            machine.onFrame(frame(1_500L, handOffset = 0.08f))
            assertEquals(StandardFslEventState.SIGN_ACTIVE, machine.state)
        }
    }

    private fun frame(timestampMs: Long, handOffset: Float): LandmarkFrame {
        return LandmarkFrame(
            poseLandmarks = List(33) { index -> LandmarkPoint(index / 100f, index / 120f, 0f) },
            leftHandLandmarks = List(21) { index ->
                LandmarkPoint(0.2f + index / 100f + handOffset, 0.3f + index / 120f, 0f)
            },
            rightHandLandmarks = null,
            timestampMs = timestampMs
        )
    }

    private fun missingFrame(timestampMs: Long): LandmarkFrame = LandmarkFrame(
        poseLandmarks = List(33) { LandmarkPoint(0f, 0f, 0f) },
        leftHandLandmarks = null,
        rightHandLandmarks = null,
        timestampMs = timestampMs
    )

    private fun noPersonFrame(timestampMs: Long): LandmarkFrame = LandmarkFrame(
        poseLandmarks = null,
        leftHandLandmarks = null,
        rightHandLandmarks = null,
        timestampMs = timestampMs
    )

    private fun raisedPoseNoHandsFrame(timestampMs: Long): LandmarkFrame = LandmarkFrame(
        poseLandmarks = List(33) { index ->
            when (index) {
                15 -> LandmarkPoint(0.3f, 0.35f, 0f)
                16 -> LandmarkPoint(0.7f, 0.75f, 0f)
                23 -> LandmarkPoint(0.4f, 0.75f, 0f)
                24 -> LandmarkPoint(0.6f, 0.75f, 0f)
                else -> LandmarkPoint(0.5f, 0.5f, 0f)
            }
        },
        leftHandLandmarks = null,
        rightHandLandmarks = null,
        timestampMs = timestampMs
    )
}
