package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

