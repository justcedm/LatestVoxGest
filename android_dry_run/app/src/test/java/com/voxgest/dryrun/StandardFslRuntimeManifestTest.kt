package com.voxgest.dryrun

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StandardFslRuntimeManifestTest {
    @Test
    fun `manifest is the authoritative runtime shape file and label contract`() {
        val config = StandardFslRuntimeManifest.parse(
            locate("runtime_manifest.json").readText(Charsets.UTF_8),
            locate("class_labels_fsl105_fullsign225_v1.json").readText(Charsets.UTF_8)
        )

        assertEquals("STANDARD_FSL_FULLSIGN225", config.profileId)
        assertEquals(20, config.sequenceLength)
        assertEquals(225, config.featureSize)
        assertEquals(105, config.classCount)
        assertArrayEquals(intArrayOf(1, 20, 225), config.inputShape)
        assertArrayEquals(intArrayOf(1, 105), config.outputShape)
        assertEquals(105, config.labels.size)
        assertEquals(105, config.labels.distinct().size)
        assertEquals("APRIL", config.labels.first())
        assertEquals("YOURE WELCOME", config.labels.last())
        assertEquals("unmirrored", config.modelInputOrientation)
        assertTrue(config.rejectionRequired)
        assertTrue(config.runtimeBanner.contains("FSL105_LIVE_SEGMENT_V1"))
        assertFalse(config.runtimeBanner.contains("MAPUA14"))
    }

    private fun locate(filename: String): File {
        val relative = File("src/main/assets/model/fsl_fullsign225_20f_105_v1/$filename")
        return listOf(
            relative,
            File("app", relative.path),
            File("android_dry_run/app", relative.path)
        ).firstOrNull(File::isFile)
            ?: error("$filename not found from ${File(".").absolutePath}")
    }
}
