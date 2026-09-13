package com.voxgest.dryrun

import androidx.camera.core.MirrorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSourceSelectionPolicyTest {
    private val front = CameraSourceDescriptor("1", CameraSource.FRONT)
    private val back = CameraSourceDescriptor("0", CameraSource.BACK)
    private val external = CameraSourceDescriptor("usb-0", CameraSource.EXTERNAL)

    @Test
    fun autoPrefersFrontThenBackThenExternal() {
        assertEquals(front, CameraSourceSelectionPolicy.choose(CameraSource.AUTO, listOf(external, back, front)))
        assertEquals(back, CameraSourceSelectionPolicy.choose(CameraSource.AUTO, listOf(external, back)))
        assertEquals(external, CameraSourceSelectionPolicy.choose(CameraSource.AUTO, listOf(external)))
    }

    @Test
    fun explicitExternalNeverFallsBack() {
        assertNull(CameraSourceSelectionPolicy.choose(CameraSource.EXTERNAL, listOf(front, back)))
        assertEquals(external, CameraSourceSelectionPolicy.choose(CameraSource.EXTERNAL, listOf(front, external)))
    }

    @Test
    fun emptyInventoryFailsClosedForEveryRequest() {
        CameraSource.entries.forEach { source ->
            assertNull(CameraSourceSelectionPolicy.choose(source, emptyList()))
        }
    }

    @Test
    fun mirrorSettingAffectsFrontPreviewOnly() {
        assertTrue(CameraPreviewMirrorPolicy.shouldMirror(CameraSource.FRONT, true))
        assertFalse(CameraPreviewMirrorPolicy.shouldMirror(CameraSource.FRONT, false))
        assertFalse(CameraPreviewMirrorPolicy.shouldMirror(CameraSource.BACK, true))
        assertFalse(CameraPreviewMirrorPolicy.shouldMirror(CameraSource.EXTERNAL, true))
        assertEquals(
            MirrorMode.MIRROR_MODE_ON_FRONT_ONLY,
            CameraPreviewMirrorPolicy.cameraXMirrorMode(CameraSource.FRONT, true)
        )
        assertEquals(
            MirrorMode.MIRROR_MODE_OFF,
            CameraPreviewMirrorPolicy.cameraXMirrorMode(CameraSource.FRONT, false)
        )
        assertEquals(
            1f,
            CameraPreviewMirrorPolicy.previewViewScaleX(
                CameraSource.FRONT,
                true,
                sdkInt = 36
            ),
            0f
        )
        assertEquals(
            1f,
            CameraPreviewMirrorPolicy.previewViewScaleX(
                CameraSource.FRONT,
                false,
                sdkInt = 36
            ),
            0f
        )
        assertEquals(
            -1f,
            CameraPreviewMirrorPolicy.previewViewScaleX(
                CameraSource.FRONT,
                false,
                sdkInt = 32
            ),
            0f
        )
    }
}
