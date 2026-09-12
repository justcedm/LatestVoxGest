package com.voxgest.dryrun

import android.content.Context

/** Persisted opt-in for sentence suggestions. Missing preference keys always resolve to OFF. */
class SentenceSuggestionSettings(
    private val store: Store
) {
    interface Store {
        fun getBoolean(key: String, defaultValue: Boolean): Boolean
        fun putBoolean(key: String, value: Boolean)
    }

    constructor(context: Context) : this(
        AndroidStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    )

    fun isEnabled(): Boolean = store.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(enabled: Boolean) {
        store.putBoolean(KEY_ENABLED, enabled)
    }

    private class AndroidStore(
        private val preferences: android.content.SharedPreferences
    ) : Store {
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return preferences.getBoolean(key, defaultValue)
        }

        override fun putBoolean(key: String, value: Boolean) {
            preferences.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
        const val PREFERENCES_NAME: String = "voxgest_sentence_suggestions"
        const val KEY_ENABLED: String = "sentence_suggestions_enabled"
    }
}
