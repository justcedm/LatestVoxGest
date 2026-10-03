package com.voxgest.dryrun.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPreviewHostArchitectureTest {
    @Test
    fun normalAndFullscreenUseOneStablePreviewHostWithoutDialogReparenting() {
        val source = sourceFile().readText()
        val start = source.indexOf("private fun RecognitionAreaCard(")
        val end = source.indexOf("private fun defaultFslDeviceSessionTag", start)

        assertTrue("RecognitionAreaCard source section must exist", start >= 0 && end > start)
        val cameraSection = source.substring(start, end)

        assertEquals(1, Regex("AndroidView\\s*\\(").findAll(cameraSection).count())
        assertFalse(cameraSection.contains("Dialog("))
        assertFalse(cameraSection.contains("if (!expanded) {\n                AndroidView"))
        assertTrue(cameraSection.contains("One stable PreviewView host is resized in place"))
        assertTrue(cameraSection.contains("ImageAnalysis/model input remains untouched"))
    }

    private fun sourceFile(): File {
        return listOf(
            File("src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"),
            File("app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"),
            File("android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt")
        ).firstOrNull(File::isFile)
            ?: error("VoxGestPresentationApp.kt not found from ${File(".").absolutePath}")
    }
}
