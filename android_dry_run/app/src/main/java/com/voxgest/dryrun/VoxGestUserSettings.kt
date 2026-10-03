package com.voxgest.dryrun

import android.content.Context

enum class PreferredCameraLens {
    FRONT,
    REAR
}

enum class VoxGestAppLanguage {
    ENGLISH,
    FILIPINO
}

enum class VoxGestMessageLanguage {
    ENGLISH,
    FILIPINO,
    BOTH
}

enum class VoxGestThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class VoxGestColorStyle {
    TERRACOTTA,
    SUN_GOLD,
    WARM_SAND,
    FILIPINO_HERITAGE
}

data class VoxGestUserSettingsSnapshot(
    val sentenceSuggestions: Boolean = false,
    val autoSpeakAcceptedMessage: Boolean = false,
    val hapticFeedback: Boolean = true,
    val ttsRate: Float = 1.0f,
    val preferredCameraLens: PreferredCameraLens = PreferredCameraLens.FRONT,
    val trackingOverlay: Boolean = true,
    val mirrorFrontPreview: Boolean = true,
    val runtimeDiagnostics: Boolean = false,
    val appLanguage: VoxGestAppLanguage = VoxGestAppLanguage.ENGLISH,
    val messageLanguage: VoxGestMessageLanguage = VoxGestMessageLanguage.BOTH,
    val themePreference: VoxGestThemePreference = VoxGestThemePreference.SYSTEM,
    val colorStyle: VoxGestColorStyle = VoxGestColorStyle.TERRACOTTA,
    val showAiLandmarks: Boolean = false,
    val reducedMotion: Boolean = false
)

/** Offline settings only. No setting in this store changes model tensors or feature math. */
class VoxGestUserSettings(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): VoxGestUserSettingsSnapshot {
        val legacySuggestionValue = SentenceSuggestionSettings(appContext).isEnabled()
        return VoxGestUserSettingsSnapshot(
            sentenceSuggestions = preferences.getBoolean(KEY_SENTENCE_SUGGESTIONS, legacySuggestionValue),
            autoSpeakAcceptedMessage = preferences.getBoolean(KEY_AUTO_SPEAK, false),
            hapticFeedback = preferences.getBoolean(KEY_HAPTIC, true),
            ttsRate = preferences.getFloat(KEY_TTS_RATE, 1.0f).coerceIn(MIN_TTS_RATE, MAX_TTS_RATE),
            preferredCameraLens = runCatching {
                PreferredCameraLens.valueOf(preferences.getString(KEY_CAMERA_LENS, null).orEmpty())
            }.getOrDefault(PreferredCameraLens.FRONT),
            trackingOverlay = preferences.getBoolean(KEY_TRACKING_OVERLAY, true),
            mirrorFrontPreview = preferences.getBoolean(KEY_MIRROR_PREVIEW, true),
            runtimeDiagnostics = preferences.getBoolean(KEY_RUNTIME_DIAGNOSTICS, false),
            appLanguage = enumPreference(KEY_APP_LANGUAGE, VoxGestAppLanguage.ENGLISH),
            messageLanguage = enumPreference(KEY_MESSAGE_LANGUAGE, VoxGestMessageLanguage.BOTH),
            themePreference = enumPreference(KEY_THEME_PREFERENCE, VoxGestThemePreference.SYSTEM),
            colorStyle = enumPreference(KEY_COLOR_STYLE, VoxGestColorStyle.TERRACOTTA),
            showAiLandmarks = preferences.getBoolean(KEY_SHOW_AI_LANDMARKS, false),
            reducedMotion = preferences.getBoolean(KEY_REDUCED_MOTION, false)
        )
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T {
        return runCatching {
            enumValueOf<T>(preferences.getString(key, null).orEmpty())
        }.getOrDefault(fallback)
    }

    fun save(value: VoxGestUserSettingsSnapshot) {
        preferences.edit()
            .putBoolean(KEY_SENTENCE_SUGGESTIONS, value.sentenceSuggestions)
            .putBoolean(KEY_AUTO_SPEAK, value.autoSpeakAcceptedMessage)
            .putBoolean(KEY_HAPTIC, value.hapticFeedback)
            .putFloat(KEY_TTS_RATE, value.ttsRate.coerceIn(MIN_TTS_RATE, MAX_TTS_RATE))
            .putString(KEY_CAMERA_LENS, value.preferredCameraLens.name)
            .putBoolean(KEY_TRACKING_OVERLAY, value.trackingOverlay)
            .putBoolean(KEY_MIRROR_PREVIEW, value.mirrorFrontPreview)
            .putBoolean(KEY_RUNTIME_DIAGNOSTICS, value.runtimeDiagnostics)
            .putString(KEY_APP_LANGUAGE, value.appLanguage.name)
            .putString(KEY_MESSAGE_LANGUAGE, value.messageLanguage.name)
            .putString(KEY_THEME_PREFERENCE, value.themePreference.name)
            .putString(KEY_COLOR_STYLE, value.colorStyle.name)
            .putBoolean(KEY_SHOW_AI_LANDMARKS, value.showAiLandmarks)
            .putBoolean(KEY_REDUCED_MOTION, value.reducedMotion)
            .apply()
        SentenceSuggestionSettings(appContext).setEnabled(value.sentenceSuggestions)
    }

    fun reset(): VoxGestUserSettingsSnapshot {
        preferences.edit().clear().apply()
        SentenceSuggestionSettings(appContext).setEnabled(false)
        return VoxGestUserSettingsSnapshot()
    }

    companion object {
        const val PREFERENCES_NAME = "voxgest_user_settings"
        const val MIN_TTS_RATE = 0.6f
        const val MAX_TTS_RATE = 1.4f

        private const val KEY_SENTENCE_SUGGESTIONS = "sentence_suggestions"
        private const val KEY_AUTO_SPEAK = "auto_speak_accepted_message"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_CAMERA_LENS = "preferred_camera_lens"
        private const val KEY_TRACKING_OVERLAY = "tracking_overlay"
        private const val KEY_MIRROR_PREVIEW = "mirror_front_preview"
        private const val KEY_RUNTIME_DIAGNOSTICS = "runtime_diagnostics"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_MESSAGE_LANGUAGE = "message_language"
        private const val KEY_THEME_PREFERENCE = "theme_preference"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_SHOW_AI_LANDMARKS = "show_ai_landmarks"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
    }
}
