package com.voxgest.dryrun

import android.os.SystemClock
import android.util.Log
import java.util.Locale

/** Bounded, allocation-light runtime telemetry for Samsung recognition hardening. */
class StandardFslPerformanceTracker {
    private val cameraCapture = EventWindow()
    private val analyzer = EventWindow()
    private val mediaPipe = EventWindow()
    private val temporal = EventWindow()
    private val tflite = EventWindow()
    private val overlay = EventWindow()
    private val uiState = EventWindow()
    private val displayVsync = EventWindow()

    private val acquisitionAge = SampleWindow()
    private val conversion = SampleWindow()
    private val hand = SampleWindow()
    private val pose = SampleWindow()
    private val mediaPipeTotal = SampleWindow()
    private val temporalStage = SampleWindow()
    private val tfliteStage = SampleWindow()
    private val gateStage = SampleWindow()
    private val overlayStage = SampleWindow()
    private var lastLogAtNanos = 0L

    @Synchronized
    fun recordCameraCapture(nowNanos: Long = SystemClock.elapsedRealtimeNanos()) = cameraCapture.add(nowNanos)

    @Synchronized
    fun recordAnalyzer(nowNanos: Long = SystemClock.elapsedRealtimeNanos()) = analyzer.add(nowNanos)

    @Synchronized
    fun recordLandmarks(metrics: LandmarkExtractionMetrics) {
        mediaPipe.add(SystemClock.elapsedRealtimeNanos())
        metrics.acquisitionAgeMs?.let(acquisitionAge::add)
        conversion.add(metrics.conversionMs)
        hand.add(metrics.handLandmarkerMs)
        pose.add(metrics.poseLandmarkerMs)
        mediaPipeTotal.add(metrics.totalMs)
    }

    @Synchronized
    fun recordTemporal(durationMs: Double) {
        temporal.add(SystemClock.elapsedRealtimeNanos())
        temporalStage.add(durationMs)
    }

    @Synchronized
    fun recordTflite(durationMs: Double) {
        tflite.add(SystemClock.elapsedRealtimeNanos())
        tfliteStage.add(durationMs)
    }

    @Synchronized
    fun recordGate(durationMs: Double) = gateStage.add(durationMs)

    @Synchronized
    fun recordOverlay(durationMs: Double, published: Boolean) {
        overlayStage.add(durationMs)
        if (published) overlay.add(SystemClock.elapsedRealtimeNanos())
    }

    @Synchronized
    fun recordUiState(nowNanos: Long = SystemClock.elapsedRealtimeNanos()) = uiState.add(nowNanos)

    @Synchronized
    fun recordDisplayVsync(frameTimeNanos: Long) {
        // frameTimeNanos uses the Choreographer timebase; rates only need callback arrival cadence.
        if (frameTimeNanos > 0L) displayVsync.add(SystemClock.elapsedRealtimeNanos())
    }

    @Synchronized
    fun logIfDue(force: Boolean = false) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (!force && now - lastLogAtNanos < LOG_INTERVAL_NANOS) return
        lastLogAtNanos = now
        Log.i(
            TAG,
            "STANDARD_FSL_PERF " +
                "camera_capture_fps=${fmt(cameraCapture.fps(now))} " +
                "analyzer_fps=${fmt(analyzer.fps(now))} " +
                "mediapipe_fps=${fmt(mediaPipe.fps(now))} " +
                "temporal_fps=${fmt(temporal.fps(now))} " +
                "tflite_fps=${fmt(tflite.fps(now))} " +
                "overlay_fps=${fmt(overlay.fps(now))} " +
                "ui_state_fps=${fmt(uiState.fps(now))} " +
                "display_vsync_fps=${fmt(displayVsync.fps(now))} " +
                stats("camera_to_landmark_age_ms", acquisitionAge) + " " +
                stats("conversion_ms", conversion) + " " +
                stats("hand_task_ms", hand) + " " +
                stats("pose_task_ms", pose) + " " +
                stats("mediapipe_total_ms", mediaPipeTotal) + " " +
                stats("temporal_ms", temporalStage) + " " +
                stats("tflite_wall_ms", tfliteStage) + " " +
                stats("gate_ms", gateStage) + " " +
                stats("overlay_callback_ms", overlayStage)
        )
    }

    @Synchronized
    fun reset() {
        listOf(cameraCapture, analyzer, mediaPipe, temporal, tflite, overlay, uiState, displayVsync)
            .forEach(EventWindow::clear)
        listOf(acquisitionAge, conversion, hand, pose, mediaPipeTotal, temporalStage, tfliteStage, gateStage, overlayStage)
            .forEach(SampleWindow::clear)
        lastLogAtNanos = 0L
    }

    private fun stats(name: String, samples: SampleWindow): String =
        "${name}_median=${fmt(samples.percentile(0.50))} ${name}_p95=${fmt(samples.percentile(0.95))}"

    private fun fmt(value: Double): String =
        if (value.isFinite()) String.format(Locale.US, "%.3f", value) else "NA"

    private class EventWindow {
        private val values = ArrayDeque<Long>(MAX_SAMPLES)

        fun add(value: Long) {
            if (values.size == MAX_SAMPLES) values.removeFirst()
            values.addLast(value)
        }

        fun fps(nowNanos: Long): Double {
            while (values.isNotEmpty() && nowNanos - values.first() > RATE_WINDOW_NANOS) values.removeFirst()
            if (values.size < 2) return Double.NaN
            val elapsedSeconds = (values.last() - values.first()) / 1_000_000_000.0
            return if (elapsedSeconds > 0.0) (values.size - 1) / elapsedSeconds else Double.NaN
        }

        fun clear() = values.clear()
    }

    private class SampleWindow {
        private val values = ArrayDeque<Double>(MAX_SAMPLES)

        fun add(value: Double) {
            if (!value.isFinite() || value < 0.0) return
            if (values.size == MAX_SAMPLES) values.removeFirst()
            values.addLast(value)
        }

        fun percentile(fraction: Double): Double {
            if (values.isEmpty()) return Double.NaN
            val sorted = values.sorted()
            val index = ((sorted.size - 1) * fraction).toInt().coerceIn(sorted.indices)
            return sorted[index]
        }

        fun clear() = values.clear()
    }

    companion object {
        private const val TAG = "VoxGestFullSign225"
        private const val MAX_SAMPLES = 240
        private const val RATE_WINDOW_NANOS = 5_000_000_000L
        private const val LOG_INTERVAL_NANOS = 5_000_000_000L
    }
}
