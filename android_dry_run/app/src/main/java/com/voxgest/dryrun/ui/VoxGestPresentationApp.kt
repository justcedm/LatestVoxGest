package com.voxgest.dryrun.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.voxgest.app.avatar.AvatarController
import com.voxgest.app.avatar.AvatarPlaybackState
import com.voxgest.app.avatar.AvatarView
import com.voxgest.dryrun.BuildConfig
import com.voxgest.dryrun.DetectionStatus
import com.voxgest.dryrun.OverlayLandmarkPoint
import com.voxgest.dryrun.R
import com.voxgest.dryrun.RecognitionFeedback
import com.voxgest.dryrun.RecognitionResult
import com.voxgest.dryrun.VoxGestCameraRecognitionController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Primary = Color(0xFF006C73)
private val PrimaryLight = Color(0xFF00AFC1)
private val AppBg = Color(0xFFF0F7F7)
private val CardWhite = Color(0xFFFFFFFF)
private val SoftCyan = Color(0xFFEAF8F8)
private val AccentGreen = Color(0xFF00C853)
private val TextMain = Color(0xFF112B3A)
private val TextMuted = Color(0xFF546E7A)
private val TextFaint = Color(0xFF90A4AE)
private val Border = Color(0xFFE1ECEF)
private val DarkInk = Color(0xFFFFFFFF)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)
private val Blue = Color(0xFF22A7D8)
private val Purple = Color(0xFF7C5AA6)
private val Green = Color(0xFF43A047)
private const val PRESENTATION_MODE = true
private const val SHOW_DEBUG_TOOLS = false

private enum class VoxTab(
    val label: String,
    @DrawableRes val icon: Int
) {
    Sign("Sign", R.drawable.ic_hand_gesture),
    Listen("Listen", R.drawable.ic_mic),
    Phrases("Phrases", R.drawable.ic_chat),
    History("History", R.drawable.ic_history)
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

private data class PhraseUi(
    val label: String,
    val icon: Int,
    val color: Color
)

private val DemoTokenWords = listOf(
    "WHAT",
    "YOUR",
    "NAME",
    "MY",
    "YOU",
    "OKAY",
    "STUDENT",
    "WHERE",
    "LIVE",
    "NOTHING",
    "CLEAR",
    "SPEAK"
)

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20
)

@Composable
fun VoxGestPresentationApp() {
    var selectedTab by remember { mutableStateOf(VoxTab.Sign) }
    var currentWord by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf("") }
    var demoTokenBuffer by remember { mutableStateOf("") }
    var recognitionRunning by remember { mutableStateOf(false) }
    var recognitionStatus by remember { mutableStateOf("Tap Start Recognition") }
    var pendingAvatarText by remember { mutableStateOf("") }
    var pendingAvatarRequestId by remember { mutableStateOf(0) }
    var selectedPhrase by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<HistoryUiEntry>() }
    val context = LocalContext.current
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    fun speakNow(text: String) {
        if (!isMeaningfulOutput(text)) return
        ttsRef.value?.let { speak(it, text) }
    }

    fun queueAvatarPhrase(text: String) {
        if (!isMeaningfulOutput(text)) return
        pendingAvatarText = text
        pendingAvatarRequestId += 1
        selectedTab = VoxTab.Listen
    }

    fun clearDemoTokens() {
        demoTokenBuffer = ""
        currentWord = ""
        sentence = ""
    }

    fun addDemoToken(token: String) {
        val clean = token.trim().uppercase(Locale.US)
        when (clean) {
            "", "NOTHING" -> return
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

        val tokens = (demoTokenBuffer.toDemoTokens() + clean).takeLast(4)
        demoTokenBuffer = tokens.joinToString("|")
        currentWord = clean
        history.add(0, HistoryUiEntry("Today", "Sign", clean, nowLabel(), "Accepted sign", R.drawable.ic_hand_gesture, PrimaryLight))

        val finalized = demoSentenceForTokens(tokens)
        if (finalized == null) {
            sentence = tokens.joinToString(" ")
            return
        }

        sentence = finalized
        history.add(0, HistoryUiEntry("Today", "Sign", finalized, nowLabel(), "From recognition", R.drawable.ic_hand_gesture, PrimaryLight))
        queueAvatarPhrase(finalized)
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef.value?.language = Locale.US
            }
        }
        ttsRef.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsRef.value = null
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Primary,
            onPrimary = DarkInk,
            background = AppBg,
            surface = CardWhite,
            onSurface = TextMain
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = AppBg,
            bottomBar = {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBg)
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        VoxTab.Sign -> SignScreen(
                            currentWord = currentWord,
                            sentence = sentence,
                            recognitionRunning = recognitionRunning,
                            recognitionStatus = recognitionStatus,
                            presentationMode = PRESENTATION_MODE,
                            onSpeak = {
                                if (isMeaningfulOutput(sentence)) {
                                    speakNow(sentence)
                                    history.add(0, HistoryUiEntry("Today", "Sign", sentence, nowLabel(), "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                                }
                            },
                            onDelete = {
                                val tokens = demoTokenBuffer.toDemoTokens()
                                if (tokens.isNotEmpty()) {
                                    val nextTokens = tokens.dropLast(1)
                                    demoTokenBuffer = nextTokens.joinToString("|")
                                    currentWord = nextTokens.lastOrNull() ?: ""
                                    sentence = demoSentenceForTokens(nextTokens) ?: nextTokens.joinToString(" ")
                                } else {
                                    sentence = sentence.split(",").dropLast(1).joinToString(", ")
                                }
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
                            onAcceptedRecognition = { result -> addDemoToken(result.label) },
                            demoTokens = demoTokenBuffer.toDemoTokens(),
                            onDemoToken = { addDemoToken(it) }
                        )
                        VoxTab.Listen -> ListenScreen(
                            pendingAvatarText = pendingAvatarText,
                            pendingAvatarRequestId = pendingAvatarRequestId,
                            onSpeechSaved = { text ->
                                if (isMeaningfulOutput(text)) {
                                    history.add(0, HistoryUiEntry("Today", "Speech", text, nowLabel(), "Shown in signs", R.drawable.ic_waveform, Primary))
                                }
                            }
                        )
                        VoxTab.Phrases -> PhrasesScreen(
                            selectedPhrase = selectedPhrase,
                            onPhrase = { phrase ->
                                if (isMeaningfulOutput(phrase)) {
                                    selectedPhrase = phrase
                                    speakNow(phrase)
                                    history.add(0, HistoryUiEntry("Today", "Phrase", phrase, nowLabel(), "From quick phrase", R.drawable.ic_chat, Amber))
                                    queueAvatarPhrase(phrase)
                                }
                            }
                        )
                        VoxTab.History -> HistoryScreen(
                            entries = history,
                            onClearAll = { history.clear() },
                            onReplay = { entry ->
                                if (isMeaningfulOutput(entry.text)) {
                                    speakNow(entry.text)
                                    queueAvatarPhrase(entry.text)
                                }
                            }
                        )
                    }
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

private fun isMeaningfulOutput(text: String): Boolean {
    return text.trim().isNotBlank() && text.trim().uppercase(Locale.US) != "NOTHING"
}

private fun String.toDemoTokens(): List<String> {
    return split("|").map { it.trim() }.filter { it.isNotBlank() && it != "NOTHING" }
}

private fun demoSentenceForTokens(tokens: List<String>): String? {
    val clean = tokens.map { it.uppercase(Locale.US) }.filter { it.isNotBlank() && it != "NOTHING" }
    return when {
        clean.endsWithTokens("WHAT", "YOUR", "NAME") -> "What is your name?"
        clean.endsWithTokens("MY", "NAME") -> "My name is [letters from alphabet recognizer]."
        clean.endsWithTokens("YOU", "OKAY") -> "Are you okay?"
        clean.endsWithTokens("YOU", "STUDENT") -> "Are you a student?"
        clean.endsWithTokens("WHERE", "YOU", "LIVE") -> "Where do you live?"
        else -> null
    }
}

private fun List<String>.endsWithTokens(vararg expected: String): Boolean {
    return size >= expected.size && takeLast(expected.size) == expected.toList()
}

@Composable
private fun ScreenTopBar(title: String, @DrawableRes trailing: Int, secondTrailing: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(if (secondTrailing == null) 24.dp else 56.dp))
        Text(
            text = title,
            color = Primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            secondTrailing?.let { VoxIcon(it, "$title action", Primary, Modifier.size(21.dp)) }
            VoxIcon(trailing, "$title menu", Primary, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SignScreen(
    currentWord: String,
    sentence: String,
    recognitionRunning: Boolean,
    recognitionStatus: String,
    presentationMode: Boolean,
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
    ScreenScroll {
        ScreenTopBar("SIGN", R.drawable.ic_settings)
        RecognitionAreaCard(
            recognitionRunning = recognitionRunning,
            recognitionStatus = recognitionStatus,
            presentationMode = presentationMode,
            onStartRecognition = onStartRecognition,
            onStopRecognition = onStopRecognition,
            onRecognitionStatus = onRecognitionStatus,
            onAcceptedRecognition = onAcceptedRecognition
        )
        CurrentWordCard(currentWord)
        SentenceCard(sentence, onSpeak)
        if (SHOW_DEBUG_TOOLS) {
            DemoTokenPanel(
                tokens = demoTokens,
                onToken = onDemoToken
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(R.drawable.ic_volume_up, "Speak", Primary, Modifier.weight(1f), onSpeak)
            ActionButton(R.drawable.ic_delete_outline, "Delete", TextMuted, Modifier.weight(1f), onDelete)
            ActionButton(R.drawable.ic_cancel, "Clear", Red, Modifier.weight(1f), onClear)
        }
        PresentationBuildFooter()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RecognitionAreaCard(
    recognitionRunning: Boolean,
    recognitionStatus: String,
    presentationMode: Boolean,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onRecognitionStatus: (String) -> Unit,
    onAcceptedRecognition: (RecognitionResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val controllerRef = remember { mutableStateOf<VoxGestCameraRecognitionController?>(null) }
    var recognitionFeedback by remember { mutableStateOf(RecognitionFeedback.idle()) }
    var recognizedToast by remember { mutableStateOf("") }
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

    LaunchedEffect(recognitionRunning, hasCameraPermission, previewView) {
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
            onRecognitionFeedback = { recognitionFeedback = it },
            onAcceptedResult = onAcceptedRecognition
        ).also { it.start(previewView) }
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

    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(1.dp, Border, RoundedCornerShape(22.dp))
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Recognition status: $recognitionStatus"
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            if (!recognitionRunning || !hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SoftCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VoxIcon(
                            R.drawable.ic_camera_off,
                            "Recognition status",
                            TextMuted,
                            Modifier.size(46.dp)
                        )
                        Text(
                            if (hasCameraPermission) "Camera preview appears here" else "Camera permission needed",
                            color = TextMain,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        Text(
                            recognitionStatus,
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 7.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                }
            }
            HandScanOverlay(
                feedback = recognitionFeedback,
                recognitionRunning = recognitionRunning && hasCameraPermission,
                modifier = Modifier.fillMaxSize()
            )
            StatusChip(
                label = recognitionStatus,
                dotColor = if (recognitionRunning) AccentGreen else Amber,
                containerColor = if (recognitionRunning) Color(0xFFE8F7EE) else Color(0xFFFFF7ED),
                contentColor = if (recognitionRunning) Green else Amber,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            )
        }
        AnimatedVisibility(visible = recognizedToast.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Primary.copy(alpha = 0.94f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        recognizedToast,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (presentationMode) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    VoxIcon(R.drawable.ic_play_arrow, "Start Recognition", DarkInk, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Start Recognition", color = DarkInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Primary),
                    border = BorderStroke(1.dp, Border)
                ) {
                    VoxIcon(R.drawable.ic_cancel, "Stop Recognition", Primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Stop Recognition", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
private fun CurrentWordCard(word: String) {
    val clean = word.trim()
    val hasWord = isMeaningfulOutput(clean)
    val displayWord = if (hasWord) clean.uppercase(Locale.US) else "No word yet"
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("CURRENT WORD", Modifier.weight(1f))
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
                color = if (hasWord) Primary else TextFaint,
                fontSize = if (hasWord) 30.sp else 18.sp,
                fontWeight = if (hasWord) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DemoTokenPanel(
    tokens: List<String>,
    onToken: (String) -> Unit
) {
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("DEMO TOKEN AREA")
                Text(
                    if (tokens.isEmpty()) "Tap tokens to build a phrase." else tokens.joinToString(" + "),
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            StatusChip(
                label = "Phrase Demo",
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
                                "NOTHING" -> TextFaint
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SentenceCard(sentence: String, onSpeak: () -> Unit) {
    val hasSentence = isMeaningfulOutput(sentence)
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Label("SENTENCE")
        Text(
            if (hasSentence) sentence else "No sentence yet.",
            color = if (hasSentence) Primary else TextFaint,
            fontSize = if (hasSentence) 20.sp else 16.sp,
            lineHeight = 27.sp,
            fontWeight = if (hasSentence) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { onSpeak() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color(0xFFFFF7ED)) {
                Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                    VoxIcon(R.drawable.ic_volume_up, "Tap to speak", Amber, Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (hasSentence) "Tap to speak" else "No sentence yet.",
                color = Amber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
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
            Text(label, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ListenScreen(
    pendingAvatarText: String,
    pendingAvatarRequestId: Int,
    onSpeechSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var speechStatus by remember { mutableStateOf("Tap the microphone and speak.") }
    var avatarState by remember { mutableStateOf(AvatarPlaybackState()) }
    val avatarController = remember { AvatarController { avatarState = it } }
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
        speechStatus = if (granted) "Tap the microphone and speak." else "Microphone permission is needed."
    }

    fun stopListening() {
        recognizerState.value?.stopListening()
        recognizerState.value?.destroy()
        recognizerState.value = null
        listening = false
        avatarController.setListening(false)
    }

    fun startListening() {
        if (!speechAvailable) {
            speechStatus = "Speech recognition is unavailable on this device."
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
                avatarController.setListening(false)
                speechStatus = "Tap the microphone and speak."
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
                avatarController.setListening(false)
                recognizerState.value?.destroy()
                recognizerState.value = null
                if (isMeaningfulOutput(text)) {
                    transcript = text
                    speechStatus = "Transcript ready"
                    onSpeechSaved(text)
                    avatarController.playTextAsSigns(text)
                } else {
                    speechStatus = "No speech recognized."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                speechStatus = "Listening..."
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        speechStatus = "Listening..."
        avatarController.setListening(true)
        recognizer.startListening(intent)
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizerState.value?.destroy()
            avatarController.detach()
        }
    }

    LaunchedEffect(pendingAvatarRequestId) {
        val clean = pendingAvatarText.trim()
        if (pendingAvatarRequestId > 0 && clean.isNotBlank()) {
            transcript = clean
            listening = false
            speechStatus = "Transcript ready"
            avatarController.setListening(false)
            avatarController.playTextAsSigns(clean)
        }
    }

    fun playTranscript() {
        if (isMeaningfulOutput(transcript)) {
            avatarController.playTextAsSigns(transcript)
        }
    }

    ScreenScroll {
        ScreenTopBar("LISTEN", R.drawable.ic_settings)
        SpeechTranscriptCard(
            transcript = transcript,
            listening = listening,
            speechStatus = speechStatus,
            onMicTap = {
                if (listening) stopListening() else startListening()
            }
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp)) {
            Text(if (listening) "Listening..." else "Tap mic to listen", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Speak clearly", color = TextFaint, fontSize = 12.sp)
        }
        AvatarCard(
            avatarController = avatarController,
            avatarState = avatarState,
            onReplay = { avatarController.replay() },
            onPlay = { playTranscript() },
            onStop = { avatarController.stop() },
            onDebugWord = { word ->
                if (!avatarController.playWord(word)) {
                    avatarState = AvatarPlaybackState(label = "Ready", detail = "NOTHING ignored", currentWord = "")
                }
            }
        )
        PresentationBuildFooter()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SpeechTranscriptCard(
    transcript: String,
    listening: Boolean,
    speechStatus: String,
    onMicTap: () -> Unit
) {
    val hasTranscript = isMeaningfulOutput(transcript)
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("SPEECH TRANSCRIPT", Modifier.weight(1f))
            VoxIcon(R.drawable.ic_volume_up, "Speak transcript", TextMuted, Modifier.size(17.dp))
        }
        Text(
            if (hasTranscript) transcript else "Tap the microphone and speak.",
            color = if (hasTranscript) Primary else TextMuted,
            fontSize = if (hasTranscript) 27.sp else 20.sp,
            lineHeight = 31.sp,
            fontWeight = if (hasTranscript) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp, bottom = 18.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Waveform(Modifier.weight(1f), listening)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .clickable { onMicTap() },
                contentAlignment = Alignment.Center
            ) {
                if (listening) {
                    MicPulse()
                }
                VoxIcon(R.drawable.ic_mic, "Microphone", DarkInk, Modifier.size(30.dp))
            }
            Waveform(Modifier.weight(1f), listening)
        }
        Text(
            speechStatus,
            color = TextFaint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun AvatarCard(
    avatarController: AvatarController,
    avatarState: AvatarPlaybackState,
    onReplay: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDebugWord: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(292.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SoftCyan),
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
                        avatarState.isPlaying -> "Signing..."
                        avatarState.isListening -> "Listening..."
                        else -> "Ready"
                    },
                    dotColor = PrimaryLight,
                    containerColor = SoftCyan,
                    contentColor = Primary
                )
            }
            AndroidView(
                factory = { viewContext -> AvatarView(viewContext).also { avatarController.attach(it) } },
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
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinePillButton("Replay", R.drawable.ic_replay, Modifier.weight(1f), onReplay)
                        FilledPillButton("Play Signs", R.drawable.ic_play_arrow, Modifier.weight(1f), onPlay)
                    }
                }
            }
        }
    }
    if (SHOW_DEBUG_TOOLS && BuildConfig.DEBUG) {
        Text(
            "Debug avatar tests",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
private fun PhrasesScreen(selectedPhrase: String, onPhrase: (String) -> Unit) {
    ScreenScroll {
        ScreenTopBar("PHRASES", R.drawable.ic_search)
        QuickPhraseHero()
        SelectedPhraseCard(selectedPhrase)
        CategoryChips()
        PhraseGrid(onPhrase)
        PresentationBuildFooter()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SelectedPhraseCard(selectedPhrase: String) {
    if (!isMeaningfulOutput(selectedPhrase)) return
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
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
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryLight)))
            .padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart).width(190.dp)) {
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
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
private fun PhraseGrid(onPhrase: (String) -> Unit) {
    val phrases = listOf(
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
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        phrases.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { phrase ->
                    PhraseCard(phrase = phrase, modifier = Modifier.weight(1f), onClick = { onPhrase(phrase.label) })
                }
            }
        }
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
                .height(52.dp),
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
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
        Text("Avatar: Prototype Visual Response", color = TextFaint, fontSize = 10.sp)
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(1.dp, contentColor.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BlinkDot(dotColor)
        Text(label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinePillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, Primary, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FilledPillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Primary)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, DarkInk, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = DarkInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Waveform(modifier: Modifier, active: Boolean) {
    if (!active) {
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
private fun MicPulse() {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardWhite,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoxTab.values().forEach { tab ->
                val active = tab == selectedTab
                Column(
                    modifier = Modifier
                        .width(66.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (active) Primary else Color.Transparent)
                        .clickable { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VoxIcon(tab.icon, tab.label, if (active) DarkInk else TextMuted, Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        color = if (active) DarkInk else TextMuted,
                        fontSize = if (active) 10.sp else 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
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
