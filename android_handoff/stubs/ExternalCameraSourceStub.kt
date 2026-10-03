package com.voxgest.handoff.pending

import java.nio.ByteBuffer

/**
 * Minimal frame metadata shared by a future UVC or RTSP adapter.
 * The adapter owns buffer lifetime and must convert this unmirrored frame to
 * the same MediaPipe input representation used by the built-in camera path.
 */
data class ExternalCameraFrame(
    val width: Int,
    val height: Int,
    val timestampNanos: Long,
    val rotationDegrees: Int,
    val rgba8888: ByteBuffer,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be 0, 90, 180, or 270"
        }
    }
}

interface ExternalCameraSource {
    fun start(
        sourceId: String,
        onFrame: (ExternalCameraFrame) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun stop()
}

/**
 * Fail-explicitly placeholder. It prevents a missing UVC integration from
 * being mistaken for a working camera source.
 */
class ExternalCameraSourceStub : ExternalCameraSource {
    override fun start(
        sourceId: String,
        onFrame: (ExternalCameraFrame) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        onError(UnsupportedOperationException("External UVC/RTSP camera support is not wired"))
    }

    override fun stop() = Unit
}
