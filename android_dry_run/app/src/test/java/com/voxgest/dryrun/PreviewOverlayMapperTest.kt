package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewOverlayMapperTest {
    @Test
    fun `center crop mapping follows fill center geometry`() {
        val center = PreviewOverlayMapper.centerCropPoint(
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            displayWidth = 1080f,
            displayHeight = 1080f,
            analysisMirrored = false,
            previewMirrored = false
        )

        assertEquals(540f, center.x, 0.001f)
        assertEquals(540f, center.y, 0.001f)
    }

    @Test
    fun `preview mirror changes display x without changing analysis coordinates`() {
        val direct = PreviewOverlayMapper.centerCropPoint(
            normalizedX = 0.25f,
            normalizedY = 0.5f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            displayWidth = 1080f,
            displayHeight = 1080f,
            analysisMirrored = false,
            previewMirrored = false
        )
        val displayMirrored = PreviewOverlayMapper.centerCropPoint(
            normalizedX = 0.25f,
            normalizedY = 0.5f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            displayWidth = 1080f,
            displayHeight = 1080f,
            analysisMirrored = false,
            previewMirrored = true
        )

        assertEquals(60f, direct.x, 0.001f)
        assertEquals(1020f, displayMirrored.x, 0.001f)
        assertEquals(0.25f, 0.25f, 0f) // The input/inference coordinate is not mutated.
    }

    @Test
    fun `matching analysis and preview mirroring does not double flip`() {
        assertEquals(
            0.25f,
            PreviewOverlayMapper.xForPreview(
                analysisX = 0.25f,
                analysisMirrored = true,
                previewMirrored = true
            ),
            0f
        )
    }
}
