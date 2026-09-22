package com.voxgest.dryrun

data class StandardFullSign225WindowQuality(
    val frameCount: Int,
    val posePresentFrames: Int,
    val leftHandPresentFrames: Int,
    val rightHandPresentFrames: Int,
    val anyHandPresentFrames: Int,
    val bothHandsPresentFrames: Int
) {
    private fun ratio(count: Int): Float = if (frameCount == 0) 0f else count.toFloat() / frameCount

    val posePresenceRatio: Float get() = ratio(posePresentFrames)
    val leftHandPresenceRatio: Float get() = ratio(leftHandPresentFrames)
    val rightHandPresenceRatio: Float get() = ratio(rightHandPresentFrames)
    val anyHandPresenceRatio: Float get() = ratio(anyHandPresentFrames)
    val bothHandsPresenceRatio: Float get() = ratio(bothHandsPresentFrames)
}

data class StandardFullSign225WindowTiming(
    val available: Boolean,
    val chronological: Boolean,
    val windowDurationMs: Long,
    val oldestFrameAgeMs: Long,
    val medianFrameGapMs: Long,
    val maxFrameGapMs: Long
)

/** Exact 20-frame rolling window owned only by STANDARD_FSL_FULLSIGN225. */
class StandardFullSign225RollingBuffer {
    private val frames = ArrayDeque<StandardFullSign225Frame>(StandardFullSign225Contract.SEQUENCE_LENGTH)

    val size: Int get() = frames.size
    val capacity: Int get() = StandardFullSign225Contract.SEQUENCE_LENGTH
    val isReady: Boolean get() = size == capacity

    fun clear() = frames.clear()

    fun add(frame: StandardFullSign225Frame): Boolean {
        require(frame.vector.size == StandardFullSign225Contract.FEATURE_SIZE) {
            "FullSign225 frame must contain exactly 225 float values"
        }
        require(frame.vector.all { it.isFinite() }) { "FullSign225 frame contains NaN or Inf" }
        if (frames.size == capacity) frames.removeFirst()
        frames.addLast(
            frame.copy(
                vector = frame.vector.copyOf(),
                quality = frame.quality.copy(issues = frame.quality.issues.toList())
            )
        )
        return isReady
    }

    fun snapshot(): Array<FloatArray>? {
        if (!isReady) return null
        return frames.map { it.vector.copyOf() }.toTypedArray()
    }

    fun quality(): StandardFullSign225WindowQuality {
        return StandardFullSign225WindowQuality(
            frameCount = frames.size,
            posePresentFrames = frames.count { it.quality.posePresent },
            leftHandPresentFrames = frames.count { it.quality.leftHandPresent },
            rightHandPresentFrames = frames.count { it.quality.rightHandPresent },
            anyHandPresentFrames = frames.count { it.quality.anyHandPresent },
            bothHandsPresentFrames = frames.count { it.quality.bothHandsPresent }
        )
    }

    fun timing(nowMs: Long): StandardFullSign225WindowTiming {
        val timestamps = frames.map { it.timestampMs }
        if (timestamps.size < 2 || timestamps.any { it <= 0L }) {
            return StandardFullSign225WindowTiming(false, false, 0L, 0L, 0L, 0L)
        }
        val gaps = timestamps.zipWithNext { before, after -> after - before }
        val chronological = gaps.all { it > 0L }
        val sortedGaps = gaps.sorted()
        val middle = sortedGaps.size / 2
        val median = if (sortedGaps.size % 2 == 0) {
            (sortedGaps[middle - 1] + sortedGaps[middle]) / 2L
        } else {
            sortedGaps[middle]
        }
        return StandardFullSign225WindowTiming(
            available = true,
            chronological = chronological,
            windowDurationMs = (timestamps.last() - timestamps.first()).coerceAtLeast(0L),
            oldestFrameAgeMs = (nowMs - timestamps.first()).coerceAtLeast(0L),
            medianFrameGapMs = median.coerceAtLeast(0L),
            maxFrameGapMs = (gaps.maxOrNull() ?: 0L).coerceAtLeast(0L)
        )
    }
}
