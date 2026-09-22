package com.voxgest.dryrun.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.camera.view.PreviewView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import com.voxgest.app.avatar.AvatarController
import com.voxgest.app.avatar.AvatarFeatureFlags
import com.voxgest.app.avatar.AvatarPlaybackState
import com.voxgest.app.avatar.AvatarStatus
import com.voxgest.app.avatar.AvatarView
import com.voxgest.app.avatar.Core3AvatarAssets
import com.voxgest.app.avatar.Core3AvatarRuntimeController
import com.voxgest.app.avatar.Core3AvatarState
import com.voxgest.app.avatar.Core3AvatarStatus
import com.voxgest.app.avatar.Core3FilamentHostView
import com.voxgest.app.avatar.Core3ListenTranscriptResolver
import com.voxgest.app.avatar.SceneAvatarHostView
import com.voxgest.dryrun.BuildConfig
import com.voxgest.dryrun.CalibrationExportMode
import com.voxgest.dryrun.DemoAllowlistPolicy
import com.voxgest.dryrun.DetectionStatus
import com.voxgest.dryrun.LandmarkFrame
import com.voxgest.dryrun.LandmarkVisualizationFrame
import com.voxgest.dryrun.NamePhraseDetector
import com.voxgest.dryrun.OneHandCalibrationConfig
import com.voxgest.dryrun.OverlayLandmarkPoint
import com.voxgest.dryrun.R
import com.voxgest.dryrun.RecognitionFeedback
import com.voxgest.dryrun.RecognitionMode
import com.voxgest.dryrun.RecognitionOutputCoordinator
import com.voxgest.dryrun.RecognitionResult
import com.voxgest.dryrun.SentenceSuggestion
import com.voxgest.dryrun.SentenceSuggestionEngine
import com.voxgest.dryrun.SentenceSuggestionSettings
import com.voxgest.dryrun.SignVocabulary
import com.voxgest.dryrun.TokenComposer
import com.voxgest.dryrun.PreferredCameraLens
import com.voxgest.dryrun.RecognitionProfile
import com.voxgest.dryrun.GradingRecognitionProfiles
import com.voxgest.dryrun.VoxGestUserSettings
import com.voxgest.dryrun.VoxGestUserSettingsSnapshot
import com.voxgest.dryrun.VoxGestThemePreference
import com.voxgest.dryrun.VoxGestMessageLanguage
import com.voxgest.dryrun.VoxGestCameraRecognitionController
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Primary = VoxGestDesignTokens.Terracotta
private val PrimaryLight = VoxGestDesignTokens.SunGold
private val AppBg = VoxGestDesignTokens.SoftCream
private val CardWhite = Color(0xFFFFFFFF)
private val SoftCyan = Color(0xFFFFEEE4)
private val AccentGreen = VoxGestDesignTokens.Success
private val TextMain = VoxGestDesignTokens.DeepCharcoal
private val TextMuted = Color(0xFF5E666B)
private val TextFaint = Color(0xFF7A8286)
private val Border = Color(0xFFE6DCD4)
private val DarkInk = VoxGestDesignTokens.DeepCharcoal
private val Amber = Color(0xFFA76600)
private val Red = VoxGestDesignTokens.Error
private val Blue = Color(0xFF22A7D8)
private val Purple = Color(0xFF7C5AA6)
private val Green = VoxGestDesignTokens.Success
private const val PRESENTATION_MODE = true
private const val SHOW_DEBUG_TOOLS = true
private const val ACCURACY_TEST_WINDOW_MS = 5000L
private const val RECOGNITION_LOG_TAG = "VoxGestRecognition"
private val STABLE_DEMO_ALLOWLIST = setOf("WHAT", "YOUR", "NAME", "MY")

private enum class VoxTab(
    @DrawableRes val icon: Int
) {
    Sign(R.drawable.ic_hand_gesture),
    Conversation(R.drawable.ic_chat),
    Listen(R.drawable.ic_mic),
    Guide(R.drawable.ic_book)
}

private data class HistoryUiEntry(
    val section: String,
    val type: String,
    val text: String,
    val time: String,
    val status: String,
    val icon: Int,
    val iconColor: Color
)

private enum class ConversationParticipant {
    FSL_USER,
    HEARING_USER
}

private enum class SpeechInputLanguage(val languageTag: String) {
    ENGLISH("en-US"),
    FILIPINO("fil-PH")
}

private enum class ConversationPresentationState {
    SPOKEN_ALOUD,
    SHOWN_AS_SIGNS
}

private data class ConversationUiEntry(
    val id: Long,
    val participant: ConversationParticipant,
    val text: String,
    val timestampMillis: Long,
    val presentationState: ConversationPresentationState
)

private data class PhraseUi(
    val label: String,
    val icon: Int,
    val color: Color
)

private val QuickPhrases = listOf(
    PhraseUi("I need help", R.drawable.ic_warning, Color(0xFFF97316)),
    PhraseUi("Call a doctor", R.drawable.ic_medical, PrimaryLight),
    PhraseUi("I need water", R.drawable.ic_water_drop, Blue),
    PhraseUi("Stop", R.drawable.ic_hand_gesture, Red),
    PhraseUi("Please wait", R.drawable.ic_clock, Amber),
    PhraseUi("Thank you", R.drawable.ic_hand_gesture, Purple),
    PhraseUi("Yes", R.drawable.ic_check_circle, Green),
    PhraseUi("No", R.drawable.ic_no_circle, Red),
    PhraseUi("What is your name?", R.drawable.ic_chat, Primary),
    PhraseUi("Are you okay?", R.drawable.ic_check_circle, Green),
    PhraseUi("Are you a student?", R.drawable.ic_chat, Blue),
    PhraseUi("Where do you live?", R.drawable.ic_history, Purple)
)

private val DemoTokenWords = listOf(
    "WHAT",
    "YOUR",
    "NAME",
    "MY",
    "A",
    "B",
    "C",
    "D",
    "E",
    "F",
    "G",
    "H",
    "I",
    "J",
    "K",
    "L",
    "M",
    "N",
    "O",
    "P",
    "Q",
    "R",
    "S",
    "T",
    "U",
    "V",
    "W",
    "X",
    "Y",
    "Z",
    "DEL",
    "CLEAR",
    "SPEAK"
)

private val AccuracyTestTargets = listOf("WHAT", "YOUR", "NAME", "MY", "YOU", "OKAY") +
    ('A'..'Z').map { it.toString() }

private data class AccuracyCounter(
    val correct: Int = 0,
    val attempts: Int = 0
) {
    val percentage: Int
        get() = if (attempts == 0) 0 else ((correct.toFloat() / attempts.toFloat()) * 100f).toInt()
}

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20
)

private val POSE_BODY_CONNECTIONS = listOf(
    11 to 12,
    11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24,
    23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
    24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32
)

@Composable
fun VoxGestPresentationApp(
    windowSizeClass: WindowSizeClass,
    developerDiagnosticsEnabled: Boolean = false
) {
    var selectedTab by rememberSaveable { mutableStateOf(VoxTab.Sign) }
    var currentWord by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf("") }
    var demoTokenBuffer by remember { mutableStateOf("") }
    var recognitionRunning by remember { mutableStateOf(false) }
    var recognitionStatus by remember { mutableStateOf("Tap Start Recognition") }
    var namePhraseHint by remember { mutableStateOf("") }
    var pendingAvatarText by remember { mutableStateOf("") }
    var pendingAvatarRequestId by remember { mutableStateOf(0) }
    var selectedPhrase by remember { mutableStateOf("") }
    var latestRecognition by remember { mutableStateOf<RecognitionResult?>(null) }
    var latestOutputReason by remember { mutableStateOf("NO_ACCEPTED_SIGN") }
    var sentenceSuggestions by remember { mutableStateOf<List<SentenceSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<SentenceSuggestion?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var cameraExpanded by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<HistoryUiEntry>() }
    val conversationEntries = remember { mutableStateListOf<ConversationUiEntry>() }
    var nextConversationEntryId by remember { mutableStateOf(1L) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val userSettingsStore = remember(context) { VoxGestUserSettings(context) }
    var userSettings by remember { mutableStateOf(userSettingsStore.load()) }
    var showSplash by remember { mutableStateOf(true) }
    val copy = copyFor(userSettings.appLanguage)
    val useDarkTheme = when (userSettings.themePreference) {
        VoxGestThemePreference.SYSTEM -> isSystemInDarkTheme()
        VoxGestThemePreference.LIGHT -> false
        VoxGestThemePreference.DARK -> true
    }
    val profilePresentations = remember(context) { packagedProfilePresentations(context) }
    val namePhraseDetector = remember { NamePhraseDetector() }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val suggestionSettings = remember(context) { SentenceSuggestionSettings(context) }
    val tokenComposer = remember { TokenComposer(STABLE_DEMO_ALLOWLIST) }
    val outputCoordinator = remember(context) {
        RecognitionOutputCoordinator(
            allowlistPolicy = DemoAllowlistPolicy(STABLE_DEMO_ALLOWLIST),
            composer = tokenComposer,
            suggestionEngine = SentenceSuggestionEngine(),
            suggestionSettings = suggestionSettings
        )
    }
    fun speakNow(text: String, language: Locale = Locale.US) {
        if (!isMeaningfulOutput(text)) return
        ttsRef.value?.let { engine ->
            if (engine.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) {
                engine.language = Locale.US
            }
            speak(engine, text)
        }
    }

    fun queueAvatarPhrase(text: String) {
        if (!isMeaningfulOutput(text)) return
        pendingAvatarText = text
        pendingAvatarRequestId += 1
        selectedTab = VoxTab.Listen
    }

    fun clearDemoTokens() {
        outputCoordinator.clear()
        demoTokenBuffer = ""
        currentWord = ""
        sentence = ""
        latestRecognition = null
        latestOutputReason = "CLEARED_BY_USER"
        sentenceSuggestions = emptyList()
        selectedSuggestion = null
        namePhraseHint = ""
        namePhraseDetector.reset()
    }

    fun addConversationEntry(
        participant: ConversationParticipant,
        text: String,
        presentationState: ConversationPresentationState
    ) {
        val clean = text.trim()
        if (!isMeaningfulOutput(clean)) return
        conversationEntries.add(
            ConversationUiEntry(
                id = nextConversationEntryId++,
                participant = participant,
                text = clean,
                timestampMillis = System.currentTimeMillis(),
                presentationState = presentationState
            )
        )
    }

    fun applyComposerSnapshot() {
        val snapshot = outputCoordinator.snapshot()
        sentence = snapshot.sentence
        currentWord = snapshot.tokens.lastOrNull().orEmpty()
        sentenceSuggestions = outputCoordinator.currentSuggestions()
        selectedSuggestion = null
    }

    fun acceptRecognitionForOutput(result: RecognitionResult) {
        latestRecognition = result
        val update = outputCoordinator.handleRecognition(result)
        latestOutputReason = update.reason
        if (!update.userFacingAccepted) {
            Log.i(
                RECOGNITION_LOG_TAG,
                "ui_output_rejected label=${result.label} confidence=${result.confidence} " +
                    "margin=${result.margin} reason=${update.reason}"
            )
            return
        }
        currentWord = update.allowlistDecision.canonicalLabel
        sentence = update.snapshot.sentence
        sentenceSuggestions = update.suggestions
        selectedSuggestion = null
        if (userSettings.hapticFeedback) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (userSettings.autoSpeakAcceptedMessage && isMeaningfulOutput(update.snapshot.sentence)) {
            speakNow(
                VoxGestOfflineMessagePresenter.speechText(update.snapshot.sentence, userSettings.messageLanguage),
                speechLocaleFor(update.snapshot.sentence, userSettings.messageLanguage)
            )
            addConversationEntry(
                ConversationParticipant.FSL_USER,
                update.snapshot.sentence,
                ConversationPresentationState.SPOKEN_ALOUD
            )
        }
        Log.i(
            RECOGNITION_LOG_TAG,
            "ui_output_accepted label=$currentWord confidence=${result.confidence} " +
                "margin=${result.margin} sentence=$sentence"
        )
    }

    fun applyNamePhraseUpdate(update: com.voxgest.dryrun.NamePhraseUpdate) {
        namePhraseHint = update.hint
        if (update.sentence.isNotBlank()) sentence = update.sentence
    }

    fun logUiUpdate(tokens: List<String>) {
        Log.i(
            RECOGNITION_LOG_TAG,
            "ui_update currentWord=$currentWord tokens=${tokens.joinToString(prefix = "[", postfix = "]")} sentence=$sentence"
        )
    }

    fun addDemoToken(token: String) {
        val raw = token.trim()
        val clean = SignVocabulary.findByLabelOrAlias(raw)
            ?.let { SignVocabulary.normalizeRuntimeLabel(it.label) }
            ?: raw.uppercase(Locale.US)
        when (clean) {
            "", "NSAC" -> return
            "DEL" -> {
                val nextTokens = demoTokenBuffer.toDemoTokens().dropLast(1)
                demoTokenBuffer = nextTokens.joinToString("|")
                currentWord = nextTokens.lastOrNull() ?: ""
                sentence = demoSentenceForTokens(nextTokens) ?: nextTokens.joinToString(" ")
                namePhraseDetector.reset()
                namePhraseHint = ""
                return
            }
            "SPACE" -> {
                namePhraseDetector.reset()
                namePhraseHint = ""
                return
            }
            "CLEAR" -> {
                clearDemoTokens()
                return
            }
            "SPEAK" -> {
                if (isMeaningfulOutput(sentence)) {
                    speakNow(sentence)
                    history.add(0, HistoryUiEntry("Today", "Sign", sentence, nowLabel(), "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                }
                return
            }
        }

        val isLetter = clean.length == 1 && clean[0] in 'A'..'Z'
        if (isLetter && namePhraseDetector.isActive()) {
            val tokens = (demoTokenBuffer.toDemoTokens() + clean).takeLast(32)
            demoTokenBuffer = tokens.joinToString("|")
            currentWord = clean
            history.add(0, HistoryUiEntry("Today", "Sign", clean, nowLabel(), "Accepted letter", R.drawable.ic_hand_gesture, PrimaryLight))
            applyNamePhraseUpdate(namePhraseDetector.acceptLetter(clean[0], SystemClock.elapsedRealtime()))
            logUiUpdate(tokens)
            return
        }

        val tokens = (demoTokenBuffer.toDemoTokens() + clean).takeLast(32)
        demoTokenBuffer = tokens.joinToString("|")
        currentWord = clean
        history.add(0, HistoryUiEntry("Today", "Sign", clean, nowLabel(), "Accepted sign", R.drawable.ic_hand_gesture, PrimaryLight))

        val finalized = demoSentenceForTokens(tokens)
        if (finalized != null) {
            sentence = finalized
            namePhraseDetector.reset()
            namePhraseHint = ""
            history.add(0, HistoryUiEntry("Today", "Sign", finalized, nowLabel(), "From recognition", R.drawable.ic_hand_gesture, PrimaryLight))
            // Sign recognition should not auto-jump to Listen/Avatar.
            // queueAvatarPhrase(finalized)
            logUiUpdate(tokens)
            return
        }

        val nameUpdate = namePhraseDetector.observeAcceptedTokens(tokens, SystemClock.elapsedRealtime())
        if (nameUpdate.hint.isNotBlank()) {
            val phraseTokens = (tokens + "IS").takeLast(32)
            demoTokenBuffer = phraseTokens.joinToString("|")
            applyNamePhraseUpdate(nameUpdate)
            history.add(0, HistoryUiEntry("Today", "Sign", nameUpdate.sentence, nowLabel(), "Name phrase started", R.drawable.ic_hand_gesture, PrimaryLight))
            logUiUpdate(phraseTokens)
            return
        }

        sentence = tokens.joinToString(" ")
        logUiUpdate(tokens)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            val update = namePhraseDetector.checkPause(SystemClock.elapsedRealtime())
            if (update.finalized) {
                applyNamePhraseUpdate(update)
                history.add(0, HistoryUiEntry("Today", "Sign", update.sentence, nowLabel(), "Name phrase finalized", R.drawable.ic_hand_gesture, PrimaryLight))
            }
        }
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef.value?.language = Locale.US
                ttsRef.value?.setSpeechRate(userSettings.ttsRate)
            }
        }
        ttsRef.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsRef.value = null
        }
    }

    LaunchedEffect(userSettings.ttsRate) {
        ttsRef.value?.setSpeechRate(userSettings.ttsRate)
    }

    LaunchedEffect(userSettings.sentenceSuggestions) {
        suggestionSettings.setEnabled(userSettings.sentenceSuggestions)
        sentenceSuggestions = outputCoordinator.currentSuggestions()
        if (!userSettings.sentenceSuggestions) selectedSuggestion = null
    }

    LaunchedEffect(Unit) {
        delay(if (userSettings.reducedMotion) 350L else 1_150L)
        showSplash = false
    }

    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClass,
        LocalVoxGestCopy provides copy
    ) {
        MaterialTheme(
            colorScheme = voxGestColorScheme(userSettings.colorStyle, useDarkTheme),
            typography = VoxGestTypography
        ) {
            val view = LocalView.current
            val statusBarColor = when {
                showSplash -> MaterialTheme.colorScheme.primary
                cameraExpanded -> Color.Black
                else -> MaterialTheme.colorScheme.background
            }
            val navigationBarColor = when {
                showSplash -> MaterialTheme.colorScheme.primary
                cameraExpanded -> Color.Black
                else -> MaterialTheme.colorScheme.surface
            }
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                window.statusBarColor = statusBarColor.toArgb()
                window.navigationBarColor = navigationBarColor.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme && !showSplash && !cameraExpanded
                    isAppearanceLightNavigationBars = !useDarkTheme && !showSplash && !cameraExpanded
                }
            }
            if (showSplash) {
                VoxGestSplashScreen(reducedMotion = userSettings.reducedMotion)
            } else {
                Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!cameraExpanded || selectedTab != VoxTab.Sign) {
                        BottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = {
                                cameraExpanded = false
                                selectedTab = it
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            VoxTab.Sign -> SignScreen(
                            currentWord = currentWord,
                            sentence = sentence,
                            recognitionRunning = recognitionRunning,
                            recognitionStatus = recognitionStatus,
                            namePhraseHint = namePhraseHint,
                            presentationMode = PRESENTATION_MODE,
                            latestRecognition = latestRecognition,
                            latestOutputReason = latestOutputReason,
                            suggestions = sentenceSuggestions,
                            selectedSuggestion = selectedSuggestion,
                            messageLanguage = userSettings.messageLanguage,
                            developerDiagnosticsEnabled = developerDiagnosticsEnabled,
                            showRuntimeMetrics = developerDiagnosticsEnabled || userSettings.runtimeDiagnostics,
                            preferredCameraLens = userSettings.preferredCameraLens,
                            trackingOverlayEnabled = userSettings.trackingOverlay,
                            showAiLandmarks = userSettings.showAiLandmarks,
                            mirrorFrontPreview = userSettings.mirrorFrontPreview,
                            cameraExpanded = cameraExpanded,
                            onCameraExpandedChange = { cameraExpanded = it },
                            onPreferredCameraLensChange = { lens ->
                                val updated = userSettings.copy(preferredCameraLens = lens)
                                userSettingsStore.save(updated)
                                userSettings = updated
                            },
                            onOpenSettings = { settingsOpen = true },
                            onSuggestionSelected = { suggestion ->
                                selectedSuggestion = outputCoordinator.selectSuggestion(suggestion.id)
                            },
                            onMessageLanguageChange = { language ->
                                val updated = userSettings.copy(messageLanguage = language)
                                userSettingsStore.save(updated)
                                userSettings = updated
                            },
                            onSpeak = {
                                val sourceText = selectedSuggestion?.text ?: sentence
                                val spokenText = VoxGestOfflineMessagePresenter.speechText(
                                    sourceText,
                                    userSettings.messageLanguage
                                )
                                if (isMeaningfulOutput(sourceText)) {
                                    speakNow(spokenText, speechLocaleFor(sourceText, userSettings.messageLanguage))
                                    addConversationEntry(
                                        ConversationParticipant.FSL_USER,
                                        sourceText,
                                        ConversationPresentationState.SPOKEN_ALOUD
                                    )
                                    history.add(0, HistoryUiEntry("Today", "Sign", sourceText, nowLabel(), "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                                }
                            },
                            onDelete = {
                                tokenComposer.accept("del")
                                latestOutputReason = "UNDO_BY_USER"
                                applyComposerSnapshot()
                            },
                            onClear = {
                                clearDemoTokens()
                            },
                            onStartRecognition = {
                                recognitionRunning = true
                                recognitionStatus = "Starting Camera"
                                currentWord = ""
                            },
                            onStopRecognition = {
                                recognitionRunning = false
                                recognitionStatus = "Recognition Paused"
                            },
                            onRecognitionStatus = { recognitionStatus = it },
                            onAcceptedRecognition = ::acceptRecognitionForOutput,
                            demoTokens = demoTokenBuffer.toDemoTokens(),
                            onDemoToken = { addDemoToken(it) }
                            )
                            VoxTab.Listen -> ListenScreen(
                            pendingAvatarText = pendingAvatarText,
                            pendingAvatarRequestId = pendingAvatarRequestId,
                            recentEntries = conversationEntries.takeLast(3).reversed(),
                            messageLanguage = userSettings.messageLanguage,
                            reducedMotion = userSettings.reducedMotion,
                            developerDiagnosticsEnabled = developerDiagnosticsEnabled,
                            onOpenSettings = { settingsOpen = true },
                            onSpeechSaved = { text ->
                                if (isMeaningfulOutput(text)) {
                                    addConversationEntry(
                                        ConversationParticipant.HEARING_USER,
                                        text,
                                        ConversationPresentationState.SHOWN_AS_SIGNS
                                    )
                                    history.add(0, HistoryUiEntry("Today", "Speech", text, nowLabel(), "Shown in signs", R.drawable.ic_waveform, Primary))
                                }
                            }
                            )
                            VoxTab.Conversation -> ConversationScreen(
                            entries = conversationEntries,
                            messageLanguage = userSettings.messageLanguage,
                            onClearAll = { conversationEntries.clear() },
                            onNewExchange = { selectedTab = VoxTab.Sign },
                            onOpenSettings = { settingsOpen = true },
                            onReplay = { entry ->
                                if (isMeaningfulOutput(entry.text)) {
                                    when (entry.participant) {
                                        ConversationParticipant.FSL_USER -> speakNow(
                                            VoxGestOfflineMessagePresenter.speechText(entry.text, userSettings.messageLanguage),
                                            speechLocaleFor(entry.text, userSettings.messageLanguage)
                                        )
                                        ConversationParticipant.HEARING_USER -> queueAvatarPhrase(entry.text)
                                    }
                                }
                            }
                            )
                            VoxTab.Guide -> GuideScreen(
                                reducedMotion = userSettings.reducedMotion,
                                onOpenSettings = { settingsOpen = true }
                            )
                        }
                    }
                }
            }
                if (settingsOpen) {
                    VoxGestSettingsDialog(
                    settings = userSettings,
                    activeProfile = profilePresentations.first,
                    experimentalProfiles = profilePresentations.second,
                    appVersion = BuildConfig.VERSION_NAME,
                    messagePreview = sentence,
                    onSettingsChange = { updated ->
                        userSettingsStore.save(updated)
                        userSettings = updated
                    },
                    onClearConversation = { conversationEntries.clear() },
                    onResetPreferences = {
                        userSettings = userSettingsStore.reset()
                    },
                    onDismiss = { settingsOpen = false }
                    )
                }
            }
        }
    }
}

private fun speak(tts: TextToSpeech, text: String) {
    val clean = text.trim()
    if (isMeaningfulOutput(clean)) {
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "voxgest-speak")
    }
}

private fun nowLabel(): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date())
}

private fun speechLocaleFor(sourceText: String, language: VoxGestMessageLanguage): Locale {
    val hasFilipino = VoxGestOfflineMessagePresenter.present(sourceText).filipino != null
    return if (language == VoxGestMessageLanguage.FILIPINO && hasFilipino) {
        Locale.forLanguageTag("fil-PH")
    } else {
        Locale.US
    }
}

private fun packagedProfilePresentations(
    context: Context
): Pair<PackagedProfilePresentation, List<PackagedProfilePresentation>> {
    val active = runCatching { RecognitionProfile.loadDefault(context) }
        .map { profile ->
            PackagedProfilePresentation(
                title = profile.id,
                badge = "ACTIVE LAUNCHER",
                model = profile.modelAsset.substringAfterLast('/'),
                classCount = profile.labels.size,
                contract = profile.featureProfile,
                inputTensor = "${profile.shapeText()} float32",
                modelSize = assetSizeLabel(context, profile.modelAsset),
                orientation = if (profile.mirroredInput) "mirrored (active profile contract)" else "unmirrored"
            )
        }
        .getOrElse { error ->
            PackagedProfilePresentation(
                title = "profile unavailable",
                badge = "LOAD ERROR",
                model = error.javaClass.simpleName,
                classCount = 0,
                contract = "unavailable",
                inputTensor = "unavailable",
                modelSize = "unavailable",
                orientation = "unavailable"
            )
        }

    val experimental = listOf(
        GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162,
        GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
    ).map { spec ->
        PackagedProfilePresentation(
            title = spec.id.name,
            badge = "EXPERIMENTAL Â· GATED",
            model = spec.modelAsset.substringAfterLast('/'),
            classCount = spec.classCount,
            contract = spec.featureVersion,
            inputTensor = "${spec.inputShape.contentToString()} float32",
            modelSize = assetSizeLabel(context, spec.modelAsset),
            orientation = spec.modelInputOrientation.name
        )
    }
    return active to experimental
}

private fun assetSizeLabel(context: Context, assetPath: String): String {
    val bytes = runCatching { context.assets.open(assetPath).use { it.available().toLong() } }
        .getOrNull() ?: return "missing"
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.US, "%.2f MiB", bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%.1f KiB", bytes.toDouble() / 1024.0)
    }
}

private fun exportAccuracyReport(
    context: Context,
    targets: List<String>,
    stats: Map<String, AccuracyCounter>
): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "voxgest_accuracy_$stamp.txt"
    val totalAttempts = stats.values.sumOf { it.attempts }
    val totalCorrect = stats.values.sumOf { it.correct }
    val overall = if (totalAttempts == 0) 0 else ((totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f).toInt()
    val body = buildString {
        appendLine("VoxGest Live Accuracy Test")
        appendLine("Generated: $stamp")
        appendLine("Overall: $overall% ($totalCorrect/$totalAttempts)")
        appendLine()
        targets.forEach { label ->
            val counter = stats[label] ?: AccuracyCounter()
            appendLine("$label: ${counter.percentage}% (${counter.correct}/${counter.attempts})")
        }
    }

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "Export failed"
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            } ?: return "Export failed"
            "Exported $fileName"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, fileName)
            file.writeText(body, Charsets.UTF_8)
            "Exported ${file.name}"
        }
    } catch (exc: Throwable) {
        "Export failed: ${exc.message ?: "unknown error"}"
    }
}

private fun isMeaningfulOutput(text: String): Boolean {
    return text.trim().isNotBlank() && text.trim().uppercase(Locale.US) != "NSAC"
}

private fun String.toDemoTokens(): List<String> {
    return split("|").map { it.trim() }.filter { it.isNotBlank() && it != "NSAC" }
}

private fun demoSentenceForTokens(tokens: List<String>): String? {
    val clean = tokens.map { it.uppercase(Locale.US) }.filter { it.isNotBlank() && it != "NSAC" }
    val nameStart = clean.indexOfLastMyNameIs()
    if (nameStart >= 0 && clean.size > nameStart + 3) {
        val letters = clean.drop(nameStart + 3)
            .filter { it.length == 1 && it[0] in 'A'..'Z' }
            .joinToString("")
        if (letters.isNotBlank()) return "My name is $letters"
    }
    return when {
        clean.endsWithTokens("WHAT", "YOUR", "NAME") -> "What is your name?"
        clean.endsWithTokens("MY", "NAME") -> null
        clean.endsWithTokens("MY", "NAME", "IS") -> null
        clean.endsWithTokens("YOU", "OKAY") -> "Are you okay?"
        clean.endsWithTokens("YOU", "STUDENT") -> "Are you a student?"
        clean.endsWithTokens("WHERE", "YOU", "LIVE") -> "Where do you live?"
        else -> null
    }
}

private fun List<String>.indexOfLastMyNameIs(): Int {
    for (index in size - 3 downTo 0) {
        if (this[index] == "MY" && this[index + 1] == "NAME" && this[index + 2] == "IS") {
            return index
        }
    }
    return -1
}

private fun List<String>.endsWithTokens(vararg expected: String): Boolean {
    return size >= expected.size && takeLast(expected.size) == expected.toList()
}

@Composable
private fun ScreenTopBar(
    title: String,
    @DrawableRes trailing: Int,
    secondTrailing: Int? = null,
    compact: Boolean = false,
    showLogo: Boolean = false,
    onLogoLongPress: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    VoxGestCapstoneTopBar(
        sectionTitle = title
            .lowercase(Locale.US)
            .replaceFirstChar { it.titlecase(Locale.US) }
            .replace("Fsl", "FSL"),
        trailingIcon = trailing,
        secondTrailingIcon = secondTrailing,
        compact = compact,
        onBrandLongPress = onLogoLongPress.takeIf { showLogo || onLogoLongPress != null },
        onTrailingClick = onTrailingClick
    )
}

@Composable
private fun SignScreen(
    currentWord: String,
    sentence: String,
    recognitionRunning: Boolean,
    recognitionStatus: String,
    namePhraseHint: String,
    presentationMode: Boolean,
    latestRecognition: RecognitionResult?,
    latestOutputReason: String,
    suggestions: List<SentenceSuggestion>,
    selectedSuggestion: SentenceSuggestion?,
    messageLanguage: VoxGestMessageLanguage,
    developerDiagnosticsEnabled: Boolean,
    showRuntimeMetrics: Boolean,
    preferredCameraLens: PreferredCameraLens,
    trackingOverlayEnabled: Boolean,
    showAiLandmarks: Boolean,
    mirrorFrontPreview: Boolean,
    cameraExpanded: Boolean,
    onCameraExpandedChange: (Boolean) -> Unit,
    onPreferredCameraLensChange: (PreferredCameraLens) -> Unit,
    onOpenSettings: () -> Unit,
    onSuggestionSelected: (SentenceSuggestion) -> Unit,
    onMessageLanguageChange: (VoxGestMessageLanguage) -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onRecognitionStatus: (String) -> Unit,
    onAcceptedRecognition: (RecognitionResult) -> Unit,
    demoTokens: List<String>,
    onDemoToken: (String) -> Unit
) {
    val copy = LocalVoxGestCopy.current
    var showCalibrationPanel by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = cameraExpanded) { onCameraExpandedChange(false) }
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !cameraExpanded) {
            ScreenTopBar(
                copy.sign,
                R.drawable.ic_settings,
                compact = true,
                onTrailingClick = onOpenSettings
            )
        }
        RecognitionAreaCard(
            modifier = if (cameraExpanded) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            },
            acceptedWord = currentWord,
            acceptedConfidence = latestRecognition?.takeIf {
                currentWord.isNotBlank() && it.label.equals(currentWord, ignoreCase = true)
            }?.confidence,
            message = sentence,
            messageLanguage = messageLanguage,
            recognitionRunning = recognitionRunning,
            recognitionStatus = recognitionStatus,
            namePhraseHint = namePhraseHint,
            presentationMode = presentationMode,
            showCalibrationPanel = showCalibrationPanel,
            developerDiagnosticsEnabled = developerDiagnosticsEnabled,
            preferredCameraLens = preferredCameraLens,
            trackingOverlayEnabled = trackingOverlayEnabled,
            showAiLandmarks = showAiLandmarks,
            mirrorFrontPreview = mirrorFrontPreview,
            expanded = cameraExpanded,
            onExpandedChange = onCameraExpandedChange,
            onPreferredCameraLensChange = onPreferredCameraLensChange,
            onStartRecognition = onStartRecognition,
            onStopRecognition = onStopRecognition,
            onRecognitionStatus = onRecognitionStatus,
            onAcceptedRecognition = onAcceptedRecognition,
            onSpeak = onSpeak
        )
        if (!cameraExpanded) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                CurrentWordCard(
                    word = currentWord,
                    confidence = latestRecognition?.confidence,
                    showConfidence = showRuntimeMetrics
                )
                SentenceCard(
                    sentence = sentence,
                    messageLanguage = messageLanguage,
                    suggestions = suggestions,
                    selectedSuggestion = selectedSuggestion,
                    onSuggestionSelected = onSuggestionSelected,
                    onMessageLanguageChange = onMessageLanguageChange
                )
                if (showRuntimeMetrics) {
                    SignDiagnosticsPanel(
                        expanded = diagnosticsExpanded,
                        recognitionStatus = recognitionStatus,
                        result = latestRecognition,
                        outputReason = latestOutputReason,
                        calibrationVisible = showCalibrationPanel,
                        allowLegacyTools = developerDiagnosticsEnabled,
                        onToggleExpanded = { diagnosticsExpanded = !diagnosticsExpanded },
                        onToggleCalibration = {
                            if (BuildConfig.DEBUG && OneHandCalibrationConfig.ENABLE_ONEHAND_CALIBRATION_RECORDING) {
                                showCalibrationPanel = !showCalibrationPanel
                            }
                        }
                    )
                }
                if (developerDiagnosticsEnabled && SHOW_DEBUG_TOOLS) {
                    DemoTokenPanel(
                        tokens = demoTokens,
                        onToken = onDemoToken
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoxGestCapstoneAction(copy.undo, R.drawable.ic_delete_outline, false, Modifier.weight(1f), onClick = onDelete)
                    VoxGestCapstoneAction(copy.speak, R.drawable.ic_volume_up, true, Modifier.weight(1f), onClick = onSpeak)
                    VoxGestCapstoneAction(copy.clear, R.drawable.ic_cancel, false, Modifier.weight(1f), onClick = onClear)
                }
                if (developerDiagnosticsEnabled) PresentationBuildFooter()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecognitionModePill(
    recognitionMode: RecognitionMode,
    onRecognitionModeChange: (RecognitionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(SoftCyan)
            .border(1.dp, Border, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RecognitionModeSegment(
            label = "Words",
            selected = recognitionMode == RecognitionMode.WORDS,
            modifier = Modifier.weight(1f)
        ) {
            onRecognitionModeChange(RecognitionMode.WORDS)
        }
        RecognitionModeSegment(
            label = if (recognitionMode == RecognitionMode.PHRASE) "Phrase A-Z" else "A-Z",
            selected = recognitionMode == RecognitionMode.ALPHABET || recognitionMode == RecognitionMode.PHRASE,
            modifier = Modifier.weight(1f)
        ) {
            onRecognitionModeChange(RecognitionMode.ALPHABET)
        }
    }
}

@Composable
private fun RecognitionModeSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Primary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DebugSettingsPanel(
    flipLandmarksHorizontal: Boolean,
    onFlipLandmarksHorizontalChange: (Boolean) -> Unit
) {
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text("Debug Settings", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onFlipLandmarksHorizontalChange(!flipLandmarksHorizontal) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (flipLandmarksHorizontal) Primary else CardWhite),
            border = BorderStroke(1.dp, if (flipLandmarksHorizontal) Primary else Border)
        ) {
            Text(
                "Flip landmarks horizontal: ${if (flipLandmarksHorizontal) "ON" else "OFF"}",
                color = if (flipLandmarksHorizontal) Color.White else TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccuracyDebugPanel(
    latestAcceptedLabel: String,
    latestAcceptedEventId: Long,
    onModeForTarget: (RecognitionMode) -> Unit
) {
    val context = LocalContext.current
    val stats = remember { mutableStateMapOf<String, AccuracyCounter>() }
    var targetIndex by remember { mutableStateOf(0) }
    var windowStartedAtMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var remainingSeconds by remember { mutableStateOf(5) }
    val target = AccuracyTestTargets[targetIndex]

    fun advanceTarget() {
        targetIndex = (targetIndex + 1) % AccuracyTestTargets.size
        windowStartedAtMs = SystemClock.elapsedRealtime()
        remainingSeconds = 5
    }

    fun recordAttempt(predicted: String?) {
        val currentTarget = AccuracyTestTargets[targetIndex]
        val previous = stats[currentTarget] ?: AccuracyCounter()
        val correct = predicted?.uppercase(Locale.US) == currentTarget
        stats[currentTarget] = previous.copy(
            correct = previous.correct + if (correct) 1 else 0,
            attempts = previous.attempts + 1
        )
        advanceTarget()
    }

    LaunchedEffect(target) {
        onModeForTarget(if (target.length == 1) RecognitionMode.ALPHABET else RecognitionMode.WORDS)
    }

    LaunchedEffect(latestAcceptedEventId) {
        if (latestAcceptedEventId > 0L) {
            recordAttempt(latestAcceptedLabel)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val elapsed = SystemClock.elapsedRealtime() - windowStartedAtMs
            remainingSeconds = (5 - (elapsed / 1000L).toInt()).coerceIn(0, 5)
            if (elapsed >= ACCURACY_TEST_WINDOW_MS) {
                recordAttempt(null)
            }
        }
    }

    val totalAttempts = stats.values.sumOf { it.attempts }
    val totalCorrect = stats.values.sumOf { it.correct }
    val overall = if (totalAttempts == 0) 0 else ((totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f).toInt()

    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live Accuracy Test", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Target: $target Ã‚Â· ${remainingSeconds}s", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Overall: $overall% ($totalCorrect/$totalAttempts)", color = TextMuted, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val fileName = exportAccuracyReport(context, AccuracyTestTargets, stats)
                    Toast.makeText(context, fileName, Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Export", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccuracyTestTargets.forEach { label ->
                val counter = stats[label] ?: AccuracyCounter()
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (label == target) Color(0xFFE8F7EE) else SoftCyan,
                    border = BorderStroke(1.dp, if (label == target) Primary else Border)
                ) {
                    Text(
                        "$label ${counter.percentage}% ${counter.correct}/${counter.attempts}",
                        color = if (label == target) Primary else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionAreaCard(
    modifier: Modifier = Modifier,
    acceptedWord: String,
    acceptedConfidence: Float?,
    message: String,
    messageLanguage: VoxGestMessageLanguage,
    recognitionRunning: Boolean,
    recognitionStatus: String,
    namePhraseHint: String,
    presentationMode: Boolean,
    showCalibrationPanel: Boolean,
    developerDiagnosticsEnabled: Boolean,
    preferredCameraLens: PreferredCameraLens,
    trackingOverlayEnabled: Boolean,
    showAiLandmarks: Boolean,
    mirrorFrontPreview: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPreferredCameraLensChange: (PreferredCameraLens) -> Unit,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onRecognitionStatus: (String) -> Unit,
    onAcceptedRecognition: (RecognitionResult) -> Unit,
    onSpeak: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val compactHeight = LocalWindowSizeClass.current.heightSizeClass == WindowHeightSizeClass.Compact
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val controllerRef = remember { mutableStateOf<VoxGestCameraRecognitionController?>(null) }
    var recognitionFeedback by remember { mutableStateOf(RecognitionFeedback.idle()) }
    var recognizedToast by remember { mutableStateOf("") }
    var showGrid by remember { mutableStateOf(false) }
    var landmarkVisualizationFrame by remember { mutableStateOf<LandmarkVisualizationFrame?>(null) }
    var cameraRestartKey by remember { mutableStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            onRecognitionStatus("Starting Camera")
        } else {
            onRecognitionStatus("Camera permission denied.")
            onStopRecognition()
        }
    }

    LaunchedEffect(recognitionRunning, hasCameraPermission, previewView, preferredCameraLens, cameraRestartKey) {
        if (!recognitionRunning) {
            controllerRef.value?.stop()
            return@LaunchedEffect
        }
        if (!hasCameraPermission) {
            onRecognitionStatus("Requesting Camera Permission")
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return@LaunchedEffect
        }
        controllerRef.value?.release()
        controllerRef.value = VoxGestCameraRecognitionController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onStatus = onRecognitionStatus,
            initialUseBackCamera = preferredCameraLens == PreferredCameraLens.REAR,
            initialLandmarkVisualizationEnabled = showAiLandmarks,
            onRecognitionFeedback = { recognitionFeedback = it },
            onSkeletonFrame = { landmarkVisualizationFrame = it },
            onAcceptedResult = onAcceptedRecognition
        ).also { it.start(previewView) }
    }

    LaunchedEffect(showAiLandmarks) {
        controllerRef.value?.setLandmarkVisualizationEnabled(showAiLandmarks)
        if (!showAiLandmarks) landmarkVisualizationFrame = null
    }

    LaunchedEffect(recognitionFeedback.eventId) {
        if (recognitionFeedback.detectionStatus == DetectionStatus.RECOGNIZED && recognitionFeedback.eventId > 0L) {
            recognizedToast = "Recognized ${recognitionFeedback.label}"
            delay(1200)
            recognizedToast = ""
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controllerRef.value?.release()
            controllerRef.value = null
        }
    }

    val buttonTextSize = adaptiveSp(12, 14, 16)
    val cameraUnavailable = recognitionRunning && hasCameraPermission && listOf(
        "error",
        "failed",
        "unavailable"
    ).any { marker -> recognitionStatus.contains(marker, ignoreCase = true) }
    val publicTrackingState = when {
        !recognitionRunning || !hasCameraPermission || cameraUnavailable -> copy.holdSignClearly
        recognitionFeedback.detectionStatus == DetectionStatus.DETECTING -> copy.ready
        recognitionFeedback.detectionStatus == DetectionStatus.RECOGNIZED -> copy.ready
        else -> copy.holdSignClearly
    }
    val cameraShape = RoundedCornerShape(if (expanded) 0.dp else 22.dp)

    Card(
        modifier = modifier,
        shape = cameraShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 0.dp else 3.dp),
        border = if (expanded) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = if (expanded) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().padding(if (compactHeight) 10.dp else 18.dp)
            }
        ) {
        AnimatedVisibility(visible = !expanded && namePhraseHint.isNotBlank()) {
            Text(
                namePhraseHint,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Box(
            modifier = when {
                expanded -> Modifier.fillMaxSize()
                compactHeight -> Modifier.fillMaxWidth().height(118.dp)
                else -> Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            }
                .clip(cameraShape)
                .background(Color.Black)
                .then(
                    if (expanded) Modifier
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, cameraShape)
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Recognition status: $publicTrackingState"
                }
        ) {
            // One stable PreviewView host is resized in place. It is never moved into another window.
            AndroidView(
                factory = { previewView },
                update = { view ->
                    // Only fullscreen changes to fill. The embedded component keeps its exact layout/fit.
                    view.scaleType = if (expanded) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
                    // Explicit opt-out of CameraX's native front preview mirror only.
                    view.scaleX = if (!mirrorFrontPreview && preferredCameraLens == PreferredCameraLens.FRONT) -1f else 1f
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Preview mirroring is display-only. ImageAnalysis/model input remains untouched.
            )
            if (showAiLandmarks && recognitionRunning && hasCameraPermission && !cameraUnavailable) {
                SkeletonFeatureOverlay(
                    visualizationFrame = landmarkVisualizationFrame,
                    previewView = previewView,
                    previewMirrored = preferredCameraLens == PreferredCameraLens.FRONT && mirrorFrontPreview,
                    hudTopPadding = if (expanded) 84.dp else 62.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (showGrid && expanded) CameraGridOverlay(Modifier.fillMaxSize())
            if (!recognitionRunning || !hasCameraPermission || cameraUnavailable) {
                CameraStateOverlay(
                    hasCameraPermission = hasCameraPermission,
                    cameraUnavailable = cameraUnavailable,
                    recognitionStatus = recognitionStatus,
                    developerDiagnosticsEnabled = developerDiagnosticsEnabled,
                    showAction = expanded || !hasCameraPermission || cameraUnavailable,
                    compactFullscreen = expanded && compactHeight,
                    onAction = {
                        if (cameraUnavailable) {
                            onRecognitionStatus("Restarting Camera")
                            cameraRestartKey += 1
                        } else {
                            onStartRecognition()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!expanded && trackingOverlayEnabled && !cameraUnavailable) {
                CleanRecognitionGuide(
                    recognitionStatus = publicTrackingState,
                    recognitionRunning = recognitionRunning && hasCameraPermission,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (expanded) {
                FullscreenCameraChrome(
                    publicTrackingState = publicTrackingState,
                    acceptedWord = acceptedWord,
                    acceptedConfidence = acceptedConfidence.takeIf { developerDiagnosticsEnabled },
                    message = message,
                    messageLanguage = messageLanguage,
                    onGridToggle = { showGrid = !showGrid },
                    onSwitchCamera = {
                        onPreferredCameraLensChange(
                            if (preferredCameraLens == PreferredCameraLens.FRONT) PreferredCameraLens.REAR else PreferredCameraLens.FRONT
                        )
                    },
                    onSpeak = onSpeak,
                    onMinimize = { onExpandedChange(false) }
                )
            } else {
                StatusChip(
                    label = publicTrackingState,
                    dotColor = if (publicTrackingState == copy.ready) AccentGreen else Amber,
                    containerColor = if (publicTrackingState == copy.ready) Color(0xFFE8F7EE) else Color(0xFFFFF7ED),
                    contentColor = if (publicTrackingState == copy.ready) Green else Amber,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                )
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CameraOverlayControl(R.drawable.ic_expand, copy.expand) { onExpandedChange(true) }
                }
            }
        }
        AnimatedVisibility(visible = !expanded && developerDiagnosticsEnabled && recognizedToast.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        recognizedToast,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (!expanded && developerDiagnosticsEnabled && BuildConfig.DEBUG && OneHandCalibrationConfig.ENABLE_ONEHAND_CALIBRATION_RECORDING) {
            AnimatedVisibility(visible = showCalibrationPanel) {
                OneHandCalibrationPanel(
                    recognitionRunning = recognitionRunning && hasCameraPermission,
                    onStartCalibration = { label, exportMode, signerId, deviceSessionTag ->
                        controllerRef.value?.startCalibration(label, exportMode, signerId, deviceSessionTag)
                            ?: Toast.makeText(context, "Start recognition first", Toast.LENGTH_SHORT).show()
                    },
                    onCancelCalibration = { controllerRef.value?.cancelCalibration() }
                )
            }
        }
        if (!expanded && presentationMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compactHeight) 8.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            onRecognitionStatus("Requesting Camera Permission")
                        }
                        onStartRecognition()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    VoxIcon(R.drawable.ic_play_arrow, "Start Recognition", MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(copy.start, color = MaterialTheme.colorScheme.onPrimary, fontSize = buttonTextSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        controllerRef.value?.stop()
                        onStopRecognition()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    VoxIcon(R.drawable.ic_cancel, "Stop Recognition", MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(copy.stop, color = MaterialTheme.colorScheme.primary, fontSize = buttonTextSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        onPreferredCameraLensChange(
                            if (preferredCameraLens == PreferredCameraLens.FRONT) {
                                PreferredCameraLens.REAR
                            } else {
                                PreferredCameraLens.FRONT
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    VoxIcon(R.drawable.ic_flip_camera, "Switch Camera", MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(copy.switchCamera, color = MaterialTheme.colorScheme.primary, fontSize = buttonTextSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }
    }
}

private fun defaultFslDeviceSessionTag(): String {
    val device = (Build.MODEL ?: "ANDROID")
        .trim()
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "ANDROID" }
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "${device}_$date"
}

@Composable
private fun OneHandCalibrationPanel(
    recognitionRunning: Boolean,
    onStartCalibration: (String, CalibrationExportMode, String, String) -> Unit,
    onCancelCalibration: () -> Unit
) {
    var exportMode by remember { mutableStateOf(CalibrationExportMode.ASL) }
    var currentExportLabel by remember { mutableStateOf(0) }
    var signerIdInput by remember { mutableStateOf("") }
    val deviceSessionTag = remember { defaultFslDeviceSessionTag() }
    val fslLabels = OneHandCalibrationConfig.FSL_EXPORT_LABELS
    val selectedFslLabel = fslLabels[currentExportLabel.coerceIn(0, fslLabels.lastIndex)]

    fun advanceFslLabel(delta: Int) {
        currentExportLabel = (currentExportLabel + delta + fslLabels.size) % fslLabels.size
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF7FBFB),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Onehand162 calibration", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (recognitionRunning) "Choose a mode and label, then perform one sample." else "Start Recognition before recording.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                OutlinePillButton("Cancel", R.drawable.ic_cancel, Modifier.weight(0.55f), onCancelCalibration)
            }
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(SoftCyan)
                    .border(1.dp, Border, RoundedCornerShape(999.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RecognitionModeSegment(
                    label = "ASL",
                    selected = exportMode == CalibrationExportMode.ASL,
                    modifier = Modifier.weight(1f)
                ) {
                    exportMode = CalibrationExportMode.ASL
                }
                RecognitionModeSegment(
                    label = "FSL",
                    selected = exportMode == CalibrationExportMode.FSL,
                    modifier = Modifier.weight(1f)
                ) {
                    exportMode = CalibrationExportMode.FSL
                }
            }

            if (exportMode == CalibrationExportMode.ASL) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OneHandCalibrationConfig.CALIBRATION_LABELS.forEach { label ->
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clickable(enabled = recognitionRunning) {
                                    onStartCalibration(label, CalibrationExportMode.ASL, "", "")
                                },
                            shape = RoundedCornerShape(999.dp),
                            color = if (recognitionRunning) SoftCyan else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Border)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                Text(label, color = if (recognitionRunning) Primary else TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = signerIdInput,
                    onValueChange = { signerIdInput = it },
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Signer ID") }
                )
                Text(
                    "Session: $deviceSessionTag",
                    color = TextFaint,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinePillButton("Prev", R.drawable.ic_replay, Modifier.weight(1f)) { advanceFslLabel(-1) }
                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1.2f),
                        shape = RoundedCornerShape(14.dp),
                        color = SoftCyan,
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(selectedFslLabel, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinePillButton("Next", R.drawable.ic_play_arrow, Modifier.weight(1f)) { advanceFslLabel(1) }
                }
                FilledPillButton(
                    label = "Record FSL",
                    icon = R.drawable.ic_check_circle,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                ) {
                    onStartCalibration(selectedFslLabel, CalibrationExportMode.FSL, signerIdInput, deviceSessionTag)
                }
            }
            Text(
                if (exportMode == CalibrationExportMode.FSL) {
                    "Exports JSON only to Downloads/VoxGestCalibration/fsl_phrase_v1."
                } else {
                    "Exports JSON only to Downloads/VoxGestCalibration/onehand162_phrase_v1."
                },
                color = TextFaint,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CleanRecognitionGuide(
    recognitionStatus: String,
    recognitionRunning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!recognitionRunning) return
    val guideText = when {
        recognitionStatus.contains("Looking", ignoreCase = true) -> "Place hand in frame"
        recognitionStatus.contains("Hold", ignoreCase = true) -> ""
        recognitionStatus.contains("Signing", ignoreCase = true) -> "Signing..."
        recognitionStatus.contains("Recognizing", ignoreCase = true) -> "Recognizing..."
        recognitionStatus.contains("Accepted", ignoreCase = true) -> "Accepted"
        else -> recognitionStatus
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.padding(bottom = 18.dp)
        ) {
            Text(
                guideText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HandScanOverlay(
    feedback: RecognitionFeedback,
    recognitionRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val status = if (recognitionRunning) feedback.detectionStatus else DetectionStatus.SEARCHING
    val transition = rememberInfiniteTransition(label = "hand-scan")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "guide-pulse"
    )

    Box(modifier = modifier) {
        when (status) {
            DetectionStatus.SEARCHING -> SearchingHandGuide(pulse)
            DetectionStatus.DETECTING -> DetectingHandOverlay(feedback.handLandmarks, AccentGreen)
            DetectionStatus.RECOGNIZED -> DetectingHandOverlay(feedback.handLandmarks, Primary)
        }
    }
}

@Composable
private fun SearchingHandGuide(pulse: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.70f)
                .scale(pulse),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx()))
                    )
                )
            }
            Text(
                "Position hand here",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DetectingHandOverlay(
    landmarks: List<OverlayLandmarkPoint>,
    reticleColor: Color
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cornerLength = size.minDimension * 0.11f
            val inset = size.minDimension * 0.08f
            val strokeWidth = 4.dp.toPx()
            fun drawCorner(start: Offset, horizontal: Float, vertical: Float) {
                drawLine(
                    color = reticleColor,
                    start = start,
                    end = Offset(start.x + horizontal, start.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = reticleColor,
                    start = start,
                    end = Offset(start.x, start.y + vertical),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            drawCorner(Offset(inset, inset), cornerLength, cornerLength)
            drawCorner(Offset(size.width - inset, inset), -cornerLength, cornerLength)
            drawCorner(Offset(inset, size.height - inset), cornerLength, -cornerLength)
            drawCorner(Offset(size.width - inset, size.height - inset), -cornerLength, -cornerLength)

            drawHandSkeleton(landmarks, size)
        }
        ScanningBadge(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 58.dp))
    }
}

@Composable
private fun ScanningBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE8F7EE))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BlinkDot(AccentGreen)
        Text("Scanning...", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandSkeleton(
    landmarks: List<OverlayLandmarkPoint>,
    canvasSize: Size
) {
    if (landmarks.size != 21) return
    val points = landmarks.map { point ->
        Offset(
            x = point.x.coerceIn(0f, 1f) * canvasSize.width,
            y = point.y.coerceIn(0f, 1f) * canvasSize.height
        )
    }
    val lineColor = Color(0xFFB8F060).copy(alpha = 0.60f)
    val dotColor = Color(0xFFB8F060)
    HAND_CONNECTIONS.forEach { connection ->
        drawLine(
            color = lineColor,
            start = points[connection.first],
            end = points[connection.second],
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    points.forEach { point ->
        drawCircle(color = dotColor, radius = 4.dp.toPx(), center = point)
    }
}


@Composable
private fun SkeletonFeatureOverlay(
    visualizationFrame: LandmarkVisualizationFrame?,
    previewView: PreviewView,
    previewMirrored: Boolean,
    hudTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    val frame = visualizationFrame?.frame
    val pose = frame?.poseLandmarks.orEmpty()
    val left = frame?.leftHandLandmarks.orEmpty()
    val right = frame?.rightHandLandmarks.orEmpty()
    val poseColor = Color.White
    val leftHandColor = VoxGestDesignTokens.SunGold
    val rightHandColor = Color(0xFF74D7FF)

    Box(
        modifier = modifier.semantics {
            contentDescription = "Local AI landmark overlay: pose ${if (frame?.hasPose == true) "detected" else "not detected"}, left hand ${if (frame?.hasLeftHand == true) "detected" else "not detected"}, right hand ${if (frame?.hasRightHand == true) "detected" else "not detected"}"
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sourceWidth = frame?.sourceWidth?.takeIf { it > 0 } ?: size.width.toInt()
            val sourceHeight = frame?.sourceHeight?.takeIf { it > 0 } ?: size.height.toInt()
            val analysisMirrored = visualizationFrame?.analysisMirrored ?: false

            fun pointOf(x: Float, y: Float): Offset {
                val metadata = frame?.cameraMetadata
                if (metadata != null) {
                    // CameraX owns sensor crop/rotation and the native preview mirror.
                    val sensorToView = previewView.sensorToViewTransform
                    val inverse = metadata.bufferToSensor
                    if (sensorToView == null || inverse == null) return Offset(-10000f, -10000f)
                    val point = com.voxgest.dryrun.CameraFrameGeometry.uprightToBuffer(
                        x, y, metadata.bufferWidth, metadata.bufferHeight, metadata.rotationDegrees
                    )
                    android.graphics.Matrix().apply { setValues(inverse) }.mapPoints(point)
                    sensorToView.mapPoints(point)
                    // scaleX is an explicit user display toggle outside CameraX's local transform.
                    if (previewView.scaleX < 0f) point[0] = size.width - point[0]
                    return Offset(point[0], point[1])
                }
                val mapped = com.voxgest.dryrun.PreviewOverlayMapper.centerCropPoint(
                    normalizedX = x,
                    normalizedY = y,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    displayWidth = size.width,
                    displayHeight = size.height,
                    analysisMirrored = analysisMirrored,
                    previewMirrored = previewMirrored,
                    fitCenter = previewView.scaleType == PreviewView.ScaleType.FIT_CENTER
                )
                return Offset(mapped.x, mapped.y)
            }

            fun drawPoint(x: Float, y: Float, color: Color, radius: Float) {
                drawCircle(
                    color = color,
                    radius = radius.dp.toPx(),
                    center = pointOf(x, y)
                )
            }

            fun drawSegment(aX: Float, aY: Float, bX: Float, bY: Float, color: Color, stroke: Float) {
                drawLine(
                    color = color,
                    start = pointOf(aX, aY),
                    end = pointOf(bX, bY),
                    strokeWidth = stroke.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            fun drawHand(points: List<com.voxgest.dryrun.LandmarkPoint>, color: Color) {
                if (points.size != 21) return
                HAND_CONNECTIONS.forEach { (first, second) ->
                    val a = points[first]
                    val b = points[second]
                    drawSegment(a.x, a.y, b.x, b.y, color.copy(alpha = 0.78f), 1.6f)
                }
                points.forEach { point -> drawPoint(point.x, point.y, color, 2.8f) }
            }

            if (pose.size == 33) {
                POSE_BODY_CONNECTIONS.forEach { (first, second) ->
                    val a = pose[first]
                    val b = pose[second]
                    drawSegment(a.x, a.y, b.x, b.y, poseColor.copy(alpha = 0.62f), 1.4f)
                }
                // FullSign225 contains all 33 pose points. Facial pose points remain unconnected,
                // so this cannot be mistaken for a facial mesh or separate face detector.
                pose.forEach { point -> drawPoint(point.x, point.y, poseColor.copy(alpha = 0.88f), 2.1f) }
            }

            drawHand(left, leftHandColor)
            drawHand(right, rightHandColor)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = hudTopPadding),
            color = Color.Black.copy(alpha = 0.66f),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "LOCAL AI LANDMARKS",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LandmarkHudItem("Pose", frame?.hasPose == true, poseColor)
                    LandmarkHudItem("L", frame?.hasLeftHand == true, leftHandColor)
                    LandmarkHudItem("R", frame?.hasRightHand == true, rightHandColor)
                }
            }
        }
    }
}

@Composable
private fun LandmarkHudItem(label: String, detected: Boolean, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (detected) color else Color.White.copy(alpha = 0.28f))
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = if (detected) 1f else 0.62f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
private fun CurrentWordCard(
    word: String,
    confidence: Float?,
    showConfidence: Boolean
) {
    val copy = LocalVoxGestCopy.current
    val clean = word.trim()
    val hasWord = isMeaningfulOutput(clean)
    val displayWord = if (hasWord) clean.uppercase(Locale.US) else copy.waitingForSign
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(copy.recognizedSign, Modifier.weight(1f))
            if (hasWord) {
                StatusChip(
                    label = if (copy === VoxGestCopy.Filipino) "Tinanggap" else "Accepted",
                    dotColor = AccentGreen,
                    containerColor = Color(0xFFE8F7EE),
                    contentColor = Green
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayWord,
                color = if (hasWord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = adaptiveSp(22, 28, 34),
                fontWeight = if (hasWord) FontWeight.Bold else FontWeight.SemiBold
            )
        }
        if (showConfidence && confidence != null) {
            Text(
                text = "confidence=${String.format(Locale.US, "%.3f", confidence)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

}

@Composable
private fun CameraStateOverlay(
    hasCameraPermission: Boolean,
    cameraUnavailable: Boolean,
    recognitionStatus: String,
    developerDiagnosticsEnabled: Boolean,
    showAction: Boolean,
    compactFullscreen: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalVoxGestCopy.current
    val headline = when {
        !hasCameraPermission -> if (copy === VoxGestCopy.Filipino) "Kailangan ang pahintulot sa camera" else "Camera permission needed"
        cameraUnavailable -> if (copy === VoxGestCopy.Filipino) "Hindi available ang camera" else "Camera unavailable"
        else -> if (copy === VoxGestCopy.Filipino) "Handa ang camera kapag nagsimula ka" else "Camera ready when you start"
    }
    val actionLabel = when {
        !hasCameraPermission -> if (copy === VoxGestCopy.Filipino) "Payagan ang camera" else "Allow camera"
        cameraUnavailable -> if (copy === VoxGestCopy.Filipino) "Subukan muli" else "Try again"
        else -> copy.start
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        contentAlignment = if (compactFullscreen) Alignment.TopCenter else Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .padding(top = if (compactFullscreen) 12.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VoxIcon(R.drawable.ic_camera_off, "Camera status", MaterialTheme.colorScheme.primary, Modifier.size(46.dp))
            Text(
                headline,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                if (developerDiagnosticsEnabled) recognitionStatus else copy.holdSignClearly,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 7.dp)
            )
            if (showAction) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 18.dp).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FullscreenCameraChrome(
    publicTrackingState: String,
    acceptedWord: String,
    acceptedConfidence: Float?,
    message: String,
    messageLanguage: VoxGestMessageLanguage,
    onGridToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSpeak: () -> Unit,
    onMinimize: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    Box(Modifier.fillMaxSize()) {
            StatusChip(
                label = publicTrackingState,
                dotColor = if (publicTrackingState == copy.ready) AccentGreen else Amber,
                containerColor = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 44.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CameraOverlayControl(R.drawable.ic_flip_camera, copy.switchCamera, onSwitchCamera)
                CameraOverlayControl(R.drawable.ic_grid, "Grid", onGridToggle)
                CameraOverlayControl(R.drawable.ic_minimize, copy.minimize, onMinimize)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),
                color = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(copy.recognizedSign, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        acceptedWord.takeIf(::isMeaningfulOutput)?.uppercase(Locale.US) ?: copy.waitingForSign,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (acceptedConfidence != null) {
                        Text(
                            "${(acceptedConfidence * 100f).toInt()}% confidence",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (isMeaningfulOutput(message)) {
                        VoxGestOfflineMessagePresenter.present(message).visibleLines(messageLanguage).forEach { (language, value) ->
                            Text(
                                "$language  $value",
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier.padding(top = 12.dp).heightIn(min = 44.dp).clickable(onClick = onSpeak),
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VoxIcon(R.drawable.ic_volume_up, copy.speak, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
                                Text(copy.speak, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun CameraOverlayControl(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            VoxIcon(icon, label, Color.White, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CameraGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = 0.20f)
        drawLine(color, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.dp.toPx())
        drawLine(color, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.dp.toPx())
        drawLine(color, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.dp.toPx())
        drawLine(color, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.dp.toPx())
    }
}

@Composable
private fun SignDiagnosticsPanel(
    expanded: Boolean,
    recognitionStatus: String,
    result: RecognitionResult?,
    outputReason: String,
    calibrationVisible: Boolean,
    allowLegacyTools: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleCalibration: () -> Unit
) {
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggleExpanded)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("DEVELOPER DIAGNOSTICS", Modifier.weight(1f))
            Text(if (expanded) "Collapse" else "Expand", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("runtime=$recognitionStatus", color = TextMuted, fontSize = 11.sp)
                Text("output_reason=$outputReason", color = TextMuted, fontSize = 11.sp)
                Text(
                    "top1=${result?.label.orEmpty()} confidence=${result?.confidence ?: 0f} margin=${result?.margin ?: 0f}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    "Raw predictions remain diagnostic; only post-gate allowlisted tokens can update the message.",
                    color = TextFaint,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                if (allowLegacyTools) {
                    OutlinePillButton(
                        label = if (calibrationVisible) "Hide calibration tools" else "Open calibration tools",
                        icon = R.drawable.ic_settings,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onToggleCalibration
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoTokenPanel(
    tokens: List<String>,
    onToken: (String) -> Unit
) {
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("NAME SPELLING ASSIST")
                Text(
                    if (tokens.isEmpty()) "Tap letters to spell a name." else tokens.joinToString(" + "),
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            StatusChip(
                label = "Manual Assist",
                dotColor = Amber,
                containerColor = Color(0xFFFFF7ED),
                contentColor = Amber
            )
        }
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DemoTokenWords.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { token ->
                        DemoTokenButton(
                            label = token,
                            tint = when (token) {
                                "NSAC" -> TextFaint
                                "CLEAR" -> Red
                                else -> Primary
                            },
                            modifier = Modifier.weight(1f),
                            onClick = { onToken(token) }
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoTokenButton(label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val isSpeak = label == "SPEAK"
    val buttonTextSize = adaptiveSp(12, 14, 16)
    Surface(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSpeak) Primary else CardWhite,
        border = BorderStroke(1.dp, if (isSpeak) Primary else Border),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                label,
                color = if (isSpeak) DarkInk else tint,
                fontSize = buttonTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SentenceCard(
    sentence: String,
    messageLanguage: VoxGestMessageLanguage,
    suggestions: List<SentenceSuggestion>,
    selectedSuggestion: SentenceSuggestion?,
    onSuggestionSelected: (SentenceSuggestion) -> Unit,
    onMessageLanguageChange: (VoxGestMessageLanguage) -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val hasSentence = isMeaningfulOutput(sentence)
    val presentation = VoxGestOfflineMessagePresenter.present(sentence)
    val sentenceTextSize = adaptiveSp(16, 20, 24)
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Label(copy.message)
        MessageLanguageSelector(messageLanguage, onMessageLanguageChange)
        if (!hasSentence) {
            Text(
                copy.noMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = sentenceTextSize,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            val visible = presentation.visibleLines(messageLanguage)
            if (visible.isEmpty()) {
                Text(copy.translationUnavailable, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            } else {
                visible.forEach { (language, value) ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(language, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = sentenceTextSize, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        suggestions.firstOrNull()?.let { suggestion ->
            val selected = suggestion == selectedSuggestion
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { onSuggestionSelected(suggestion) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Color(0xFFE8F7EE) else SoftCyan,
                border = BorderStroke(1.dp, if (selected) Primary else Border)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        if (selected) {
                            if (copy === VoxGestCopy.Filipino) "PINILING MUNGKAHI" else "SELECTED SUGGESTION"
                        } else {
                            if (copy === VoxGestCopy.Filipino) "OPSIYONAL NA MUNGKAHI" else "OPTIONAL SUGGESTION"
                        },
                        color = if (selected) Green else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(suggestion.text, color = TextMain, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageLanguageSelector(
    selected: VoxGestMessageLanguage,
    onSelected: (VoxGestMessageLanguage) -> Unit
) {
    val copy = LocalVoxGestCopy.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(
            VoxGestMessageLanguage.ENGLISH to copy.english,
            VoxGestMessageLanguage.BOTH to copy.both,
            VoxGestMessageLanguage.FILIPINO to copy.filipino
        ).forEach { (language, label) ->
            val active = language == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelected(language) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val buttonTextSize = adaptiveSp(12, 14, 16)
    Card(
        modifier = modifier
            .height(74.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            VoxIcon(icon, label, tint, Modifier.size(22.dp))
            Spacer(Modifier.height(7.dp))
            Text(label, color = TextMain, fontSize = buttonTextSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ListenScreen(
    pendingAvatarText: String,
    pendingAvatarRequestId: Int,
    recentEntries: List<ConversationUiEntry>,
    messageLanguage: VoxGestMessageLanguage,
    reducedMotion: Boolean,
    developerDiagnosticsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onSpeechSaved: (String) -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var speechStatus by remember(copy) { mutableStateOf(copy.tapMic) }
    var speechInputLanguage by rememberSaveable { mutableStateOf(SpeechInputLanguage.ENGLISH) }
    var avatarRequestId by rememberSaveable { mutableStateOf(0L) }
    var avatarRequestTranscript by rememberSaveable { mutableStateOf("") }
    val avatarRequest = ListenAvatarRequest(
        id = avatarRequestId,
        transcript = avatarRequestTranscript,
        canonicalLabel = Core3ListenTranscriptResolver.resolve(avatarRequestTranscript)
    )
    val recognizerState = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        speechStatus = if (granted) copy.tapMic else if (copy === VoxGestCopy.Filipino) "Kailangan ang pahintulot sa mikropono." else "Microphone permission is needed."
    }

    fun stopListening() {
        recognizerState.value?.stopListening()
        recognizerState.value?.destroy()
        recognizerState.value = null
        listening = false
    }

    fun requestVerifiedAvatar(text: String) {
        avatarRequestId += 1L
        avatarRequestTranscript = text
    }

    fun startListening() {
        if (!speechAvailable) {
            speechStatus = if (copy === VoxGestCopy.Filipino) "Hindi available ang speech recognition sa device na ito." else "Speech recognition is unavailable on this device."
            return
        }
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recognizerState.value?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizerState.value = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speechStatus = "Listening..."
            }

            override fun onBeginningOfSpeech() {
                speechStatus = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                speechStatus = "Processing speech..."
            }

            override fun onError(error: Int) {
                listening = false
                speechStatus = copy.tapMic
                recognizerState.value?.destroy()
                recognizerState.value = null
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                listening = false
                recognizerState.value?.destroy()
                recognizerState.value = null
                if (isMeaningfulOutput(text)) {
                    transcript = text
                    speechStatus = if (copy === VoxGestCopy.Filipino) "Handa na ang transkrip" else "Transcript ready"
                    onSpeechSaved(text)
                    requestVerifiedAvatar(text)
                } else {
                    speechStatus = if (copy === VoxGestCopy.Filipino) "Walang nakilalang pananalita." else "No speech recognized."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                speechStatus = "Listening..."
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechInputLanguage.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        speechStatus = "Listening..."
        recognizer.startListening(intent)
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizerState.value?.destroy()
        }
    }

    LaunchedEffect(pendingAvatarRequestId) {
        val clean = pendingAvatarText.trim()
        if (pendingAvatarRequestId > 0 && clean.isNotBlank()) {
            transcript = clean
            listening = false
            speechStatus = if (copy === VoxGestCopy.Filipino) "Handa na ang transkrip" else "Transcript ready"
            requestVerifiedAvatar(clean)
        }
    }

    fun playTranscript() {
        if (isMeaningfulOutput(transcript)) {
            requestVerifiedAvatar(transcript)
        }
    }

    ScreenScroll {
        ListenTopBar(onOpenSettings)
        SpeechTranscriptCard(
            transcript = transcript,
            speechInputLanguage = speechInputLanguage,
            messageLanguage = messageLanguage,
            onTranscriptChange = { transcript = it },
            onSpeechInputLanguageChange = { speechInputLanguage = it },
            onCopy = {
                if (isMeaningfulOutput(transcript)) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VoxGest transcript", transcript))
                    Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                }
            }
        )
        ListenMicrophoneInteraction(
            listening = listening,
            speechStatus = speechStatus,
            reducedMotion = reducedMotion,
            onMicTap = { if (listening) stopListening() else startListening() }
        )
        ListenCore3AvatarCard(
            request = avatarRequest,
            reducedMotion = reducedMotion,
            onPlay = { playTranscript() },
            developerDiagnosticsEnabled = developerDiagnosticsEnabled,
            onDebugSign = { label -> requestVerifiedAvatar(label) }
        )
        RecentConversationPreview(recentEntries)
        if (developerDiagnosticsEnabled) PresentationBuildFooter()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ListenTopBar(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(128.dp)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(
                        R.drawable.ic_hand_gesture,
                        "VoxGest",
                        MaterialTheme.colorScheme.primary,
                        Modifier.size(23.dp)
                    )
                }
            }
            Text(
                "VoxGest",
                modifier = Modifier.padding(start = 7.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "LISTEN",
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(44.dp)
                .clickable(onClick = onOpenSettings),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(
                    R.drawable.ic_settings,
                    "Listen settings",
                    MaterialTheme.colorScheme.primary,
                    Modifier.size(22.dp)
                )
            }
        }
    }
}

private data class ListenAvatarRequest(
    val id: Long = 0L,
    val transcript: String = "",
    val canonicalLabel: String? = null
)

@Composable
private fun SpeechTranscriptCard(
    transcript: String,
    speechInputLanguage: SpeechInputLanguage,
    messageLanguage: VoxGestMessageLanguage,
    onTranscriptChange: (String) -> Unit,
    onSpeechInputLanguageChange: (SpeechInputLanguage) -> Unit,
    onCopy: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val hasTranscript = isMeaningfulOutput(transcript)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(copy.transcript, Modifier.weight(1f))
            if (hasTranscript) {
                Surface(
                    modifier = Modifier.size(44.dp).clickable(onClick = onCopy),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        VoxIcon(R.drawable.ic_copy, "Copy transcript", MaterialTheme.colorScheme.primary, Modifier.size(17.dp))
                    }
                }
            }
            SpeechLanguageChoice(copy.english, speechInputLanguage == SpeechInputLanguage.ENGLISH, Modifier.width(76.dp)) {
                onSpeechInputLanguageChange(SpeechInputLanguage.ENGLISH)
            }
            Spacer(Modifier.width(6.dp))
            SpeechLanguageChoice(copy.filipino, speechInputLanguage == SpeechInputLanguage.FILIPINO, Modifier.width(76.dp)) {
                onSpeechInputLanguageChange(SpeechInputLanguage.FILIPINO)
            }
        }
        if (hasTranscript) {
            OutlinedTextField(
                value = transcript,
                onValueChange = onTranscriptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp)
            )
            if (speechInputLanguage == SpeechInputLanguage.ENGLISH && messageLanguage != VoxGestMessageLanguage.ENGLISH) {
                val translated = VoxGestOfflineMessagePresenter.present(transcript).filipino
                Text(
                    translated ?: copy.translationUnavailable,
                    color = if (translated != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        } else {
            Text(
                if (copy === VoxGestCopy.Filipino) "Lalabas dito ang iyong sasabihin." else "Your speech will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
            )
        }
        }
    }
}

@Composable
private fun ListenMicrophoneInteraction(
    listening: Boolean,
    speechStatus: String,
    reducedMotion: Boolean,
    onMicTap: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val processing = speechStatus.contains("processing", ignoreCase = true)
    val readyStatus = speechStatus == copy.tapMic ||
        speechStatus.contains("transcript ready", ignoreCase = true) ||
        speechStatus.contains("handa na ang transkrip", ignoreCase = true)
    val stateLabel = when {
        processing -> if (copy === VoxGestCopy.Filipino) "Pinoproseso..." else "Processing..."
        listening -> "Listening..."
        readyStatus -> if (copy === VoxGestCopy.Filipino) "Handa" else "Ready"
        else -> speechStatus
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Waveform(Modifier.weight(1f), listening, reducedMotion)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onMicTap),
                contentAlignment = Alignment.Center
            ) {
                if (listening) {
                    MicPulse(reducedMotion)
                }
                VoxIcon(R.drawable.ic_mic, "Microphone: $stateLabel", MaterialTheme.colorScheme.onPrimary, Modifier.size(32.dp))
            }
            Waveform(Modifier.weight(1f), listening, reducedMotion)
        }
        Text(
            stateLabel,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (readyStatus) copy.tapMic else copy.speakClearly,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpeechLanguageChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentConversationPreview(entries: List<ConversationUiEntry>) {
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("RECENT CONVERSATION", Modifier.weight(1f))
            VoxIcon(R.drawable.ic_chat, "Conversation", MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
        }
        if (entries.isEmpty()) {
            Text(
                "No exchanges in this session yet.",
                color = TextFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                VoxIcon(
                                    if (entry.participant == ConversationParticipant.FSL_USER) R.drawable.ic_hand_gesture else R.drawable.ic_waveform,
                                    entry.participant.name,
                                    MaterialTheme.colorScheme.primary,
                                    Modifier.size(17.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                if (entry.participant == ConversationParticipant.FSL_USER) "FSL user" else "Hearing user",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                entry.text,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(formatConversationTime(entry.timestampMillis), color = TextFaint, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenCore3AvatarCard(
    request: ListenAvatarRequest,
    reducedMotion: Boolean,
    onPlay: () -> Unit,
    developerDiagnosticsEnabled: Boolean,
    onDebugSign: (String) -> Unit
) {
    val context = LocalContext.current
    val compactHeight = LocalWindowSizeClass.current.heightSizeClass == WindowHeightSizeClass.Compact
    val viewportColor = Color(0xFFCFEFF3)
    val catalogResult = remember(context) { runCatching { Core3AvatarAssets.loadCatalog(context) } }
    val catalog = catalogResult.getOrNull()
    val host = remember(context) { Core3FilamentHostView(context) }
    var playerState by remember { mutableStateOf(Core3AvatarState()) }
    var routeMessage by remember { mutableStateOf("Ready when you are") }
    val controller = remember(catalog, host) {
        catalog?.let {
            Core3AvatarRuntimeController(
                catalog = it,
                runtime = host,
                enabledLabels = setOf("HELLO", "MILK", "RICE"),
                onStateChanged = { next -> playerState = next }
            )
        }
    }

    LaunchedEffect(controller, request.id, reducedMotion) {
        val runtimeController = controller ?: return@LaunchedEffect
        if (request.id == 0L) {
            routeMessage = "Ready when you are"
            runtimeController.loadSign("HELLO", autoPlay = false)
            return@LaunchedEffect
        }
        val label = request.canonicalLabel
        if (label == null) {
            routeMessage = "Sign animation not available yet."
            if (
                playerState.status == Core3AvatarStatus.READY ||
                playerState.status == Core3AvatarStatus.PLAYING
            ) {
                runtimeController.resetNeutral()
            }
        } else {
            routeMessage = if (reducedMotion) {
                "Sign is ready to play"
            } else {
                "Converting speech to sign language"
            }
            runtimeController.loadSign(label, autoPlay = !reducedMotion)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller?.unload() }
    }

    val statusLabel = when {
        catalog == null || playerState.status == Core3AvatarStatus.ERROR -> "Unavailable"
        playerState.status == Core3AvatarStatus.LOADING -> "Analyzing..."
        playerState.status == Core3AvatarStatus.PLAYING -> "Signing..."
        request.id > 0L && request.canonicalLabel == null -> "Not available"
        else -> "Ready"
    }
    val detail = when {
        catalog == null || playerState.status == Core3AvatarStatus.ERROR -> "Avatar unavailable"
        request.id > 0L && request.canonicalLabel == null -> "Sign animation not available yet."
        playerState.status == Core3AvatarStatus.LOADING -> "Preparing sign animation..."
        playerState.status == Core3AvatarStatus.PLAYING -> "Converting speech to sign language"
        else -> routeMessage
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Label("FSL AVATAR", Modifier.weight(1f))
                    StatusChip(
                        label = statusLabel,
                        dotColor = if (playerState.status == Core3AvatarStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .aspectRatio(if (compactHeight) 2.35f else 1.44f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(viewportColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (catalog != null) {
                        AndroidView(
                            factory = {
                                host.apply {
                                    setViewportColor(viewportColor.toArgb())
                                    contentDescription = "Listen FSL Avatar viewport"
                                }
                            },
                            update = {
                                val requestedSign = playerState.currentSign.takeIf { request.id > 0L }
                                it.contentDescription = if (requestedSign == null) {
                                    "FSL Avatar: $statusLabel"
                                } else {
                                    "FSL Avatar signing $requestedSign: $statusLabel"
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    when {
                        catalog == null || playerState.status == Core3AvatarStatus.ERROR -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Avatar unavailable",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (developerDiagnosticsEnabled) {
                                            catalogResult.exceptionOrNull()?.message
                                                ?: playerState.error
                                                ?: "Renderer could not start"
                                        } else {
                                            "Please leave and reopen Listen."
                                        },
                                        modifier = Modifier.padding(top = 5.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Transcript and microphone remain available.",
                                        modifier = Modifier.padding(top = 7.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        playerState.status == Core3AvatarStatus.UNLOADED ||
                            playerState.status == Core3AvatarStatus.LOADING -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                    Text(
                                        "Loading Avatar…",
                                        modifier = Modifier.padding(top = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    detail,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp),
                    color = if (request.id > 0L && request.canonicalLabel == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinePillButton(
                "Replay",
                R.drawable.ic_replay,
                Modifier.weight(0.92f)
            ) { controller?.replay() }
            FilledPillButton(
                "Play Signs",
                R.drawable.ic_play_arrow,
                Modifier.weight(1.08f),
                onPlay
            )
        }
    }

    if (developerDiagnosticsEnabled && SHOW_DEBUG_TOOLS && BuildConfig.DEBUG) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("HELLO", "MILK", "RICE").forEach { label ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable { onDebugSign(label) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarCard(
    avatarController: AvatarController,
    avatarState: AvatarPlaybackState,
    onReplay: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    developerDiagnosticsEnabled: Boolean,
    onDebugWord: (String) -> Unit
) {
    val isAnalyzing = avatarState.status == AvatarStatus.ANALYZING
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(1f)
    ) {
        Card(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Label("AVATAR", Modifier.weight(1f))
                    StatusChip(
                        label = when {
                            isAnalyzing -> "Analyzing..."
                            avatarState.isPlaying -> "Signing..."
                            avatarState.isListening -> "Listening..."
                            else -> "Ready"
                        },
                        dotColor = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                AndroidView(
                    factory = { viewContext ->
                        if (AvatarFeatureFlags.ENABLE_3D_AVATAR) {
                            SceneAvatarHostView(viewContext).also {
                                avatarController.attach(it.canvasAvatarView)
                            }
                        } else {
                            AvatarView(viewContext).also { avatarController.attach(it) }
                        }
                    },
                    update = { it.contentDescription = "Avatar signing: ${avatarState.currentWord.ifBlank { avatarState.label }}" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                Surface(color = CardWhite.copy(alpha = 0.92f), shadowElevation = 0.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            avatarState.detail.ifBlank { "Converting speech to sign language" },
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinePillButton("Replay", R.drawable.ic_replay, Modifier.weight(1f), onReplay)
                            FilledPillButton("Play Signs", R.drawable.ic_play_arrow, Modifier.weight(1f), onPlay)
                        }
                    }
                }
            }
        }
        if (isAnalyzing) {
            AnalyzingAvatarOverlay(Modifier.matchParentSize())
        }
    }
    if (developerDiagnosticsEnabled && SHOW_DEBUG_TOOLS && BuildConfig.DEBUG) {
        Text(
            "Debug avatar tests",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("HELLO", "THANKYOU", "WATER", "EAT", "WHAT", "YOUR", "NAME", "MY", "YOU", "OKAY", "STUDENT", "WHERE", "LIVE", "STOP").forEach { word ->
                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable {
                            if (word == "STOP") onStop() else onDebugWord(word)
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = SoftCyan,
                    border = BorderStroke(1.dp, Border)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            when (word) {
                                "THANKYOU" -> "THANK"
                                "STOP" -> "Stop"
                                else -> word
                            },
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingAvatarOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar-analyzing")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "avatar-ring"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x140E3F43))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 2.dp.toPx()
            drawArc(
                color = PrimaryLight,
                startAngle = rotation,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Primary.copy(alpha = 0.50f),
                startAngle = rotation + 180f,
                sweepAngle = 64f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun PhrasesScreen(selectedPhrase: String, onPhrase: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTopBar("PHRASES", R.drawable.ic_search)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            QuickPhraseHero()
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SelectedPhraseCard(selectedPhrase)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            CategoryChips()
        }
        items(QuickPhrases) { phrase ->
            PhraseCard(
                phrase = phrase,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPhrase(phrase.label) }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PresentationBuildFooter()
        }
    }
}

@Composable
private fun SelectedPhraseCard(selectedPhrase: String) {
    if (!isMeaningfulOutput(selectedPhrase)) return
    VoxGestCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Label("SELECTED PHRASE")
        Text(
            selectedPhrase,
            color = Primary,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun QuickPhraseHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 6f)
            .heightIn(min = 124.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryLight)))
            .padding(20.dp)
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f)
        ) {
            Text("Quick Phrases", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tap a phrase to show it in sign or speak it out.",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                repeat(3) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.40f))) }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(64.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.23f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(R.drawable.ic_bolt, "Quick phrases", Color.White, Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun CategoryChips() {
    Row(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryChip("Emergency", R.drawable.ic_warning, Amber, Color(0x26F59E0B), true)
        CategoryChip("Medical", R.drawable.ic_medical, PrimaryLight, Color(0x2618686D), false)
        CategoryChip("Daily", R.drawable.ic_wb_sunny, TextMuted, CardWhite, false)
        CategoryChip("Conversation", R.drawable.ic_chat, TextMuted, CardWhite, false)
    }
}

@Composable
private fun CategoryChip(label: String, @DrawableRes icon: Int, tint: Color, bg: Color, active: Boolean) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, if (active) tint.copy(alpha = 0.35f) else Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoxIcon(icon, label, tint, Modifier.size(16.dp))
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PhraseCard(phrase: PhraseUi, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(86.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = phrase.color.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(phrase.icon, phrase.label, phrase.color, Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(phrase.label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
private fun HistoryScreen(
    entries: List<HistoryUiEntry>,
    onClearAll: () -> Unit,
    onReplay: (HistoryUiEntry) -> Unit
) {
    ScreenScroll {
        ScreenTopBar("HISTORY", R.drawable.ic_filter_list, R.drawable.ic_search)
        if (entries.isEmpty()) {
            EmptyHistoryCard()
        } else {
            HistorySection("Today", entries.filter { it.section == "Today" }, onReplay)
            HistorySection("Yesterday", entries.filter { it.section == "Yesterday" }, onReplay)
        }
        Button(
            onClick = onClearAll,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            VoxIcon(R.drawable.ic_delete_outline, "Clear history", DarkInk, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Clear All History", color = DarkInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        PresentationBuildFooter()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GuideScreen(reducedMotion: Boolean, onOpenSettings: () -> Unit) {
    val copy = LocalVoxGestCopy.current
    val isFilipino = copy === VoxGestCopy.Filipino
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FslGuideCategory?>(null) }
    var selectedEntry by remember { mutableStateOf<FslGuideEntry?>(null) }
    var avatarLabel by remember { mutableStateOf<String?>(null) }
    var datasetInfoExpanded by remember { mutableStateOf(false) }
    var learningInfoExpanded by remember { mutableStateOf(false) }
    val catalogResult = remember(context) { runCatching { Fsl105GuideCatalog.load(context) } }
    val completeCatalog = catalogResult.getOrElse { emptyList() }
    val entries = completeCatalog.filter { entry ->
        (selectedCategory == null || entry.category == selectedCategory) &&
            (query.isBlank() || entry.label.contains(query.trim(), ignoreCase = true) ||
                entry.filipinoTranslation?.contains(query.trim(), ignoreCase = true) == true)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 166.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTopBar(copy.supportedGuide, R.drawable.ic_settings, onTrailingClick = onOpenSettings)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            GuideHero(
                totalCount = completeCatalog.size,
                visibleCount = entries.size,
                isFilipino = isFilipino
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DatasetInformationCard(
                expanded = datasetInfoExpanded,
                isFilipino = isFilipino,
                onToggle = { datasetInfoExpanded = !datasetInfoExpanded }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            VoxGestLearningCard(
                expanded = learningInfoExpanded,
                isFilipino = isFilipino,
                onToggle = { learningInfoExpanded = !learningInfoExpanded }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(copy.searchSigns) },
                leadingIcon = { VoxIcon(R.drawable.ic_search, null, MaterialTheme.colorScheme.primary, Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuideCategoryChip(
                    label = copy.all,
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
                FslGuideCategory.entries.forEach { category ->
                    GuideCategoryChip(
                        label = guideCategoryLabel(category, isFilipino),
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }
        if (catalogResult.isFailure) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GuideEmptyState(
                    title = if (isFilipino) "Hindi mabasa ang FSL-105 catalogue" else "FSL-105 catalogue unavailable",
                    detail = if (isFilipino) {
                        "Hindi nagbukas ang canonical runtime label asset. Walang pamalit na listahan ang ginawa."
                    } else {
                        "The canonical runtime label asset could not be opened. No substitute list was generated."
                    }
                )
            }
        } else if (entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GuideEmptyState(
                    title = if (isFilipino) "Walang tugmang sign" else "No matching sign",
                    detail = if (isFilipino) {
                        "Subukan ang ibang salita o piliin ang Lahat."
                    } else {
                        "Try another search term or select All."
                    }
                )
            }
        } else {
            items(entries, key = { entry -> entry.label }) { entry ->
                GuideInventoryCard(entry = entry, isFilipino = isFilipino) {
                    selectedEntry = entry
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        val avatarAvailable = Core3AvatarGuideAvailability.isAvailable(entry.label)
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(entry.label, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        entry.filipinoTranslation ?: if (isFilipino) {
                            "Wala pang beripikadong salin sa Filipino"
                        } else "Verified Filipino translation unavailable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${if (isFilipino) "Kategorya" else "Category"}: ${guideCategoryLabel(entry.category, isFilipino)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GuideDetailStatus(entry.datasetStatus, MaterialTheme.colorScheme.primary)
                    GuideDetailStatus(entry.liveValidationStatus, Amber)
                    GuideDetailStatus(
                        if (avatarAvailable) "AVATAR AVAILABLE" else "AVATAR TUTORIAL — IN CALIBRATION",
                        if (avatarAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (avatarAvailable && isFilipino) {
                            "May beripikadong CORE3 animation para sa sign na ito. Technical animation verification ito, hindi expert FSL linguistic validation."
                        } else if (avatarAvailable) {
                            "A verified CORE3 animation is packaged for this sign. This is technical animation verification, not expert FSL linguistic validation."
                        } else if (isFilipino) {
                            "Wala pang tutorial media sa build na ito. Ang label ay mula sa selected runtime vocabulary at hindi pa patunay ng live Samsung accuracy."
                        } else {
                            "Tutorial media is not packaged in this build. This selected-runtime label is not evidence of live Samsung accuracy."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        "${if (isFilipino) "Pinagmulan" else "Source"}: ${entry.source}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .then(
                                if (avatarAvailable) {
                                    Modifier.clickable {
                                        selectedEntry = null
                                        avatarLabel = entry.label
                                    }
                                } else Modifier
                            ),
                        color = if (avatarAvailable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VoxIcon(
                                R.drawable.ic_play_arrow,
                                null,
                                if (avatarAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                Modifier.size(18.dp)
                            )
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    if (avatarAvailable) "Watch Sign" else "Avatar Tutorial",
                                    color = if (avatarAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (avatarAvailable) {
                                        if (isFilipino) "Available ang beripikadong 3D Avatar" else "Avatar Available"
                                    } else {
                                        if (isFilipino) "Nasa calibration pa" else "In calibration"
                                    },
                                    color = if (avatarAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedEntry = null }) { Text(copy.done) } }
        )
    }

    avatarLabel?.let { label ->
        Core3AvatarPlayerDialog(
            canonicalLabel = label,
            reducedMotion = reducedMotion,
            onDismiss = { avatarLabel = null }
        )
    }
}

@Composable
private fun GuideHero(totalCount: Int, visibleCount: Int, isFilipino: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxWidth(0.74f)) {
            Text(
                "FSL-105 Knowledge Base",
                color = VoxGestDesignTokens.DeepCharcoal,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isFilipino) {
                    "105 dokumentadong Filipino Sign Language signs at phrases sa kasalukuyang classifier vocabulary ng VoxGest."
                } else {
                    "105 documented Filipino Sign Language signs and phrases forming VoxGest's current classifier vocabulary."
                },
                color = VoxGestDesignTokens.DeepCharcoal.copy(alpha = 0.84f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            Text(
                "$visibleCount / $totalCount ${if (isFilipino) "signs na nakikita" else "signs shown"}",
                color = VoxGestDesignTokens.DeepCharcoal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).size(62.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.38f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(R.drawable.ic_book, null, VoxGestDesignTokens.DeepCharcoal, Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun DatasetInformationCard(expanded: Boolean, isFilipino: Boolean, onToggle: () -> Unit) {
    VoxGestExpandableInfoCard(
        title = if (isFilipino) "Tungkol sa FSL-105 Dataset" else "About the FSL-105 Dataset",
        summary = if (isFilipino) "Beripikadong source, attribution, bersyon, at lisensya" else "Verified source, attribution, version, and license",
        expanded = expanded,
        onToggle = onToggle
    ) {
        Text(
            Fsl105DatasetFacts.TITLE,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Text(
            if (isFilipino) {
                "Orihinal na dataset: ${Fsl105DatasetFacts.CONTRIBUTOR} • ${Fsl105DatasetFacts.INSTITUTION} • ${Fsl105DatasetFacts.SOURCE} • inilathala noong 7 Marso 2023."
            } else {
                "Original dataset: ${Fsl105DatasetFacts.CONTRIBUTOR} • ${Fsl105DatasetFacts.INSTITUTION} • ${Fsl105DatasetFacts.SOURCE} • published ${Fsl105DatasetFacts.PUBLISHED}."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "DOI ${Fsl105DatasetFacts.DOI} • ${Fsl105DatasetFacts.VERSION} • ${Fsl105DatasetFacts.LICENSE} • ${Fsl105DatasetFacts.VIDEO_COUNT} four-second clips • ${Fsl105DatasetFacts.CLASS_COUNT} classes",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            if (isFilipino) {
                "Ang VoxGest team ang nag-preprocess, nag-validate ng integrity, nagsanay, at nag-deploy ng sarili nitong on-device model. Hindi kami ang lumikha ng orihinal na FSL-105 dataset."
            } else {
                "The VoxGest team preprocesses, validates integrity, trains, and deploys its own on-device model. The team did not create the original FSL-105 dataset."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun VoxGestLearningCard(expanded: Boolean, isFilipino: Boolean, onToggle: () -> Unit) {
    VoxGestExpandableInfoCard(
        title = if (isFilipino) "Paano Natuto ang VoxGest ng FSL-105" else "How VoxGest Learned FSL-105",
        summary = if (isFilipino) "Mula reference video hanggang ligtas na on-device output" else "From reference video to safe on-device output",
        expanded = expanded,
        onToggle = onToggle
    ) {
        Text(
            "FSL reference videos  →  MediaPipe  →  pose + both hands  →  temporal sequence  →  local model training  →  TFLite on device",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("33 pose × XYZ = 99", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("21 left hand × XYZ = 63", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("21 right hand × XYZ = 63", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("99 + 63 + 63 = 225 features per frame", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Text(
            if (isFilipino) {
                "Sinusuri ang magkakasunod na frame para sa galaw, saka dadaan ang resulta sa acceptance/rejection bago lumabas bilang English/Filipino na komunikasyon. Lokal sa device ang camera inference; walang raw top-1 prediction na direktang ipinapakita."
            } else {
                "Frames are evaluated as a motion sequence, then acceptance/rejection decides whether a result is safe to present as English/Filipino communication. Camera inference stays on device; a raw top-1 prediction is never shown directly."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun GuideCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.heightIn(min = 42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Box(Modifier.padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuideInventoryCard(entry: FslGuideEntry, isFilipino: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 154.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        VoxIcon(R.drawable.ic_hand_gesture, null, MaterialTheme.colorScheme.primary, Modifier.size(20.dp))
                    }
                }
                Text(
                    entry.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
            Text(
                guideCategoryLabel(entry.category, isFilipino),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                if (isFilipino) "BOKABULARYO NG DATASET" else entry.datasetStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 5.dp)
            )
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Amber))
                Text(
                    if (isFilipino) "HINDI PA LIVE VALIDATED" else entry.liveValidationStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideEmptyState(title: String, detail: String) {
    VoxGestCapstoneCard(Modifier.fillMaxWidth()) {
        VoxIcon(R.drawable.ic_search, null, MaterialTheme.colorScheme.primary, Modifier.size(28.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun GuideDetailStatus(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
    }
}

private fun guideCategoryLabel(category: FslGuideCategory, isFilipino: Boolean): String {
    if (!isFilipino) return category.displayName
    return when (category) {
        FslGuideCategory.CALENDAR -> "Kalendaryo at oras"
        FslGuideCategory.COLORS -> "Mga kulay"
        FslGuideCategory.FAMILY_PEOPLE -> "Pamilya at tao"
        FslGuideCategory.FOOD_DRINK -> "Pagkain at inumin"
        FslGuideCategory.NUMBERS -> "Mga numero"
        FslGuideCategory.CONVERSATION -> "Pag-uusap"
        FslGuideCategory.OTHER -> "Araw-araw na signs"
    }
}

@Composable
private fun ConversationScreen(
    entries: List<ConversationUiEntry>,
    messageLanguage: VoxGestMessageLanguage,
    onClearAll: () -> Unit,
    onNewExchange: () -> Unit,
    onOpenSettings: () -> Unit,
    onReplay: (ConversationUiEntry) -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScroll {
        ScreenTopBar(copy.conversation, R.drawable.ic_settings, onTrailingClick = onOpenSettings)
        Text(
            copy.conversation,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp)
        )
        Text(
            if (copy === VoxGestCopy.Filipino) "Kasaysayan ng bilingual na usapan" else "Real-time bilingual chat history",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp)
        )
        if (entries.isEmpty()) {
            VoxGestCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Label(copy.currentExchange)
                Text(
                    copy.noMessages,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    copy.noMessagesHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            Text(
                copy.currentExchange,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entries.forEach { entry ->
                    ConversationBubble(
                        entry = entry,
                        messageLanguage = messageLanguage,
                        onReplay = { onReplay(entry) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("VoxGest conversation", entry.text))
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VoxGestCapstoneAction(
                label = if (copy === VoxGestCopy.Filipino) "Burahin ang history" else "Clear history",
                icon = R.drawable.ic_delete_outline,
                filled = false,
                modifier = Modifier.weight(1f),
                enabled = entries.isNotEmpty(),
                onClick = { confirmClear = true }
            )
            VoxGestCapstoneAction(
                label = copy.newExchange,
                icon = R.drawable.ic_plus,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onNewExchange
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(if (copy === VoxGestCopy.Filipino) "Burahin ang usapang ito?" else "Clear this conversation?") },
            text = {
                Text(
                    if (copy === VoxGestCopy.Filipino) {
                        "Ang mga mensaheng nakikita lamang sa kasalukuyang session ang mabubura. Hindi magbabago ang models, labels, recognition history, o settings."
                    } else {
                        "This removes only the messages shown in the current app session. Models, labels, recognition history, and settings are not changed."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClear = false
                }) { Text(copy.clear) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(copy.cancel) }
            }
        )
    }
}

@Composable
private fun ConversationBubble(
    entry: ConversationUiEntry,
    messageLanguage: VoxGestMessageLanguage,
    onReplay: () -> Unit,
    onCopy: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val isFslUser = entry.participant == ConversationParticipant.FSL_USER
    val presentation = VoxGestOfflineMessagePresenter.present(entry.text)
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .align(if (isFslUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isFslUser) 20.dp else 5.dp,
                bottomEnd = if (isFslUser) 5.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = if (isFslUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (isFslUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isFslUser) copy.fslUser else copy.hearingUser,
                        color = if (isFslUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatConversationTime(entry.timestampMillis),
                        color = if (isFslUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                presentation.visibleLines(messageLanguage).ifEmpty { listOf("" to entry.text) }.forEach { (language, value) ->
                    Column(Modifier.padding(top = 6.dp)) {
                        if (language.isNotBlank()) {
                            Text(language, color = if (isFslUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            value,
                            color = if (isFslUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (entry.presentationState) {
                            ConversationPresentationState.SPOKEN_ALOUD -> copy.spokenAloud
                            ConversationPresentationState.SHOWN_AS_SIGNS -> copy.shownAsSigns
                        },
                        color = if (isFslUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isFslUser) copy.speak else if (copy === VoxGestCopy.Filipino) "Ipakita sa FSL" else "Show in FSL",
                        color = if (isFslUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onReplay).padding(vertical = 8.dp)
                    )
                    VoxIcon(
                        R.drawable.ic_copy,
                        "Copy message",
                        if (isFslUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        Modifier.size(18.dp).clickable(onClick = onCopy)
                    )
                }
            }
        }
    }
}

private fun formatConversationTime(timestampMillis: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date(timestampMillis))
}

@Composable
private fun EmptyHistoryCard() {
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Label("TODAY")
        Text(
            "No history yet.",
            color = TextFaint,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun HistorySection(title: String, entries: List<HistoryUiEntry>, onReplay: (HistoryUiEntry) -> Unit) {
    if (entries.isEmpty()) return
    Text(
        title,
        color = Primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp)
    )
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { HistoryItem(it, onReplay) }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun HistoryItem(entry: HistoryUiEntry, onReplay: (HistoryUiEntry) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = entry.iconColor, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(entry.icon, entry.type, Color.White, Modifier.size(22.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.type, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(entry.time, color = TextFaint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    entry.text,
                    color = TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    VoxIcon(if (entry.status == "Spoken") R.drawable.ic_volume_up else R.drawable.ic_play_arrow, entry.status, TextFaint, Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(entry.status, color = TextFaint, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onReplay(entry) },
                contentAlignment = Alignment.Center
            ) {
                VoxIcon(R.drawable.ic_play_arrow, "Replay", Primary, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            VoxIcon(R.drawable.ic_more_vert, "More", TextFaint, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VoxGestCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    VoxGestCapstoneCard(modifier = modifier, content = content)
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun PresentationBuildFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VoxGest Presentation Build", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Recognition: Experimental / Calibration", color = TextFaint, fontSize = 10.sp)
        Text("Avatar: CORE3 Filament / Verified Actions Only", color = TextFaint, fontSize = 10.sp)
    }
}

@Composable
private fun StatusChip(
    label: String,
    dotColor: Color,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    VoxGestStatusBadge(label, dotColor, containerColor, contentColor, modifier)
}

@Composable
private fun OutlinePillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    VoxGestCapstoneAction(label, icon, filled = false, modifier = modifier, onClick = onClick)
}

@Composable
private fun FilledPillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    VoxGestCapstoneAction(label, icon, filled = true, modifier = modifier, onClick = onClick)
}

@Composable
private fun Waveform(modifier: Modifier, active: Boolean, reducedMotion: Boolean = false) {
    if (!active || reducedMotion) {
        WaveformBars(modifier = modifier, pulse = 0f, active = false)
        return
    }
    val transition = rememberInfiniteTransition(label = "wave")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    WaveformBars(modifier = modifier, pulse = pulse, active = true)
}

@Composable
private fun WaveformBars(modifier: Modifier, pulse: Float, active: Boolean) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val heights = listOf(8, 14, 24, 34, 22, 13, 8)
        heights.forEachIndexed { index, height ->
            val scale = if (active) 0.55f + ((pulse + index * 0.17f) % 1f) * 0.55f else 0.55f
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(3.dp)
                    .height((height * scale).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index in 2..4) Primary.copy(alpha = 0.55f) else Color(0xFFD1E6E8))
            )
        }
    }
}

@Composable
private fun MicPulse(reducedMotion: Boolean = false) {
    if (reducedMotion) return
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White.copy(alpha = 0.18f), radius = size.minDimension * 0.5f * scale)
    }
}

@Composable
private fun BlinkDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun BottomNavBar(selectedTab: VoxTab, onTabSelected: (VoxTab) -> Unit) {
    val copy = LocalVoxGestCopy.current
    val items = VoxTab.values().map { tab ->
        val label = when (tab) {
            VoxTab.Sign -> copy.sign
            VoxTab.Conversation -> copy.conversation
            VoxTab.Listen -> copy.listen
            VoxTab.Guide -> copy.guide
        }
        VoxGestBottomNavItem(tab.name, label, tab.icon)
    }
    VoxGestCapstoneBottomNav(
        items = items,
        selectedKey = selectedTab.name,
        onSelected = { key ->
            VoxTab.values().firstOrNull { it.name == key }?.let(onTabSelected)
        }
    )
}

@Composable
private fun ScreenScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun VoxIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = ImageVector.vectorResource(id = icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

