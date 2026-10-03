package com.voxgest.dryrun

import org.junit.Assert.*
import org.junit.Test

class ExactTimestampPairerTest {
    @Test fun exactPair() {
        val p = ExactTimestampPairer<String, String>()
        p.submit(1, 0); p.hand(1, "h"); p.pose(1, "p")
        assertEquals(listOf(ExactTimestampPairer.PairResult(1L, "h", "p")), p.drain())
    }
    @Test fun delayedDetectorDoesNotMix() {
        val p = ExactTimestampPairer<String, String>()
        p.submit(1, 0); p.submit(2, 0); p.hand(1, "h1"); p.pose(2, "p2")
        assertTrue(p.drain().isEmpty()); p.pose(1, "p1")
        assertEquals(1L, p.drain().single().timestamp)
    }
    @Test fun missingExpires() {
        val p = ExactTimestampPairer<String, String>(timeoutMs = 10)
        p.submit(1, 0); p.hand(1, "h")
        assertTrue(p.expire(9).isEmpty()); assertEquals(listOf(1L), p.expire(10))
        assertEquals(0, p.size)
    }
    @Test fun staleCallbackRejected() {
        val p = ExactTimestampPairer<String, String>(timeoutMs = 10)
        p.submit(1, 0); p.expire(10); p.pose(1, "p")
        assertEquals(1, p.stale); assertTrue(p.drain().isEmpty())
    }
    @Test fun outOfOrderCompletionDrainsChronologically() {
        val p = ExactTimestampPairer<String, String>()
        p.submit(1, 0); p.submit(2, 1); p.hand(2, "h2"); p.pose(2, "p2")
        assertTrue(p.drain().isEmpty()); p.pose(1, "p1"); p.hand(1, "h1")
        assertEquals(listOf(1L, 2L), p.drain().map { it.timestamp })
    }
    @Test fun monotonicTimestamps() {
        val p = ExactTimestampPairer<String, String>()
        assertTrue(p.submit(20, 0)); assertFalse(p.submit(20, 1)); assertFalse(p.submit(19, 2))
    }
    @Test fun boundedBackpressure() {
        val p = ExactTimestampPairer<String, String>(capacity = 1)
        assertTrue(p.submit(1, 0)); assertFalse(p.submit(2, 1)); assertEquals(1, p.size)
        p.hand(1, "h"); p.pose(1, "p"); p.drain(); assertTrue(p.submit(3, 2))
        assertEquals(1, p.dropped)
    }
    @Test fun lifecycleRejectsPreviousGeneration() {
        val p = ExactTimestampPairer<String, String>()
        val old = p.generation; p.submit(1, 0); p.reset(); p.submit(1, 1)
        p.hand(1, "old", old); p.pose(1, "p"); assertTrue(p.drain().isEmpty())
        p.hand(1, "new"); assertEquals("new", p.drain().single().hand)
    }
    @Test fun expiredHeadAllowsNextCompletePair() {
        val p = ExactTimestampPairer<String, String>(timeoutMs = 10)
        p.submit(1, 0); p.submit(2, 5); p.hand(2, "h"); p.pose(2, "p")
        p.expire(10); assertEquals(2L, p.drain().single().timestamp)
    }
}
