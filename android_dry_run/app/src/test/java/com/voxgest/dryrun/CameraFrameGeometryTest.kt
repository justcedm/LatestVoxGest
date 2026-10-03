package com.voxgest.dryrun

import org.junit.Assert.*
import org.junit.Test

class CameraFrameGeometryTest {
    @Test fun allSensorRotations() {
        val expected = mapOf(0 to floatArrayOf(160f, 360f), 90 to floatArrayOf(480f, 360f),
            180 to floatArrayOf(480f, 120f), 270 to floatArrayOf(160f, 120f))
        expected.forEach { (rotation, point) ->
            assertArrayEquals(point, CameraFrameGeometry.uprightToBuffer(.25f, .75f, 640, 480, rotation), .001f)
        }
    }
    @Test fun fitCenterKeepsWholeSigningFramePortraitAndLandscape() {
        listOf(1080f to 1920f, 1920f to 1080f).forEach { (width, height) ->
            val a = PreviewOverlayMapper.centerCropPoint(0f, 0f, 640, 480, width, height, false, false, true)
            val b = PreviewOverlayMapper.centerCropPoint(1f, 1f, 640, 480, width, height, false, false, true)
            assertTrue(a.x >= 0 && a.y >= 0 && b.x <= width && b.y <= height)
            assertEquals(4f / 3f, (b.x - a.x) / (b.y - a.y), .001f)
        }
    }
    @Test fun mirrorOnlyChangesDisplay() {
        val p = PreviewOverlayMapper.centerCropPoint(.2f, .7f, 480, 640, 1080f, 1920f, false, false, true)
        val q = PreviewOverlayMapper.centerCropPoint(.2f, .7f, 480, 640, 1080f, 1920f, false, true, true)
        assertEquals(1080f, p.x + q.x, .001f); assertEquals(p.y, q.y, 0f)
    }
}
