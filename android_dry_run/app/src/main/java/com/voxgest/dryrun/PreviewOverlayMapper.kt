package com.voxgest.dryrun

/** Keeps display-only selfie mirroring out of the model feature contract. */
object PreviewOverlayMapper {
    fun xForPreview(
        analysisX: Float,
        analysisMirrored: Boolean,
        previewMirrored: Boolean
    ): Float {
        return if (analysisMirrored == previewMirrored) analysisX else 1f - analysisX
    }

    data class DisplayPoint(val x: Float, val y: Float)

    /** Maps upright normalized analysis coordinates into PreviewView.ScaleType.FILL_CENTER pixels. */
    fun centerCropPoint(
        normalizedX: Float,
        normalizedY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        displayWidth: Float,
        displayHeight: Float,
        analysisMirrored: Boolean,
        previewMirrored: Boolean
    ): DisplayPoint {
        if (sourceWidth <= 0 || sourceHeight <= 0 || displayWidth <= 0f || displayHeight <= 0f) {
            return DisplayPoint(0f, 0f)
        }
        val x = xForPreview(normalizedX, analysisMirrored, previewMirrored).coerceIn(0f, 1f)
        val y = normalizedY.coerceIn(0f, 1f)
        val scale = maxOf(displayWidth / sourceWidth.toFloat(), displayHeight / sourceHeight.toFloat())
        val renderedWidth = sourceWidth * scale
        val renderedHeight = sourceHeight * scale
        val offsetX = (displayWidth - renderedWidth) / 2f
        val offsetY = (displayHeight - renderedHeight) / 2f
        return DisplayPoint(
            x = offsetX + x * renderedWidth,
            y = offsetY + y * renderedHeight
        )
    }
}
