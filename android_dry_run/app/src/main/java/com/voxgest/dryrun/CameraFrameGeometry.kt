package com.voxgest.dryrun

/** Pure geometry: upright, unmirrored normalized coordinates -> original buffer pixels. */
object CameraFrameGeometry {
    fun uprightToBuffer(x: Float, y: Float, width: Int, height: Int, rotation: Int): FloatArray {
        require(width > 0 && height > 0)
        return when (rotation) {
            0 -> floatArrayOf(x * width, y * height)
            90 -> floatArrayOf(y * width, (1f - x) * height)
            180 -> floatArrayOf((1f - x) * width, (1f - y) * height)
            270 -> floatArrayOf((1f - y) * width, x * height)
            else -> error("Unsupported camera rotation: $rotation")
        }
    }
}

/** Display/capture metadata only; never consumed by a feature builder. */
data class CameraFrameMetadata(
    val sourceTimestampNanos: Long,
    val rotationDegrees: Int,
    val bufferWidth: Int,
    val bufferHeight: Int,
    val bufferToSensor: FloatArray?,
    val lens: String,
    val previewMirrored: Boolean,
    val analysisMirrored: Boolean = false
)
