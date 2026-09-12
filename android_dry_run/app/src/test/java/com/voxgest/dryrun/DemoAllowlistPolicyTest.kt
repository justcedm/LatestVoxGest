package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAllowlistPolicyTest {
    @Test
    fun defaultPolicyFailsClosed() {
        val decision = DemoAllowlistPolicy().evaluate(accepted("YES"))

        assertFalse(decision.userFacing)
        assertEquals(DemoAllowlistReason.NOT_DEMO_QUALIFIED, decision.reason)
        assertTrue(DemoAllowlistPolicy.DEMO_ALLOWLIST.isEmpty())
    }

    @Test
    fun upstreamRejectedResultCannotPassEvenWhenLabelIsQualified() {
        val raw = RecognitionResult("YES", 0.99f, 0.90f, false, "raw_tflite")
        val decision = DemoAllowlistPolicy(setOf("YES")).evaluate(raw)

        assertFalse(decision.userFacing)
        assertEquals(DemoAllowlistReason.UPSTREAM_REJECTED, decision.reason)
    }

    @Test
    fun exactQualifiedCanonicalTokenPassesWithoutNormalization() {
        val policy = DemoAllowlistPolicy(setOf("GOOD_MORNING"))

        assertTrue(policy.evaluate(accepted("GOOD_MORNING")).userFacing)
        assertEquals(
            DemoAllowlistReason.NON_CANONICAL_LABEL,
            policy.evaluate(accepted("GOOD MORNING")).reason
        )
        assertEquals(
            DemoAllowlistReason.NON_CANONICAL_LABEL,
            policy.evaluate(accepted("good_morning")).reason
        )
    }

    @Test
    fun noOutputAndUiControlTokensCannotBeConfigured() {
        assertThrows(IllegalArgumentException::class.java) { DemoAllowlistPolicy(setOf("NSAC")) }
        assertThrows(IllegalArgumentException::class.java) { DemoAllowlistPolicy(setOf("SPEAK")) }
    }

    @Test
    fun nonFiniteScoresFailClosedEvenIfUpstreamMarksThemAccepted() {
        val decision = DemoAllowlistPolicy(setOf("YES")).evaluate(
            RecognitionResult("YES", Float.NaN, 0.5f, true, "bad_upstream_result")
        )

        assertFalse(decision.userFacing)
        assertEquals(DemoAllowlistReason.NON_FINITE_SCORE, decision.reason)
    }

    private fun accepted(label: String): RecognitionResult {
        return RecognitionResult(label, 0.9f, 0.5f, true, "accepted")
    }
}
