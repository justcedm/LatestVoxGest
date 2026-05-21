package com.voxgest.dryrun.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

private val PrimaryTeal = Color(0xFF00897B)
private val PrimaryDark = Color(0xFF00695C)
private val BackgroundLight = Color(0xFFF0F4F4)
private val SurfaceWhite = Color.White
private val TextPrimary = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF546E7A)
private val TextMuted = Color(0xFF90A4AE)
private val TrackingGreen = Color(0xFF00C853)
private val WordAmber = Color(0xFFFFB300)
private val EmergencyOrange = Color(0xFFF57C00)
private val MedicalBg = Color(0xFFE0F2F1)
private val EmergencyBg = Color(0xFFFFF3E0)
private val Divider = Color(0xFFECEFF1)
private val NoRed = Color(0xFFE53935)
private val YesGreen = Color(0xFF43A047)

private enum class VoxTab(
    val label: String,
    @DrawableRes val icon: Int
) {
    Sign("Sign", R.drawable.ic_hand_gesture),
    Listen("Listen", R.drawable.ic_mic),
    Phrases("Phrases", R.drawable.ic_chat),
    History("History", R.drawable.ic_history)
}

@Composable
fun VoxGestMockupApp() {
    var selectedTab by remember { mutableStateOf(VoxTab.Sign) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryTeal,
            onPrimary = SurfaceWhite,
            background = BackgroundLight,
            surface = SurfaceWhite,
            onSurface = TextPrimary
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = BackgroundLight,
            bottomBar = {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundLight)
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    VoxTab.Sign -> SignScreen()
                    VoxTab.Listen -> ListenScreen()
                    VoxTab.Phrases -> PhrasesScreen()
                    VoxTab.History -> HistoryScreen()
                }
            }
        }
    }
}

@Composable
private fun SignScreen() {
    ScreenColumn {
        TopBar(title = "SIGN", trailingIcons = listOf(R.drawable.ic_settings))
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CameraPreviewCard()
            CurrentWordCard()
            SentenceCard()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = "Speak",
                    icon = R.drawable.ic_volume_up,
                    tint = PrimaryTeal,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "Delete",
                    icon = R.drawable.ic_delete_outline,
                    tint = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "Clear",
                    icon = R.drawable.ic_cancel,
                    tint = NoRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ListenScreen() {
    var listening by remember { mutableStateOf(false) }
    var avatarState by remember { mutableStateOf(AvatarPlaybackState()) }
    var transcript by remember { mutableStateOf("Hello, I need water.") }
    val avatarController = remember {
        AvatarController { avatarState = it }
    }

    DisposableEffect(Unit) {
        onDispose { avatarController.detach() }
    }

    fun setListening(active: Boolean) {
        listening = active
        avatarController.setListening(active)
    }

    fun playTranscript() {
        setListening(false)
        avatarController.playTextAsSigns(transcript)
    }

    ScreenColumn {
        TopBar(title = "LISTEN", trailingIcons = listOf(R.drawable.ic_settings))
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpeechTranscriptCard(
                transcript = transcript,
                listening = listening,
                onMicTap = {
                    setListening(!listening)
                    if (!listening) {
                        transcript = "Hello, I need water."
                    }
                }
            )
            AvatarCard(
                avatarController = avatarController,
                avatarState = avatarState,
                listening = listening,
                onDebugWord = { word ->
                    if (!avatarController.playWord(word)) {
                        avatarState = AvatarPlaybackState(
                            label = "Ignored",
                            detail = "NOTHING is no-output",
                            currentWord = "",
                            isPlaying = false
                        )
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { avatarController.replay() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, PrimaryTeal)
                ) {
                    VoxIcon(R.drawable.ic_replay, "Replay", PrimaryTeal, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Replay", color = PrimaryTeal, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { playTranscript() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    VoxIcon(R.drawable.ic_play_arrow, "Play signs", SurfaceWhite, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Signs", color = SurfaceWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PhrasesScreen() {
    ScreenColumn {
        TopBar(title = "PHRASES", trailingIcons = listOf(R.drawable.ic_search))
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickPhrasesHero()
            CategoryChips()
            PhraseGrid()
        }
    }
}

@Composable
private fun HistoryScreen() {
    ScreenColumn {
        TopBar(title = "HISTORY", trailingIcons = listOf(R.drawable.ic_search, R.drawable.ic_filter_list))
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateHeader("Today")
            HistoryCard("Sign", "HELLO, I NEED WATER", "10:45 AM", "Spoken", R.drawable.ic_hand_gesture, PrimaryTeal)
            HistoryCard("Speech", "Hello, how can I help you?", "10:42 AM", "Shown in signs", R.drawable.ic_waveform, WordAmber)
            HistoryCard("Phrase", "I need help", "10:40 AM", "Shown in signs", R.drawable.ic_chat, EmergencyOrange)
            DateHeader("Yesterday")
            HistoryCard("Sign", "THANK YOU", "08:15 PM", "Spoken", R.drawable.ic_hand_gesture, PrimaryTeal)
            HistoryCard("Speech", "Please wait a moment.", "07:50 PM", "Shown in signs", R.drawable.ic_waveform, WordAmber)
            HistoryCard("Phrase", "Call a doctor", "07:30 PM", "Shown in signs", R.drawable.ic_chat, EmergencyOrange)
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A3A))
            ) {
                VoxIcon(R.drawable.ic_delete_outline, "Clear history", SurfaceWhite, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear All History", color = SurfaceWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun TopBar(title: String, trailingIcons: List<Int>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SurfaceWhite)
            .border(width = 0.5.dp, color = Divider)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            trailingIcons.forEach { icon ->
                IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                    VoxIcon(icon, null, TextSecondary, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF8F9999), Color(0xFF303636))
                )
            )
            val centerX = size.width * 0.53f
            val headRadius = size.width * 0.12f
            drawCircle(Color(0xFFE6B99E), headRadius, Offset(centerX, size.height * 0.28f))
            drawRoundRect(
                color = Color(0xFF15191C),
                topLeft = Offset(centerX - size.width * 0.18f, size.height * 0.42f),
                size = Size(size.width * 0.36f, size.height * 0.42f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(26f, 26f)
            )
            val wrist = Offset(size.width * 0.28f, size.height * 0.70f)
            val palm = Offset(size.width * 0.28f, size.height * 0.48f)
            drawLine(PrimaryTeal, wrist, palm, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            repeat(5) { index ->
                val spread = (index - 2) * size.width * 0.035f
                val tip = Offset(palm.x + spread, size.height * (0.26f + index * 0.01f))
                val fingerColor = listOf(WordAmber, TrackingGreen, Color(0xFF2196F3), Color(0xFF9C27B0), NoRed)[index]
                drawLine(fingerColor, palm, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(fingerColor, 4.dp.toPx(), tip)
            }
            drawCircle(PrimaryTeal, 6.dp.toPx(), wrist)
            val cornerColor = TrackingGreen
            val bracket = 24.dp.toPx()
            val pad = 28.dp.toPx()
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(pad, pad), Offset(pad + bracket, pad), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(pad, pad), Offset(pad, pad + bracket), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad - bracket, pad), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + bracket), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad + bracket, size.height - pad), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - bracket), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - bracket, size.height - pad), strokeWidth = stroke.width)
            drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - bracket), strokeWidth = stroke.width)
        }
        Row(
            modifier = Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TextPrimary.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TrackingGreen)
            )
            Spacer(Modifier.width(8.dp))
            Text("Tracking Hand", color = SurfaceWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(36.dp),
            shape = CircleShape,
            color = SurfaceWhite.copy(alpha = 0.85f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(R.drawable.ic_wb_sunny, "Light", TextPrimary, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CurrentWordCard() {
    VoxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelText("CURRENT WORD", modifier = Modifier.weight(1f))
            VoxIcon(R.drawable.ic_volume_up, "Speak current word", PrimaryTeal, Modifier.size(18.dp))
        }
        Text(
            text = "HELLO",
            color = TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun SentenceCard() {
    VoxCard {
        LabelText("SENTENCE")
        Text(
            text = "HELLO, I NEED WATER",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoxIcon(R.drawable.ic_volume_up, "Speak sentence", WordAmber, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tap to speak", color = WordAmber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    @DrawableRes icon: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable {},
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VoxIcon(icon, label, tint, Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SpeechTranscriptCard(
    transcript: String,
    listening: Boolean,
    onMicTap: () -> Unit
) {
    VoxCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelText("SPEECH TRANSCRIPT", modifier = Modifier.weight(1f))
            VoxIcon(R.drawable.ic_volume_up, "Speak transcript", TextSecondary, Modifier.size(18.dp))
        }
        Text(
            text = transcript,
            color = TextPrimary,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WaveBars(reverse = true)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .size(64.dp)
                    .clickable { onMicTap() },
                shape = CircleShape,
                color = if (listening) PrimaryDark else PrimaryTeal,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(R.drawable.ic_mic, "Microphone", SurfaceWhite, Modifier.size(28.dp))
                }
            }
            WaveBars(reverse = false)
        }
        Text(
            text = if (listening) "Listening..." else "Tap mic to listen",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Text(
            text = "Speak clearly",
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WaveBars(reverse: Boolean) {
    val heights = if (reverse) listOf(8, 14, 22, 30, 22, 14, 8) else listOf(8, 14, 22, 30, 22, 14, 8)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryTeal.copy(alpha = 0.28f))
            )
        }
    }
}

@Composable
private fun AvatarCard(
    avatarController: AvatarController,
    avatarState: AvatarPlaybackState,
    listening: Boolean,
    onDebugWord: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabelText("AVATAR", modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (listening) "Listening..." else avatarState.label,
                        color = PrimaryTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFE8F5E9), SurfaceWhite)))
            ) {
                AndroidView(
                    factory = { viewContext ->
                        AvatarView(viewContext).also { avatarController.attach(it) }
                    },
                    update = {
                        it.contentDescription = "Avatar signing: ${avatarState.currentWord.ifBlank { avatarState.label }}"
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(150.dp)
                )
                Text(
                    text = avatarState.detail,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )
            }
            if (BuildConfig.DEBUG) {
                DebugAvatarWordChips(onDebugWord = onDebugWord)
            }
        }
    }
}

@Composable
private fun DebugAvatarWordChips(onDebugWord: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("HELLO", "THANKYOU", "WATER", "EAT", "NOTHING").forEach { word ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clickable { onDebugWord(word) },
                shape = RoundedCornerShape(17.dp),
                color = if (word == "NOTHING") BackgroundLight else MedicalBg,
                border = BorderStroke(1.dp, if (word == "NOTHING") TextMuted.copy(alpha = 0.35f) else PrimaryTeal.copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (word == "THANKYOU") "THANK" else word,
                        color = if (word == "NOTHING") TextMuted else PrimaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendlyAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(140.dp)) {
        drawCircle(Color(0xFFE7BFA7), radius = 34.dp.toPx(), center = Offset(size.width / 2f, 42.dp.toPx()))
        drawArc(
            color = Color(0xFF2D1E1A),
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = true,
            topLeft = Offset(size.width / 2f - 40.dp.toPx(), 5.dp.toPx()),
            size = Size(80.dp.toPx(), 54.dp.toPx())
        )
        drawCircle(TextPrimary, 3.dp.toPx(), Offset(size.width / 2f - 12.dp.toPx(), 42.dp.toPx()))
        drawCircle(TextPrimary, 3.dp.toPx(), Offset(size.width / 2f + 12.dp.toPx(), 42.dp.toPx()))
        drawArc(
            color = TextPrimary,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width / 2f - 12.dp.toPx(), 48.dp.toPx()),
            size = Size(24.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawRoundRect(
            color = PrimaryTeal,
            topLeft = Offset(size.width / 2f - 30.dp.toPx(), 76.dp.toPx()),
            size = Size(60.dp.toPx(), 56.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )
        drawLine(PrimaryDark, Offset(48.dp.toPx(), 88.dp.toPx()), Offset(26.dp.toPx(), 64.dp.toPx()), 10.dp.toPx(), StrokeCap.Round)
        drawLine(PrimaryDark, Offset(90.dp.toPx(), 88.dp.toPx()), Offset(104.dp.toPx(), 68.dp.toPx()), 10.dp.toPx(), StrokeCap.Round)
        drawRoundRect(
            color = Color(0xFFE7BFA7),
            topLeft = Offset(15.dp.toPx(), 45.dp.toPx()),
            size = Size(24.dp.toPx(), 32.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFE7BFA7),
            topLeft = Offset(96.dp.toPx(), 56.dp.toPx()),
            size = Size(24.dp.toPx(), 32.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
    }
}

@Composable
private fun QuickPhrasesHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(PrimaryTeal, PrimaryDark)))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Quick Phrases", color = SurfaceWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tap a phrase to show it in sign or speak it out.",
                color = SurfaceWhite.copy(alpha = 0.86f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(210.dp)
            )
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite.copy(alpha = if (index == 0) 1f else 0.42f))
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(56.dp),
            shape = CircleShape,
            color = SurfaceWhite.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                VoxIcon(R.drawable.ic_bolt, "Quick phrases", SurfaceWhite, Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun CategoryChips() {
    val chips = listOf(
        Triple("Emergency", R.drawable.ic_warning, EmergencyOrange),
        Triple("Medical", R.drawable.ic_medical, PrimaryTeal),
        Triple("Daily", R.drawable.ic_wb_sunny, TextSecondary),
        Triple("Conversation", R.drawable.ic_chat, TextSecondary)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, color) ->
                    val bg = if (label == "Emergency") EmergencyBg else if (label == "Medical") MedicalBg else BackgroundLight
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bg)
                            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        VoxIcon(icon, label, color, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhraseGrid() {
    val phrases = listOf(
        Phrase("I need help", R.drawable.ic_warning, EmergencyOrange),
        Phrase("Call a doctor", R.drawable.ic_medical, PrimaryTeal),
        Phrase("I need water", R.drawable.ic_water_drop, Color(0xFF2196F3)),
        Phrase("Stop", R.drawable.ic_hand_gesture, NoRed),
        Phrase("Please wait", R.drawable.ic_clock, WordAmber),
        Phrase("Thank you", R.drawable.ic_hand_gesture, Color(0xFF9C27B0)),
        Phrase("Yes", R.drawable.ic_check_circle, YesGreen),
        Phrase("No", R.drawable.ic_no_circle, NoRed)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        phrases.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { phrase ->
                    PhraseCard(phrase, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhraseCard(phrase: Phrase, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable {},
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = phrase.color.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(phrase.icon, phrase.label, phrase.color, Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = phrase.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DateHeader(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun HistoryCard(
    type: String,
    text: String,
    time: String,
    meta: String,
    @DrawableRes icon: Int,
    tint: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    VoxIcon(icon, type, tint, Modifier.size(22.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(time, color = TextMuted, fontSize = 10.sp)
                }
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    VoxIcon(R.drawable.ic_play_arrow, meta, TextMuted, Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(meta, color = TextMuted, fontSize = 10.sp)
                }
            }
            VoxIcon(R.drawable.ic_play_arrow, "Replay", PrimaryDark, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            VoxIcon(R.drawable.ic_more_vert, "More", TextMuted, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun VoxCard(
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            content = content
        )
    }
}

@Composable
private fun LabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        modifier = modifier
    )
}

@Composable
private fun BottomNavBar(
    selectedTab: VoxTab,
    onTabSelected: (VoxTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SurfaceWhite)
            .border(width = 1.dp, color = Divider),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VoxTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) PrimaryTeal else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = if (isSelected) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                VoxIcon(
                    icon = tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) SurfaceWhite else TextMuted,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(if (isSelected) 6.dp else 4.dp))
                Text(
                    text = tab.label,
                    color = if (isSelected) SurfaceWhite else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
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

private data class Phrase(
    val label: String,
    @DrawableRes val icon: Int,
    val color: Color
)
