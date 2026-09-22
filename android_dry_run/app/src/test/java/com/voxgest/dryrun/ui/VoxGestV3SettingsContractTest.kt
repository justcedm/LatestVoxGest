package com.voxgest.dryrun.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxGestV3SettingsContractTest {
    @Test
    fun `landmarks are default off and persisted independently`() {
        val source = sourceFile("../VoxGestUserSettings.kt").readText()

        assertTrue(source.contains("val showAiLandmarks: Boolean = false"))
        assertTrue(source.contains("KEY_SHOW_AI_LANDMARKS"))
        assertTrue(source.contains("putBoolean(KEY_SHOW_AI_LANDMARKS, value.showAiLandmarks)"))
        assertTrue(source.contains("getBoolean(KEY_SHOW_AI_LANDMARKS, false)"))
    }

    @Test
    fun `appearance mode and all curated color styles have separate persistence`() {
        val settings = sourceFile("../VoxGestUserSettings.kt").readText()
        val design = sourceFile("VoxGestDesignSystem.kt").readText()

        listOf("TERRACOTTA", "SUN_GOLD", "WARM_SAND", "FILIPINO_HERITAGE").forEach {
            assertTrue(settings.contains(it))
            assertTrue(design.contains("VoxGestColorStyle.$it"))
        }
        assertTrue(settings.contains("putString(KEY_THEME_PREFERENCE"))
        assertTrue(settings.contains("putString(KEY_COLOR_STYLE"))
        assertTrue(design.contains("val Success = Color(0xFF3F8064)"))
        assertTrue(design.contains("val Error = Color(0xFFBA4C42)"))
    }

    private fun sourceFile(relative: String): File {
        val normalized = relative.replace('/', File.separatorChar)
        return listOf(
            File("src/main/java/com/voxgest/dryrun/ui", normalized),
            File("app/src/main/java/com/voxgest/dryrun/ui", normalized),
            File("android_dry_run/app/src/main/java/com/voxgest/dryrun/ui", normalized)
        ).firstOrNull(File::isFile)
            ?: error("$relative not found from ${File(".").absolutePath}")
    }
}
