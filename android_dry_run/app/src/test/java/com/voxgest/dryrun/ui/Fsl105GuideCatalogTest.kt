package com.voxgest.dryrun.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Fsl105GuideCatalogTest {
    @Test
    fun `guide inventory exactly matches canonical selected runtime labels`() {
        val asset = locateCanonicalLabelsAsset()
        val sourceJson = asset.readText(Charsets.UTF_8)
        val canonicalJson = JSONObject(sourceJson)
        val canonicalArray = canonicalJson.getJSONArray("labels")
        val canonical = List(canonicalArray.length()) { index -> canonicalArray.getString(index) }

        val guide = Fsl105GuideCatalog.parse(
            sourceJson,
            locatePresentationAsset().readText(Charsets.UTF_8)
        )
        val guideLabels = guide.map(FslGuideEntry::label)

        assertEquals(105, guideLabels.size)
        assertEquals(105, guideLabels.distinct().size)
        assertEquals(canonical, guideLabels)
        assertEquals(canonical.toSet(), guideLabels.toSet())
    }

    @Test
    fun `model vocabulary has complete bilingual semantic presentation without live claims`() {
        val guide = Fsl105GuideCatalog.parse(
            locateCanonicalLabelsAsset().readText(Charsets.UTF_8),
            locatePresentationAsset().readText(Charsets.UTF_8)
        )

        assertTrue(guide.all { it.datasetStatus == "FSL-105 MODEL VOCABULARY" })
        assertTrue(guide.all { it.liveValidationStatus == "NOT YET DEVICE-QUALIFIED" })
        assertTrue(guide.all { it.englishDisplay.isNotBlank() })
        assertTrue(guide.all { it.filipinoDisplay.isNotBlank() })
        assertEquals("Wheelchair person", guide.single { it.label == "WEELCHAIR PERSON" }.englishDisplay)
        assertEquals("Salamat", guide.single { it.label == "THANK YOU" }.filipinoDisplay)
    }

    @Test
    fun `dataset attribution facts match the source record`() {
        assertEquals("Isaiah Jassen Tupal", Fsl105DatasetFacts.CONTRIBUTOR)
        assertEquals("De La Salle University", Fsl105DatasetFacts.INSTITUTION)
        assertEquals("10.17632/48y2y99mb9.1", Fsl105DatasetFacts.DOI)
        assertEquals("Version 1", Fsl105DatasetFacts.VERSION)
        assertEquals("CC BY 4.0", Fsl105DatasetFacts.LICENSE)
        assertEquals(2_130, Fsl105DatasetFacts.VIDEO_COUNT)
        assertEquals(105, Fsl105DatasetFacts.CLASS_COUNT)
    }

    private fun locateCanonicalLabelsAsset(): File {
        val relative = File(
            "src/main/assets/model/fsl_fullsign225_20f_105_v1/" +
                "class_labels_fsl105_fullsign225_v1.json"
        )
        val candidates = listOf(
            relative,
            File("app", relative.path),
            File("android_dry_run/app", relative.path)
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Canonical FSL-105 labels asset not found from ${File(".").absolutePath}")
    }

    private fun locatePresentationAsset(): File {
        val relative = File(
            "src/main/assets/model/fsl_fullsign225_20f_105_v1/" +
                "presentation_fsl105_bilingual_v1.json"
        )
        val candidates = listOf(
            relative,
            File("app", relative.path),
            File("android_dry_run/app", relative.path)
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("FSL-105 presentation asset not found from ${File(".").absolutePath}")
    }
}
