package com.voxgest.dryrun.ui

import com.voxgest.dryrun.VoxGestAppLanguage
import com.voxgest.dryrun.VoxGestMessageLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VoxGestLocalizationTest {
    @Test
    fun appCopy_switchesPrimaryNavigationLanguage() {
        assertEquals("Sign", copyFor(VoxGestAppLanguage.ENGLISH).sign)
        assertEquals("Senyas", copyFor(VoxGestAppLanguage.FILIPINO).sign)
    }

    @Test
    fun presenter_preservesSourceTokensAndTranslatesKnownPhrase() {
        val message = VoxGestOfflineMessagePresenter.present("WHAT YOUR NAME")
        assertEquals("WHAT YOUR NAME", message.sourceTokens)
        assertEquals("What your name", message.english)
        assertEquals("Ano ang pangalan mo?", message.filipino)
    }

    @Test
    fun presenter_doesNotInventUnknownTranslation() {
        val message = VoxGestOfflineMessagePresenter.present("UNMAPPED TOKEN")
        assertNull(message.filipino)
        assertEquals("Unmapped token", VoxGestOfflineMessagePresenter.speechText("UNMAPPED TOKEN", VoxGestMessageLanguage.FILIPINO))
    }

    @Test
    fun presenter_usesCompleteFsl105MapForCanonicalAndComposedConcepts() {
        configureFsl105()

        val hello = VoxGestOfflineMessagePresenter.present("HELLO")
        val quirk = VoxGestOfflineMessagePresenter.present("WEELCHAIR PERSON")
        val sentence = VoxGestOfflineMessagePresenter.present("GOOD MORNING YES")

        assertEquals("Hello", hello.english)
        assertEquals("Kumusta", hello.filipino)
        assertEquals("Wheelchair person", quirk.english)
        assertEquals("Taong gumagamit ng wheelchair", quirk.filipino)
        assertEquals("Good Morning Yes", sentence.english)
        assertEquals("Magandang umaga Oo", sentence.filipino)
        assertEquals(2, hello.visibleLines(VoxGestMessageLanguage.BOTH).size)
    }

    private fun configureFsl105() {
        VoxGestOfflineMessagePresenter.configureFsl105(
            Fsl105PresentationCatalog.parse(
                locateAsset("class_labels_fsl105_fullsign225_v1.json").readText(Charsets.UTF_8),
                locateAsset("presentation_fsl105_bilingual_v1.json").readText(Charsets.UTF_8)
            )
        )
    }

    private fun locateAsset(filename: String): File {
        val relative = File("src/main/assets/model/fsl_fullsign225_20f_105_v1/$filename")
        return listOf(
            relative,
            File("app", relative.path),
            File("android_dry_run/app", relative.path)
        ).firstOrNull(File::isFile)
            ?: error("$filename not found from ${File(".").absolutePath}")
    }

}
