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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.font.FontStyle
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

private val Bg = Color(0xFF09090B)
private val Bg2 = Color(0xFF111114)
private val CardBg = Color(0xFF17171C)
private val CardHi = Color(0xFF1E1E26)
private val CardTop = Color(0xFF24242E)
private val Border = Color.White.copy(alpha = 0.08f)
private val BorderStrong = Color.White.copy(alpha = 0.14f)
private val TextMain = Color(0xFFEDEDEA)
private val TextMuted = Color(0xFF989894)
private val TextDim = Color(0xFF55554F)
private val TextFaint = Color(0xFF3A3A36)
private val Accent = Color(0xFFB8F060)
private val Accent2 = Color(0xFF9ED84A)
private val AccentDim = Color(0x26B8F060)
private val Red = Color(0xFFFF5252)
private val Amber = Color(0xFFF0A830)
private val Teal = Color(0xFF3ECFAA)
private val Blue = Color(0xFF5B9EF0)
private val Purple = Color(0xFFA78BFA)

private enum class VoxTab(
    val label: String,
    @DrawableRes val icon: Int
) {
    Sign("Sign", R.drawable.ic_hand_gesture),
    Listen("Listen", R.drawable.ic_mic),
    Phrases("Phrases", R.drawable.ic_bolt),
    History("History", R.drawable.ic_history)
}

private data class HistoryUiEntry(
    val type: String,
    val text: String,
    val detail: String,
    val time: String,
    val confidence: String,
    val words: List<String>
)

@Composable
fun VoxGestMockupApp() {
    var selectedTab by remember { mutableStateOf(VoxTab.Sign) }
    var signSentence by remember { mutableStateOf("Thank you, I need water") }
    val history = remember {
        mutableStateListOf(
            HistoryUiEntry("Sign", "Thank you, I need water", "spoken aloud via voice output", "2 min ago", "94% avg", listOf("THANKYOU", "WATER")),
            HistoryUiEntry("Listen", "How are you feeling today?", "translated to sign via avatar", "8 min ago", "speech", listOf("HOW", "FEELING", "TODAY")),
            HistoryUiEntry("Phrase", "Please wait", "quick phrase", "15 min ago", "manual", listOf("PLEASE", "WAIT")),
            HistoryUiEntry("Sign", "Hello, please stop", "spoken aloud via voice output", "32 min ago", "88% avg", listOf("HELLO", "STOP")),
            HistoryUiEntry("Listen", "Do you need a doctor?", "translated to sign via avatar", "1 hr ago", "speech", listOf("DOCTOR", "NEED"))
        )
    }
    val context = LocalContext.current
    val tts = remember {
        TextToSpeech(context) { }
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = CardBg,
            onSurface = TextMain
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                HtmlBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .padding(innerPadding)
            ) {
                HtmlStatusBar()
                HtmlTopBar()
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        VoxTab.Sign -> SignScreen(
                            sentence = signSentence,
                            onSpeak = {
                                speak(tts, signSentence)
                                history.add(0, HistoryUiEntry("Sign", signSentence, "spoken aloud via voice output", "now", "94% avg", listOf("THANKYOU", "WATER")))
                            },
                            onClear = { signSentence = "" }
                        )
                        VoxTab.Listen -> ListenScreen(
                            onHistory = { text ->
                                history.add(0, HistoryUiEntry("Listen", text, "translated to sign via avatar", "now", "speech", text.uppercase(Locale.US).split(" ").take(3)))
                            }
                        )
                        VoxTab.Phrases -> PhrasesScreen(
                            onPhrase = { phrase ->
                                speak(tts, phrase)
                                history.add(0, HistoryUiEntry("Phrase", phrase, "quick phrase", "now", "manual", phrase.uppercase(Locale.US).split(" ").take(3)))
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
private fun HtmlStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 22.dp, end = 22.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:41", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                listOf(4, 7, 10, 13).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TextMain)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(11.dp)
                    .border(1.5.dp, TextMuted, RoundedCornerShape(3.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Accent)
                )
            }
        }
    }
}

@Composable
private fun HtmlTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vox", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Text("Gest", color = Accent, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconSquare(R.drawable.ic_filter_list, "Settings")
            IconSquare(R.drawable.ic_warning, "Alerts")
        }
    }
}

@Composable
private fun IconSquare(@DrawableRes icon: Int, description: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        VoxIcon(icon, description, TextMuted, Modifier.size(17.dp))
    }
}

@Composable
private fun SignScreen(
    sentence: String,
    onSpeak: () -> Unit,
    onClear: () -> Unit
) {
    ScreenScroll {
        CameraCard()
        SectionLabel("RECOGNIZED SENTENCE")
        HtmlSentenceCard(sentence = sentence.ifBlank { "Ready for accepted signs" }, onSpeak = onSpeak, onClear = onClear)
        ConfidenceBars()
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniModeButton("Switch to Letters", R.drawable.ic_grid, Modifier.weight(1f))
            MiniModeButton("Phrases Mode", R.drawable.ic_bolt, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun CameraCard() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Accent.copy(alpha = 0.08f), radius = size.minDimension * 0.38f, center = Offset(size.width * 0.5f, size.height * 0.46f))
            val palm = Offset(size.width * 0.50f, size.height * 0.46f)
            val wrist = Offset(size.width * 0.50f, size.height * 0.68f)
            drawLine(Accent.copy(alpha = 0.16f), wrist, palm, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            repeat(5) { i ->
                val dx = (i - 2) * 13.dp.toPx()
                val tip = Offset(palm.x + dx, size.height * (0.18f + i * 0.02f))
                drawLine(Accent.copy(alpha = 0.18f), palm, tip, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Accent.copy(alpha = 0.26f), 3.dp.toPx(), tip)
            }
            drawCircle(Accent.copy(alpha = 0.24f), 4.dp.toPx(), wrist)
        }
        ScanLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlinkDot(Red)
                MonoText("LIVE", color = TextMuted, size = 10)
            }
            PillText("WORDS MODE", accent = Accent)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatChip("HAND", "RIGHT", Teal, Modifier.weight(1f))
            StatChip("FRAMES", "26/30", Accent, Modifier.weight(1f))
            StatChip("CONF", "94%", Accent, Modifier.weight(1f))
            StatChip("FPS", "29", TextMuted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScanLine() {
    val transition = rememberInfiniteTransition(label = "scan")
    val y by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan-y"
    )
    Canvas(Modifier.fillMaxSize()) {
        val top = size.height * y
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Accent.copy(alpha = 0.9f), Color.Transparent)),
            start = Offset(0f, top),
            end = Offset(size.width, top),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun HtmlSentenceCard(sentence: String, onSpeak: () -> Unit, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxSize()
                .background(Accent)
        )
        Column(Modifier.padding(15.dp)) {
            Text(sentence, color = TextMain, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Light)
            MonoText("3 signs · 94% avg confidence", color = TextDim, size = 11, modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSpeak,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                ) {
                    VoxIcon(R.drawable.ic_volume_up, "Speak aloud", Bg, Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Speak aloud", color = Bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onClear() },
                    shape = RoundedCornerShape(8.dp),
                    color = CardHi,
                    border = BorderStroke(0.5.dp, BorderStrong)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        VoxIcon(R.drawable.ic_cancel, "Clear", TextMuted, Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBars() {
    val bars = listOf(
        Triple("THANK YOU", 94, Accent),
        Triple("WATER", 88, Accent2),
        Triple("HELLO", 6, TextFaint),
        Triple("YES", 2, TextFaint)
    )
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        bars.forEach { (label, pct, color) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonoText(label, color = TextDim, size = 10, modifier = Modifier.width(76.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CardHi)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(color)
                    )
                }
                MonoText("$pct%", color = TextDim, size = 10, modifier = Modifier.width(30.dp))
            }
        }
    }
}

@Composable
private fun MiniModeButton(label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
            .clickable {}
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        VoxIcon(icon, label, TextDim, Modifier.size(15.dp))
        Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ListenScreen(onHistory: (String) -> Unit) {
    var listening by remember { mutableStateOf(false) }
    var heardText by remember { mutableStateOf("How are you feeling today?") }
    var avatarState by remember { mutableStateOf(AvatarPlaybackState()) }
    val avatarController = remember {
        AvatarController { avatarState = it }
    }
    DisposableEffect(Unit) {
        onDispose { avatarController.detach() }
    }

    fun playText() {
        avatarController.playTextAsSigns(heardText)
        onHistory(heardText)
    }

    ScreenScroll {
        ListenHero(
            listening = listening,
            heardText = heardText,
            onMic = {
                listening = !listening
                avatarController.setListening(listening)
                if (listening) heardText = "How are you feeling today?"
            }
        )
        AvatarHtmlCard(
            avatarController = avatarController,
            avatarState = avatarState,
            onDebugWord = { word ->
                if (!avatarController.playWord(word)) {
                    avatarState = AvatarPlaybackState(label = "Ignored", detail = "NOTHING is no-output")
                }
            }
        )
        TranscriptCard()
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HtmlButton(
                label = if (listening) "Listening..." else "Start listening",
                icon = R.drawable.ic_mic,
                modifier = Modifier.weight(1f),
                onClick = {
                    listening = !listening
                    avatarController.setListening(listening)
                }
            )
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        listening = false
                        avatarController.stop()
                    },
                shape = RoundedCornerShape(8.dp),
                color = Red.copy(alpha = 0.10f),
                border = BorderStroke(0.5.dp, Red.copy(alpha = 0.28f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(R.drawable.ic_cancel, "Stop", Red, Modifier.size(18.dp))
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HtmlButton("Replay avatar", R.drawable.ic_replay, Modifier.weight(1f), onClick = { avatarController.replay() })
            HtmlAccentButton("Play signs", R.drawable.ic_play_arrow, Modifier.weight(1f), onClick = { playText() })
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ListenHero(
    listening: Boolean,
    heardText: String,
    onMic: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MonoText(if (listening) "LISTENING" else "TAP TO LISTEN", color = TextDim, size = 10)
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 10.dp)
                .size(92.dp)
                .clickable { onMic() },
            contentAlignment = Alignment.Center
        ) {
            MicRing(54, 0)
            MicRing(70, 400)
            MicRing(86, 800)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentDim)
                    .border(1.5.dp, Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                VoxIcon(R.drawable.ic_mic, "Mic", Accent, Modifier.size(20.dp))
            }
        }
        Waveform()
        Text("“$heardText”", color = TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun MicRing(size: Int, delay: Int) {
    val transition = rememberInfiniteTransition(label = "ring-$size")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = delay), RepeatMode.Restart),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = delay), RepeatMode.Restart),
        label = "scale"
    )
    Canvas(Modifier.size(size.dp)) {
        drawCircle(
            color = Accent.copy(alpha = alpha),
            radius = size.dp.toPx() * 0.5f * scale,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
private fun Waveform() {
    val heights = listOf(7, 15, 23, 18, 11, 21, 15, 8, 17, 10)
    Row(
        modifier = Modifier
            .height(26.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { index, height ->
            val transition = rememberInfiniteTransition(label = "wave-$index")
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800, delayMillis = index * 80), RepeatMode.Reverse),
                label = "scale"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((height * scale).dp.coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent)
            )
        }
    }
}

@Composable
private fun AvatarHtmlCard(
    avatarController: AvatarController,
    avatarState: AvatarPlaybackState,
    onDebugWord: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(CardHi)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawOval(
                    color = Accent.copy(alpha = 0.12f),
                    topLeft = Offset(size.width * 0.5f - 80.dp.toPx(), size.height - 30.dp.toPx()),
                    size = Size(160.dp.toPx(), 30.dp.toPx())
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Bg.copy(alpha = 0.70f))
                        .border(0.5.dp, BorderStrong, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    BlinkDot(Accent)
                    MonoText(avatarState.label.uppercase(Locale.US), color = Accent, size = 10)
                }
                PillText(avatarState.currentWord.ifBlank { "READY" }, Accent)
            }
            AndroidView(
                factory = { viewContext -> AvatarView(viewContext).also { avatarController.attach(it) } },
                update = {
                    it.contentDescription = "Avatar signing: ${avatarState.currentWord.ifBlank { avatarState.label }}"
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(198.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoText(avatarState.detail.ifBlank { "Signing: ready" }, color = TextDim, size = 11)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(CardTop)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (avatarState.isPlaying) 0.80f else 0.35f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Accent)
                )
            }
        }
        if (BuildConfig.DEBUG) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("HELLO", "THANKYOU", "WATER", "EAT", "NOTHING").forEach { word ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clickable { onDebugWord(word) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (word == "NOTHING") CardHi else AccentDim,
                        border = BorderStroke(0.5.dp, if (word == "NOTHING") BorderStrong else Accent.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (word == "THANKYOU") "THANK" else word,
                                color = if (word == "NOTHING") TextDim else Accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard() {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TranscriptLine("YOU", "How are you feeling today?", TextDim)
        DividerLine()
        TranscriptLine("SIGN", "I am fine, thank you", Accent)
        DividerLine()
        TranscriptLine("APP", "Avatar sequence: HELLO · WATER · EAT", TextDim)
    }
}

@Composable
private fun TranscriptLine(who: String, said: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        MonoText(who, color = color, size = 10, modifier = Modifier.width(38.dp))
        Text(said, color = if (color == Accent) TextMain else TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun PhrasesScreen(onPhrase: (String) -> Unit) {
    ScreenScroll {
        SearchBox()
        PhraseSectionTitle("EMERGENCY", R.drawable.ic_warning, Red)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EmergencyCard("I need help", "Alert nearby person", Modifier.weight(1f), onClick = { onPhrase("I need help") })
            EmergencyCard("Call a doctor", "Medical request", Modifier.weight(1f), onClick = { onPhrase("Call a doctor") })
        }
        PhraseSectionTitle("QUICK PHRASES", R.drawable.ic_bolt, Amber)
        Column(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("I need water", "Request a drink", R.drawable.ic_water_drop),
                Triple("Please wait", "Ask for patience", R.drawable.ic_clock),
                Triple("Thank you", "Show gratitude", R.drawable.ic_hand_gesture),
                Triple("Yes", "Confirm", R.drawable.ic_check_circle),
                Triple("No", "Decline", R.drawable.ic_no_circle),
                Triple("I want to eat", "Food request", R.drawable.ic_bolt)
            ).forEach { (word, sub, icon) ->
                PhraseItem(word, sub, icon, onClick = { onPhrase(word) })
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
                .clickable {}
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoxIcon(R.drawable.ic_plus, "Add custom phrase", TextDim, Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Add custom phrase", color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SearchBox() {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VoxIcon(R.drawable.ic_search, "Search", TextDim, Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text("Search phrases...", color = TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun PhraseSectionTitle(title: String, @DrawableRes icon: Int, color: Color) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VoxIcon(icon, title, color, Modifier.size(13.dp))
        MonoText(title, color = TextDim, size = 10)
    }
}

@Composable
private fun EmergencyCard(text: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Red.copy(alpha = 0.07f))
            .border(0.5.dp, Red.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        VoxIcon(R.drawable.ic_warning, text, Red, Modifier.size(18.dp))
        Text(text, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
        Text(sub, color = TextDim, fontSize = 10.sp)
    }
}

@Composable
private fun PhraseItem(text: String, sub: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentDim),
            contentAlignment = Alignment.Center
        ) {
            VoxIcon(icon, text, Accent, Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HistoryScreen(entries: List<HistoryUiEntry>) {
    var filter by remember { mutableStateOf("All") }
    val shown = if (filter == "All" || filter == "Today") entries else entries.filter { it.type == filter.dropLastWhile { ch -> ch == 's' } }
    ScreenScroll {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .border(0.5.dp, Border, RoundedCornerShape(8.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryStat(entries.size.toString(), "TOTAL")
            VerticalRule()
            SummaryStat(entries.count { it.type == "Sign" }.toString(), "SIGN")
            VerticalRule()
            SummaryStat(entries.count { it.type == "Listen" }.toString(), "LISTEN")
            VerticalRule()
            SummaryStat(entries.count { it.type == "Phrase" }.toString(), "PHRASE")
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Sign", "Listen", "Phrases", "Today").forEach { item ->
                FilterChip(label = item, selected = filter == item, onClick = { filter = item })
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            shown.forEach { entry -> HistoryHtmlCard(entry) }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        MonoText(label, color = TextDim, size = 10)
    }
}

@Composable
private fun VerticalRule() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(34.dp)
            .background(Border)
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(30.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) AccentDim else CardBg,
        border = BorderStroke(0.5.dp, if (selected) Accent else BorderStrong)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(label, color = if (selected) Accent else TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HistoryHtmlCard(entry: HistoryUiEntry) {
    val tint = when (entry.type) {
        "Sign" -> Accent
        "Listen" -> Blue
        else -> Purple
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tint.copy(alpha = 0.10f))
                    .border(0.5.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoxIcon(if (entry.type == "Listen") R.drawable.ic_mic else if (entry.type == "Phrase") R.drawable.ic_bolt else R.drawable.ic_hand_gesture, entry.type, tint, Modifier.size(10.dp))
                MonoText(entry.type, color = tint, size = 10)
            }
            MonoText(entry.time, color = TextDim, size = 10)
        }
        Text(
            "\"${entry.text}\" — ${entry.detail}",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 7.dp)
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.words.take(3).forEach { WordChip(it) }
            }
            MonoText(entry.confidence, color = TextDim, size = 10)
        }
    }
}

@Composable
private fun WordChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CardHi)
            .border(0.5.dp, Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = TextDim, fontSize = 10.sp, maxLines = 1)
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
private fun SectionLabel(text: String) {
    MonoText(text, color = TextDim, size = 10, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 7.dp))
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Bg.copy(alpha = 0.80f))
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        MonoText(label, color = TextDim, size = 9)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillText(text: String, accent: Color) {
    Text(
        text = text,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CardHi)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun BlinkDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun HtmlButton(label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, BorderStrong, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, Accent, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HtmlAccentButton(label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Accent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoxIcon(icon, label, Bg, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Border)
    )
}

@Composable
private fun MonoText(text: String, color: Color, size: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HtmlBottomNav(
    selectedTab: VoxTab,
    onTabSelected: (VoxTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .border(0.5.dp, Border)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp)
    ) {
        Row {
            VoxTab.values().forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) CardBg else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VoxIcon(tab.icon, tab.label, if (selected) Accent else TextFaint, Modifier.size(20.dp))
                    Text(tab.label, color = if (selected) Accent else TextDim, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
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
