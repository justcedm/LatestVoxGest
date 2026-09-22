package com.voxgest.dryrun.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VoxGestSplashScreen(reducedMotion: Boolean) {
    var started by remember { mutableStateOf(false) }
    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.9f,
        animationSpec = tween(if (reducedMotion) 1 else 650),
        label = "brand-scale"
    )
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0.08f,
        animationSpec = tween(if (reducedMotion) 1 else 950),
        label = "splash-progress"
    )
    LaunchedEffect(Unit) { started = true }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primary)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(secondary.copy(alpha = 0.32f), radius = size.minDimension * 0.72f, center = Offset(size.width * 0.08f, size.height * 0.92f))
            drawCircle(tertiary.copy(alpha = 0.28f), radius = size.minDimension * 0.60f, center = Offset(size.width * 0.98f, size.height * 0.98f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.96f, size.height * 0.08f))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 38.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VoxGestBrandMark(Modifier.size(132.dp).scale(markScale))
            Spacer(Modifier.height(24.dp))
            Text("VoxGest", color = onPrimary, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Signs Connect People",
                color = onPrimary.copy(alpha = 0.84f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(70.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(onPrimary.copy(alpha = 0.18f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(secondary)
                )
            }
            Text(
                "Building a more inclusive Philippines",
                color = onPrimary.copy(alpha = 0.72f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
internal fun VoxGestBrandMark(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(modifier = modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(21.dp)) {
            val white = onPrimary
            val yellow = secondary
            val center = Offset(size.width * 0.52f, size.height * 0.56f)
            drawCircle(white, size.minDimension * 0.23f, center)
            drawCircle(primary, size.minDimension * 0.095f, center)
            val fingerX = listOf(0.25f, 0.38f, 0.51f, 0.64f)
            fingerX.forEachIndexed { index, x ->
                drawLine(
                    white,
                    start = Offset(size.width * x, size.height * (0.50f - index * 0.025f)),
                    end = Offset(size.width * (x + 0.04f), size.height * (0.13f + index * 0.015f)),
                    strokeWidth = size.minDimension * 0.105f,
                    cap = StrokeCap.Round
                )
            }
            repeat(7) { index ->
                val angle = Math.toRadians((-72 + index * 24).toDouble())
                val start = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.34f,
                    center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.34f
                )
                val end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.45f,
                    center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.45f
                )
                drawLine(yellow, start, end, strokeWidth = size.minDimension * 0.035f, cap = StrokeCap.Round)
            }
            drawCircle(white, size.minDimension * 0.24f, center, style = Stroke(size.minDimension * 0.02f))
        }
    }
}
