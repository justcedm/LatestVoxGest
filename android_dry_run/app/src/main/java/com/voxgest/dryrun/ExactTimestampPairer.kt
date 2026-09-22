package com.voxgest.dryrun

/** Bounded exact joins. Timeouts use arrival clock, never camera clock. No stale cross-pairing. */
class ExactTimestampPairer<H : Any, P : Any>(
    private val capacity: Int = 4,
    private val timeoutMs: Long = 1000
) {
    data class PairResult<H, P>(val timestamp: Long, val hand: H, val pose: P)
    private data class Pending<H, P>(val arrival: Long, var hand: H? = null, var pose: P? = null)
    private val pending = java.util.TreeMap<Long, Pending<H, P>>()
    private var lastSubmitted = Long.MIN_VALUE
    var generation = 0L; private set
    var dropped = 0; private set
    var stale = 0; private set
    val size get() = pending.size

    init { require(capacity > 0 && timeoutMs > 0) }

    fun submit(timestamp: Long, now: Long): Boolean {
        if (timestamp <= lastSubmitted || pending.size >= capacity) { dropped++; return false }
        lastSubmitted = timestamp
        pending[timestamp] = Pending(now)
        return true
    }
    fun hand(timestamp: Long, value: H, epoch: Long = generation) {
        val entry = pending[timestamp]
        if (epoch != generation || entry == null || entry.hand != null) { stale++; return }
        entry.hand = value
    }
    fun pose(timestamp: Long, value: P, epoch: Long = generation) {
        val entry = pending[timestamp]
        if (epoch != generation || entry == null || entry.pose != null) { stale++; return }
        entry.pose = value
    }
    fun expire(now: Long): List<Long> {
        val expired = pending.filter { now - it.value.arrival >= timeoutMs }.keys.toList()
        expired.forEach { pending.remove(it); dropped++ }
        return expired
    }
    fun drain(): List<PairResult<H, P>> {
        val ready = mutableListOf<PairResult<H, P>>()
        while (pending.isNotEmpty()) {
            val entry = pending.firstEntry() ?: break
            val hand = entry.value.hand ?: break
            val pose = entry.value.pose ?: break
            pending.remove(entry.key)
            ready += PairResult(entry.key, hand, pose)
        }
        return ready
    }
    fun reset() { pending.clear(); lastSubmitted = Long.MIN_VALUE; generation++ }
}
