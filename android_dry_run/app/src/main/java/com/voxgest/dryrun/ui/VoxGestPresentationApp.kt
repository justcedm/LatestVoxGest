package com.voxgest.dryrun.ui

import android.speech.tts.TextToSpeech
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.voxgest.app.avatar.AvatarController
import com.voxgest.app.avatar.AvatarPlaybackState
import com.voxgest.app.avatar.AvatarView
import com.voxgest.dryrun.BuildConfig
import com.voxgest.dryrun.R
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

@Composable
fun VoxGestPresentationApp() {
    var selectedTab by rememberSaveable { mutableStateOf(VoxTab.Sign) }
    var currentWord by rememberSaveable { mutableStateOf("READY") }
    var sentence by rememberSaveable { mutableStateOf("") }
    var demoTokenBuffer by rememberSaveable { mutableStateOf("") }
    var recognitionRunning by rememberSaveable { mutableStateOf(false) }
    var pendingAvatarText by rememberSaveable { mutableStateOf("") }
    var pendingAvatarRequestId by rememberSaveable { mutableStateOf(0) }
    val history = remember { mutableStateListOf<HistoryUiEntry>() }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    fun speakNow(text: String) {
        if (!isMeaningfulOutput(text)) return
        val engine = tts ?: TextToSpeech(context) { }.also { tts = it }
        speak(engine, text)
    }

    fun queueAvatarPhrase(text: String) {
        if (!isMeaningfulOutput(text)) return
        pendingAvatarText = text
        pendingAvatarRequestId += 1
        selectedTab = VoxTab.Listen
    }

    fun clearDemoTokens() {
        demoTokenBuffer = ""
        currentWord = "READY"
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
                    history.add(0, HistoryUiEntry("Today", "Sign", sentence, "Now", "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                }
                return
            }
        }

        val tokens = (demoTokenBuffer.toDemoTokens() + clean).takeLast(4)
        demoTokenBuffer = tokens.joinToString("|")
        currentWord = clean

        val finalized = demoSentenceForTokens(tokens)
        if (finalized == null) {
            sentence = tokens.joinToString(" ")
            return
        }

        sentence = finalized
        history.add(0, HistoryUiEntry("Today", "Sign", finalized, "Now", "Shown in signs", R.drawable.ic_hand_gesture, PrimaryLight))
        queueAvatarPhrase(finalized)
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
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
                            presentationMode = PRESENTATION_MODE,
                            onSpeak = {
                                if (isMeaningfulOutput(sentence)) {
                                    speakNow(sentence)
                                    history.add(0, HistoryUiEntry("Today", "Sign", sentence, "Now", "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                                }
                            },
                            onDelete = {
                                val tokens = demoTokenBuffer.toDemoTokens()
                                if (tokens.isNotEmpty()) {
                                    val nextTokens = tokens.dropLast(1)
                                    demoTokenBuffer = nextTokens.joinToString("|")
                                    currentWord = nextTokens.lastOrNull() ?: "READY"
                                    sentence = demoSentenceForTokens(nextTokens) ?: nextTokens.joinToString(" ")
                                } else {
                                    sentence = sentence.split(",").dropLast(1).joinToString(", ").ifBlank { currentWord }
                                }
                            },
                            onClear = {
                                clearDemoTokens()
                            },
                            onStartRecognition = {
                                recognitionRunning = true
                                currentWord = "READY"
                            },
                            onStopRecognition = {
                                recognitionRunning = false
                                currentWord = "READY"
                            },
                            demoTokens = demoTokenBuffer.toDemoTokens(),
                            onDemoToken = { addDemoToken(it) }
                        )
                        VoxTab.Listen -> ListenScreen(
                            pendingAvatarText = pendingAvatarText,
                            pendingAvatarRequestId = pendingAvatarRequestId,
                            onSpeechSaved = { text ->
                                if (isMeaningfulOutput(text)) {
                                    history.add(0, HistoryUiEntry("Today", "Speech", text, "Now", "Shown in signs", R.drawable.ic_waveform, Primary))
                                }
                            }
                        )
                        VoxTab.Phrases -> PhrasesScreen(
                            onPhrase = { phrase ->
                                if (isMeaningfulOutput(phrase)) {
                                    speakNow(phrase)
                                    history.add(0, HistoryUiEntry("Today", "Phrase", phrase, "Now", "Shown in signs", R.drawable.ic_chat, Amber))
                                    queueAvatarPhrase(phrase)
                                }
                            }
                        )
                        VoxTab.History -> HistoryScreen(entries = history)
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
    presentationMode: Boolean,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    demoTokens: List<String>,
    onDemoToken: (String) -> Unit
) {
    ScreenScroll {
        ScreenTopBar("SIGN", R.drawable.ic_settings)
        RecognitionAreaCard(
            recognitionRunning = recognitionRunning,
            presentationMode = presentationMode,
            onStartRecognition = onStartRecognition,
            onStopRecognition = onStopRecognition
        )
        CurrentWordCard(currentWord)
        SentenceCard(sentence.ifBlank { "Ready for accepted signs" }, onSpeak)
        DemoTokenPanel(
            tokens = demoTokens,
            onToken = onDemoToken
        )
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
    presentationMode: Boolean,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit
) {
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("RECOGNITION AREA", Modifier.weight(1f))
            StatusChip(
                label = if (recognitionRunning) "Calibration" else "Paused",
                dotColor = if (recognitionRunning) AccentGreen else Amber,
                containerColor = if (recognitionRunning) Color(0xFFE8F7EE) else Color(0xFFFFF7ED),
                contentColor = if (recognitionRunning) Green else Amber
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (recognitionRunning) Color(0xFFE7F8F5) else SoftCyan)
                .border(1.dp, Border, RoundedCornerShape(18.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VoxIcon(
                    if (recognitionRunning) R.drawable.ic_hand_gesture else R.drawable.ic_camera_off,
                    "Recognition status",
                    if (recognitionRunning) Primary else TextMuted,
                    Modifier.size(34.dp)
                )
                Text(
                    "Recognition mode is available for testing but still under calibration.",
                    color = TextMain,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    if (recognitionRunning) "Manual start is active; camera inference remains gated." else "Tap Start Recognition when panelists want to try calibration mode.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (presentationMode) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledPillButton("Start Recognition", R.drawable.ic_play_arrow, Modifier.weight(1f), onStartRecognition)
                OutlinePillButton("Stop Recognition", R.drawable.ic_cancel, Modifier.weight(1f), onStopRecognition)
            }
        }
    }
}

@Composable
private fun CurrentWordCard(word: String) {
    val displayWord = when (word.trim().uppercase(Locale.US)) {
        "", "NOTHING" -> "READY"
        "HELLO" -> "HELLO"
        else -> word
    }
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("CURRENT WORD", Modifier.weight(1f))
            VoxIcon(R.drawable.ic_volume_up, "Speak current word", Primary, Modifier.size(17.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(displayWord, color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Label("SENTENCE")
        Text(
            sentence,
            color = Primary,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
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
            Text("Tap to speak", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    var listening by rememberSaveable { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("What is your name?") }
    var avatarState by remember { mutableStateOf(AvatarPlaybackState()) }
    val avatarController = remember { AvatarController { avatarState = it } }

    DisposableEffect(Unit) {
        onDispose { avatarController.detach() }
    }

    LaunchedEffect(pendingAvatarRequestId) {
        val clean = pendingAvatarText.trim()
        if (pendingAvatarRequestId > 0 && clean.isNotBlank()) {
            transcript = clean
            listening = false
            avatarController.setListening(false)
            avatarController.playTextAsSigns(clean)
        }
    }

    fun playTranscript() {
        avatarController.playTextAsSigns(transcript)
        onSpeechSaved(transcript)
    }

    ScreenScroll {
        ScreenTopBar("LISTEN", R.drawable.ic_settings)
        SpeechTranscriptCard(
            transcript = transcript,
            listening = listening,
            onMicTap = {
                listening = !listening
                avatarController.setListening(listening)
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
    onMicTap: () -> Unit
) {
    VoxGestCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("SPEECH TRANSCRIPT", Modifier.weight(1f))
            VoxIcon(R.drawable.ic_volume_up, "Speak transcript", TextMuted, Modifier.size(17.dp))
        }
        Text(
            transcript,
            color = Primary,
            fontSize = 27.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Bold,
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
                    label = if (avatarState.isPlaying) "Signing..." else "Analyzing...",
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
    if (BuildConfig.DEBUG) {
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
private fun PhrasesScreen(onPhrase: (String) -> Unit) {
    ScreenScroll {
        ScreenTopBar("PHRASES", R.drawable.ic_search)
        QuickPhraseHero()
        CategoryChips()
        PhraseGrid(onPhrase)
        PresentationBuildFooter()
        Spacer(Modifier.height(16.dp))
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
        PhraseUi("What is your name?", R.drawable.ic_chat, Primary),
        PhraseUi("My name is...", R.drawable.ic_hand_gesture, PrimaryLight),
        PhraseUi("Are you okay?", R.drawable.ic_check_circle, Green),
        PhraseUi("Are you a student?", R.drawable.ic_chat, Blue),
        PhraseUi("Where do you live?", R.drawable.ic_history, Purple),
        PhraseUi("I need help", R.drawable.ic_warning, Color(0xFFF97316)),
        PhraseUi("Call a doctor", R.drawable.ic_medical, PrimaryLight),
        PhraseUi("I need water", R.drawable.ic_water_drop, Blue),
        PhraseUi("Stop", R.drawable.ic_hand_gesture, Red),
        PhraseUi("Please wait", R.drawable.ic_clock, Amber),
        PhraseUi("Thank you", R.drawable.ic_hand_gesture, Purple),
        PhraseUi("Yes", R.drawable.ic_check_circle, Green),
        PhraseUi("No", R.drawable.ic_no_circle, Red)
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
private fun HistoryScreen(entries: List<HistoryUiEntry>) {
    ScreenScroll {
        ScreenTopBar("HISTORY", R.drawable.ic_filter_list, R.drawable.ic_search)
        HistorySection("Today", entries.filter { it.section == "Today" })
        HistorySection("Yesterday", entries.filter { it.section == "Yesterday" })
        Button(
            onClick = {},
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
private fun HistorySection(title: String, entries: List<HistoryUiEntry>) {
    if (entries.isEmpty()) return
    Text(
        title,
        color = Primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp)
    )
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { HistoryItem(it) }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun HistoryItem(entry: HistoryUiEntry) {
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
            VoxIcon(R.drawable.ic_play_arrow, "Replay", Primary, Modifier.size(18.dp))
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
    contentColor: Color
) {
    Row(
        modifier = Modifier
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
