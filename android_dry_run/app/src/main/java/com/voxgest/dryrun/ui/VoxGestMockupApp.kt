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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val Primary = Color(0xFFB8F060)
private val PrimaryLight = Color(0xFF9ED84A)
private val AppBg = Color(0xFF09090B)
private val CardWhite = Color(0xFF17171C)
private val SoftCyan = Color(0xFF1E1E26)
private val AccentGreen = Color(0xFFB8F060)
private val TextMain = Color(0xFFEDEDEA)
private val TextMuted = Color(0xFF989894)
private val TextFaint = Color(0xFF55554F)
private val Border = Color(0x24FFFFFF)
private val DarkInk = Color(0xFF09090B)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFEF4444)
private val Blue = Color(0xFF38BDF8)
private val Purple = Color(0xFF8B5CF6)
private val Green = Color(0xFF22C55E)

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

@Composable
fun VoxGestMockupApp() {
    var selectedTab by remember { mutableStateOf(VoxTab.Sign) }
    var currentWord by remember { mutableStateOf("HELLO") }
    var sentence by remember { mutableStateOf("HELLO, I NEED WATER") }
    val history = remember {
        mutableStateListOf(
            HistoryUiEntry("Today", "Sign", "HELLO, I NEED WATER", "10:45 AM", "Spoken", R.drawable.ic_hand_gesture, PrimaryLight),
            HistoryUiEntry("Today", "Speech", "Hello, how can I help you?", "10:42 AM", "Shown in signs", R.drawable.ic_waveform, Primary),
            HistoryUiEntry("Today", "Phrase", "I need help", "10:40 AM", "Shown in signs", R.drawable.ic_chat, Amber),
            HistoryUiEntry("Yesterday", "Sign", "THANK YOU", "08:15 PM", "Spoken", R.drawable.ic_hand_gesture, PrimaryLight),
            HistoryUiEntry("Yesterday", "Speech", "Please wait a moment.", "07:50 PM", "Shown in signs", R.drawable.ic_waveform, Primary),
            HistoryUiEntry("Yesterday", "Phrase", "Call a doctor", "07:30 PM", "Shown in signs", R.drawable.ic_chat, Amber)
        )
    }
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context) { } }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
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
                FakeStatusBar()
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        VoxTab.Sign -> SignScreen(
                            currentWord = currentWord,
                            sentence = sentence,
                            onSpeak = {
                                speak(tts, sentence)
                                history.add(0, HistoryUiEntry("Today", "Sign", sentence, "Now", "Spoken", R.drawable.ic_hand_gesture, PrimaryLight))
                            },
                            onDelete = {
                                sentence = sentence.split(",").dropLast(1).joinToString(", ").ifBlank { currentWord }
                            },
                            onClear = {
                                currentWord = "READY"
                                sentence = ""
                            }
                        )
                        VoxTab.Listen -> ListenScreen(
                            onSpeechSaved = { text ->
                                history.add(0, HistoryUiEntry("Today", "Speech", text, "Now", "Shown in signs", R.drawable.ic_waveform, Primary))
                            }
                        )
                        VoxTab.Phrases -> PhrasesScreen(
                            onPhrase = { phrase ->
                                speak(tts, phrase)
                                history.add(0, HistoryUiEntry("Today", "Phrase", phrase, "Now", "Shown in signs", R.drawable.ic_chat, Amber))
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
    if (clean.isNotBlank()) {
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "voxgest-speak")
    }
}

@Composable
private fun FakeStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:30", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalIcon()
            WifiDot()
            BatteryIcon()
        }
    }
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
            letterSpacing = 0.7.sp,
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
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    ScreenScroll {
        ScreenTopBar("SIGN", R.drawable.ic_settings)
        CameraPreviewCard()
        CurrentWordCard(currentWord)
        SentenceCard(sentence.ifBlank { "Ready for accepted signs" }, onSpeak)
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(R.drawable.ic_volume_up, "Speak", Primary, Modifier.weight(1f), onSpeak)
            ActionButton(R.drawable.ic_delete_outline, "Delete", TextMuted, Modifier.weight(1f), onDelete)
            ActionButton(R.drawable.ic_cancel, "Clear", Red, Modifier.weight(1f), onClear)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CameraPreviewCard() {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFD1D5DB))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF))))
            val cx = w * 0.52f
            val headR = w * 0.13f
            drawCircle(Color(0xFFF1C9A8), headR, Offset(cx, h * 0.28f))
            drawOval(Color(0xFF3B2418), Offset(cx - headR * 1.05f, h * 0.16f), Size(headR * 2.1f, headR * 1.0f))
            drawRoundRect(Color(0xFF111827), Offset(cx - w * 0.22f, h * 0.44f), Size(w * 0.44f, h * 0.32f), CornerRadius(28.dp.toPx(), 28.dp.toPx()))
            drawCircle(Color(0xFF111827), w * 0.20f, Offset(cx, h * 0.60f))
            drawLine(Color(0xFFF1C9A8), Offset(w * 0.30f, h * 0.73f), Offset(w * 0.35f, h * 0.44f), strokeWidth = 16.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFFF1C9A8), Offset(w * 0.35f, h * 0.44f), Offset(w * 0.30f, h * 0.32f), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
            drawRoundRect(Color(0xFFF1C9A8), Offset(w * 0.25f, h * 0.27f), Size(42.dp.toPx(), 58.dp.toPx()), CornerRadius(14.dp.toPx(), 14.dp.toPx()))
            val palm = Offset(w * 0.31f, h * 0.34f)
            val fingerXs = listOf(-18, -8, 2, 12, 22)
            fingerXs.forEachIndexed { index, dx ->
                val tip = Offset(palm.x + dx.dp.toPx(), h * (0.16f + index * 0.015f))
                drawLine(AccentGreen.copy(alpha = 0.75f), palm, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(AccentGreen.copy(alpha = 0.75f), 3.5.dp.toPx(), tip)
            }
            drawLine(AccentGreen.copy(alpha = 0.65f), Offset(w * 0.31f, h * 0.50f), palm, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        TrackingBadge()
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(36.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.86f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(R.drawable.ic_wb_sunny, "Light", TextMuted, Modifier.size(17.dp))
            }
        }
        TrackingCorners()
    }
}

@Composable
private fun TrackingBadge() {
    Row(
        modifier = Modifier
            .padding(14.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB0000000))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BlinkDot(AccentGreen)
        Text("Tracking Hand", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackingCorners() {
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        val stroke = 3.dp.toPx()
        val len = 28.dp.toPx()
        val color = AccentGreen.copy(alpha = 0.82f)
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, len), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(size.width - len, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, len), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, size.height), Offset(len, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - len), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, size.height), Offset(size.width - len, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - len), stroke, StrokeCap.Round)
    }
}

@Composable
private fun CurrentWordCard(word: String) {
    VoxCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
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
            Text(word, color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            VoxIcon(R.drawable.ic_hand_gesture, "Gesture", Amber, Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SentenceCard(sentence: String, onSpeak: () -> Unit) {
    VoxCard(modifier = Modifier.padding(horizontal = 20.dp)) {
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
private fun ListenScreen(onSpeechSaved: (String) -> Unit) {
    var listening by remember { mutableStateOf(true) }
    var transcript by remember { mutableStateOf("Hello, how can I help you?") }
    var avatarState by remember { mutableStateOf(AvatarPlaybackState()) }
    val avatarController = remember { AvatarController { avatarState = it } }

    DisposableEffect(Unit) {
        onDispose { avatarController.detach() }
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
                if (listening) transcript = "Hello, how can I help you?"
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
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SpeechTranscriptCard(
    transcript: String,
    listening: Boolean,
    onMicTap: () -> Unit
) {
    VoxCard(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                Surface(shape = RoundedCornerShape(999.dp), color = CardWhite.copy(alpha = 0.86f), shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        BlinkDot(Primary)
                        Text(
                            if (avatarState.isPlaying) "Signing..." else "Analyzing...",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("HELLO", "THANKYOU", "WATER", "EAT", "NOTHING", "STOP").forEach { word ->
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable {
                            if (word == "STOP") onStop() else onDebugWord(word)
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = SoftCyan,
                    border = BorderStroke(1.dp, Border)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(if (word == "THANKYOU") "THANK" else word, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
private fun VoxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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
        letterSpacing = 1.0.sp,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun OutlinePillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, Primary, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilledPillButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Primary)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, DarkInk, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = DarkInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Waveform(modifier: Modifier, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
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
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun SignalIcon() {
    Canvas(Modifier.size(17.dp)) {
        val barW = 2.7.dp.toPx()
        listOf(5, 8, 11, 14).forEachIndexed { index, h ->
            drawRoundRect(
                color = TextMain,
                topLeft = Offset(index * 4.dp.toPx(), size.height - h.dp.toPx()),
                size = Size(barW, h.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun WifiDot() {
    Canvas(Modifier.size(15.dp)) {
        drawArc(TextMain, 205f, 130f, false, topLeft = Offset(size.width * 0.10f, size.height * 0.10f), size = Size(size.width * 0.80f, size.height * 0.80f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawArc(TextMain, 215f, 110f, false, topLeft = Offset(size.width * 0.25f, size.height * 0.32f), size = Size(size.width * 0.50f, size.height * 0.50f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(TextMain, 1.8.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.76f))
    }
}

@Composable
private fun BatteryIcon() {
    Canvas(Modifier.size(18.dp, 13.dp)) {
        drawRoundRect(TextMain, Offset(0f, 2.dp.toPx()), Size(15.dp.toPx(), 9.dp.toPx()), CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawRoundRect(TextMain, Offset(3.dp.toPx(), 4.dp.toPx()), Size(9.dp.toPx(), 5.dp.toPx()), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        drawRoundRect(TextMain, Offset(16.dp.toPx(), 5.dp.toPx()), Size(2.dp.toPx(), 4.dp.toPx()), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
    }
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
