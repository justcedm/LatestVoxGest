package com.voxgest.dryrun.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FilipinoHeritageHeaderContractTest {
    @Test
    fun heritageHeaderHasBoundedResponsiveIdentityContract() {
        val source = sourceFile("VoxGestDesignSystem.kt").readText()

        assertTrue(source.contains(".height(if (compact) 64.dp else 72.dp)"))
        assertTrue(source.contains("Signs Connect People"))
        assertTrue(source.contains("Senyas para sa Mas Malawak na Bukas"))
        assertTrue(source.contains("Signs for a Brighter Tomorrow"))
        assertTrue(source.contains("maxWidth >= 390.dp"))
        assertTrue(source.contains("FilipinoHeritageBackdrop"))
    }

    @Test
    fun signUsesCompactHeaderAndConversationUsesReferenceActions() {
        val source = sourceFile("VoxGestPresentationApp.kt").readText()

        assertTrue(source.contains("compact = true"))
        assertTrue(source.contains("Clear history"))
        assertTrue(source.contains("Show in FSL"))
        assertTrue(source.contains("Real-time bilingual chat history"))
    }

    private fun sourceFile(name: String): File {
        return listOf(
            File("src/main/java/com/voxgest/dryrun/ui/$name"),
            File("app/src/main/java/com/voxgest/dryrun/ui/$name"),
            File("android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/$name")
        ).firstOrNull(File::isFile)
            ?: error("$name not found from ${File(".").absolutePath}")
    }
}
