package com.voxgest.dryrun

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSuggestionSettingsTest {
    @Test
    fun missingPreferenceDefaultsOffAndRequiresExplicitOptIn() {
        val store = MemoryStore()
        val settings = SentenceSuggestionSettings(store)

        assertFalse(settings.isEnabled())
        settings.setEnabled(true)
        assertTrue(settings.isEnabled())
        settings.setEnabled(false)
        assertFalse(settings.isEnabled())
    }

    private class MemoryStore : SentenceSuggestionSettings.Store {
        private val values = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return values[key] ?: defaultValue
        }

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
