package com.voxgest.dryrun.ui

import com.voxgest.dryrun.VoxGestAppLanguage
import com.voxgest.dryrun.VoxGestMessageLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

}
