package com.voxgest.app.avatar

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class Core3FramePacingSnapshot(
    val refreshRateHz: Float,
    val measuredFrames: Long,
    val renderedFrames: Long,
    val averageFps: Float,
    val frameTimeP50Ms: Float,
    val frameTimeP95Ms: Float,
    val jankyFrames: Long,
    val jankPercent: Float,
    val estimatedDroppedFrames: Long,
    val renderSkippedFrames: Long,
    val animationUpdates: Long,
    val animationUpdateFps: Float,
    val actionSwitchLatencyMs: Float
) {
    fun toLogString(): String = String.format(
        Locale.US,
        "displayHz=%.1f frames=%d rendered=%d avgFps=%.1f p50Ms=%.2f p95Ms=%.2f " +
        "janky=%d jankPct=%.2f dropped=%d renderSkipped=%d animationUpdates=%d " +
            "animationFps=%.1f switchMs=%.2f",
        refreshRateHz,
        measuredFrames,
        renderedFrames,
        averageFps,
        frameTimeP50Ms,
        frameTimeP95Ms,
        jankyFrames,
        jankPercent,
        estimatedDroppedFrames,
        renderSkippedFrames,
        animationUpdates,
        animationUpdateFps,
        actionSwitchLatencyMs
    )
}

/** Fixed-storage Choreographer cadence tracker; [recordFrame] allocates no objects per frame. */
class Core3FramePacingTracker(
    private val sampleCapacity: Int = DEFAULT_SAMPLE_CAPACITY
) {
    private val intervalSamples = LongArray(sampleCapacity)
    private var sampleCount = 0
    private var nextSampleIndex = 0
    private var previousFrameTimeNanos = 0L
    private var totalIntervalNanos = 0L
    private var measuredFrames = 0L
    private var renderedFrames = 0L
    private var jankyFrames = 0L
    private var estimatedDroppedFrames = 0L
    private var renderSkippedFrames = 0L
    private var previousAnimationTimeNanos = 0L
    private var totalAnimationIntervalNanos = 0L
    private var animationUpdates = 0L
    private var actionSwitchLatencyNanos = 0L
    private var refreshRateHz = DEFAULT_REFRESH_RATE_HZ
    private var expectedIntervalNanos = NANOS_PER_SECOND / DEFAULT_REFRESH_RATE_HZ

    init {
        require(sampleCapacity > 0) { "Frame pacing sample capacity must be positive" }
    }

    fun reset(displayRefreshRateHz: Float) {
        refreshRateHz = displayRefreshRateHz.takeIf { it.isFinite() && it >= 30f }
            ?: DEFAULT_REFRESH_RATE_HZ
        expectedIntervalNanos = NANOS_PER_SECOND / refreshRateHz
        sampleCount = 0
        nextSampleIndex = 0
        previousFrameTimeNanos = 0L
        totalIntervalNanos = 0L
        measuredFrames = 0L
        renderedFrames = 0L
        jankyFrames = 0L
        estimatedDroppedFrames = 0L
        renderSkippedFrames = 0L
        beginAction()
    }

    fun pause() {
        previousFrameTimeNanos = 0L
        previousAnimationTimeNanos = 0L
    }

    fun beginAction() {
        previousAnimationTimeNanos = 0L
        totalAnimationIntervalNanos = 0L
        animationUpdates = 0L
        actionSwitchLatencyNanos = 0L
    }

    fun recordAnimationUpdate(frameTimeNanos: Long) {
        if (previousAnimationTimeNanos != 0L) {
            val intervalNanos = frameTimeNanos - previousAnimationTimeNanos
            if (intervalNanos > 0L) totalAnimationIntervalNanos += intervalNanos
        }
        previousAnimationTimeNanos = frameTimeNanos
        animationUpdates += 1L
    }

    fun recordActionSwitchLatency(latencyNanos: Long) {
        actionSwitchLatencyNanos = latencyNanos.coerceAtLeast(0L)
    }

    fun recordFrame(frameTimeNanos: Long, rendered: Boolean) {
        if (previousFrameTimeNanos == 0L) {
            previousFrameTimeNanos = frameTimeNanos
            return
        }
        val intervalNanos = frameTimeNanos - previousFrameTimeNanos
        previousFrameTimeNanos = frameTimeNanos
        if (intervalNanos <= 0L) return

        intervalSamples[nextSampleIndex] = intervalNanos
        nextSampleIndex = (nextSampleIndex + 1) % sampleCapacity
        sampleCount = (sampleCount + 1).coerceAtMost(sampleCapacity)
        totalIntervalNanos += intervalNanos
        measuredFrames += 1
        if (rendered) {
            renderedFrames += 1
        } else {
            renderSkippedFrames += 1
        }
        if (intervalNanos > expectedIntervalNanos * JANK_MULTIPLIER) jankyFrames += 1
        estimatedDroppedFrames += max(
            0,
            (intervalNanos / expectedIntervalNanos).roundToInt() - 1
        )
    }

    fun snapshot(): Core3FramePacingSnapshot {
        val sortedSamples = LongArray(sampleCount)
        repeat(sampleCount) { index -> sortedSamples[index] = intervalSamples[index] }
        sortedSamples.sort()
        val averageFps = if (totalIntervalNanos > 0L) {
            (measuredFrames * NANOS_PER_SECOND / totalIntervalNanos).toFloat()
        } else {
            0f
        }
        val animationUpdateFps = if (totalAnimationIntervalNanos > 0L && animationUpdates > 1L) {
            ((animationUpdates - 1L) * NANOS_PER_SECOND / totalAnimationIntervalNanos).toFloat()
        } else {
            0f
        }
        val jankPercent = if (measuredFrames > 0L) {
            jankyFrames * 100f / measuredFrames
        } else {
            0f
        }
        return Core3FramePacingSnapshot(
            refreshRateHz = refreshRateHz,
            measuredFrames = measuredFrames,
            renderedFrames = renderedFrames,
            averageFps = averageFps,
            frameTimeP50Ms = percentileMillis(sortedSamples, 0.50f),
            frameTimeP95Ms = percentileMillis(sortedSamples, 0.95f),
            jankyFrames = jankyFrames,
            jankPercent = jankPercent,
            estimatedDroppedFrames = estimatedDroppedFrames,
            renderSkippedFrames = renderSkippedFrames,
            animationUpdates = animationUpdates,
            animationUpdateFps = animationUpdateFps,
            actionSwitchLatencyMs = (actionSwitchLatencyNanos / NANOS_PER_MILLISECOND).toFloat()
        )
    }

    private fun percentileMillis(sortedSamples: LongArray, percentile: Float): Float {
        if (sortedSamples.isEmpty()) return 0f
        val index = (ceil(percentile * sortedSamples.size).toInt() - 1)
            .coerceIn(0, sortedSamples.lastIndex)
        return sortedSamples[index] / NANOS_PER_MILLISECOND
    }

    private companion object {
        const val DEFAULT_SAMPLE_CAPACITY = 1_800
        const val DEFAULT_REFRESH_RATE_HZ = 60f
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000f
        const val JANK_MULTIPLIER = 1.5
    }
}
