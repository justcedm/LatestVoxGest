package com.voxgest.dryrun.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxgest.dryrun.VoxGestColorStyle

/** Central Modern Filipino Warm Tech visual contract. */
internal object VoxGestDesignTokens {
    val Terracotta = Color(0xFFD96B32)
    val SunGold = Color(0xFFF2B84B)
    val WarmSand = Color(0xFFE8D4BF)
    val SoftCream = Color(0xFFFFF8F2)
    val White = Color(0xFFFFFFFF)
    val DeepCharcoal = Color(0xFF172027)
    val DarkBackground = Color(0xFF111517)
    val DarkElevated = Color(0xFF1B2023)
    val Success = Color(0xFF3F8064)
    val Error = Color(0xFFBA4C42)

    val ScreenHorizontalPadding = 16.dp
    val CardCorner = 20.dp
    val FeatureCorner = 24.dp
    val ControlCorner = 14.dp
    val ChipCorner = 12.dp

    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
}

internal data class VoxGestColorPreview(
    val primary: Color,
    val highlight: Color,
    val warmSurface: Color,
    val support: Color
)

internal fun colorPreviewFor(style: VoxGestColorStyle): VoxGestColorPreview = when (style) {
    VoxGestColorStyle.TERRACOTTA -> VoxGestColorPreview(
        VoxGestDesignTokens.Terracotta,
        VoxGestDesignTokens.SunGold,
        VoxGestDesignTokens.SoftCream,
        VoxGestDesignTokens.WarmSand
    )
    VoxGestColorStyle.SUN_GOLD -> VoxGestColorPreview(
        Color(0xFFA36B00),
        VoxGestDesignTokens.SunGold,
        Color(0xFFFFFAEE),
        Color(0xFFF0DFC0)
    )
    VoxGestColorStyle.WARM_SAND -> VoxGestColorPreview(
        Color(0xFF87583D),
        VoxGestDesignTokens.Terracotta,
        VoxGestDesignTokens.SoftCream,
        VoxGestDesignTokens.WarmSand
    )
    VoxGestColorStyle.FILIPINO_HERITAGE -> VoxGestColorPreview(
        VoxGestDesignTokens.Terracotta,
        VoxGestDesignTokens.SunGold,
        VoxGestDesignTokens.SoftCream,
        VoxGestDesignTokens.WarmSand
    )
}

internal fun voxGestColorScheme(style: VoxGestColorStyle, dark: Boolean): ColorScheme {
    val preview = colorPreviewFor(style)
    return if (dark) {
        darkColorScheme(
            primary = when (style) {
                VoxGestColorStyle.SUN_GOLD -> VoxGestDesignTokens.SunGold
                VoxGestColorStyle.WARM_SAND -> Color(0xFFE3B896)
                else -> Color(0xFFFFA06C)
            },
            onPrimary = VoxGestDesignTokens.DeepCharcoal,
            primaryContainer = when (style) {
                VoxGestColorStyle.SUN_GOLD -> Color(0xFF4B3506)
                VoxGestColorStyle.WARM_SAND -> Color(0xFF473126)
                else -> Color(0xFF5C2D18)
            },
            onPrimaryContainer = Color(0xFFFFE6D8),
            secondary = preview.highlight,
            onSecondary = VoxGestDesignTokens.DeepCharcoal,
            secondaryContainer = Color(0xFF493A22),
            onSecondaryContainer = Color(0xFFFFE4AE),
            tertiary = Color(0xFFE8CDB7),
            onTertiary = VoxGestDesignTokens.DeepCharcoal,
            background = VoxGestDesignTokens.DarkBackground,
            surface = VoxGestDesignTokens.DarkElevated,
            surfaceVariant = Color(0xFF252C30),
            onSurface = Color(0xFFF7F1EC),
            onSurfaceVariant = Color(0xFFD2C7BF),
            outline = Color(0xFF9A8F88),
            outlineVariant = Color(0xFF3B4246),
            error = VoxGestDesignTokens.Error,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = preview.primary,
            onPrimary = when (style) {
                VoxGestColorStyle.SUN_GOLD,
                VoxGestColorStyle.WARM_SAND -> Color.White
                else -> VoxGestDesignTokens.DeepCharcoal
            },
            primaryContainer = when (style) {
                VoxGestColorStyle.SUN_GOLD -> Color(0xFFFFE8B4)
                VoxGestColorStyle.WARM_SAND -> VoxGestDesignTokens.WarmSand
                else -> Color(0xFFFFE6D7)
            },
            onPrimaryContainer = VoxGestDesignTokens.DeepCharcoal,
            secondary = preview.highlight,
            onSecondary = VoxGestDesignTokens.DeepCharcoal,
            secondaryContainer = Color(0xFFFFEDC9),
            onSecondaryContainer = VoxGestDesignTokens.DeepCharcoal,
            tertiary = VoxGestDesignTokens.WarmSand,
            onTertiary = VoxGestDesignTokens.DeepCharcoal,
            background = preview.warmSurface,
            surface = VoxGestDesignTokens.White,
            surfaceVariant = Color(0xFFF5ECE4),
            onSurface = VoxGestDesignTokens.DeepCharcoal,
            onSurfaceVariant = Color(0xFF5E666B),
            outline = Color(0xFF80756E),
            outlineVariant = Color(0xFFE6DCD4),
            error = VoxGestDesignTokens.Error,
            onError = Color.White
        )
    }
}

internal val VoxGestTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

internal data class VoxGestBottomNavItem(
    val key: String,
    val label: String,
    @DrawableRes val icon: Int
)

@Composable
internal fun VoxGestCapstoneTopBar(
    sectionTitle: String,
    @DrawableRes trailingIcon: Int,
    @DrawableRes secondTrailingIcon: Int? = null,
    compact: Boolean = false,
    onBrandLongPress: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 64.dp else 72.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val showSlogan = !compact && secondTrailingIcon == null && maxWidth >= 390.dp
        FilipinoHeritageBackdrop(showSun = showSlogan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderBrandMark(
                modifier = Modifier
                    .size(if (compact) 42.dp else 46.dp)
                    .pointerInput(onBrandLongPress) {
                        detectTapGestures(onLongPress = { onBrandLongPress?.invoke() })
                    }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = "VoxGest",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (compact) 18.sp else 20.sp,
                    lineHeight = if (compact) 19.sp else 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Signs Connect People",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = if (compact) 9.sp else 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sectionTitle.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showSlogan) {
                Column(
                    modifier = Modifier
                        .width(118.dp)
                        .padding(end = 10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Senyas para sa Mas Malawak na Bukas",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 2
                    )
                    Text(
                        text = "Signs for a Brighter Tomorrow",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 6.sp,
                        lineHeight = 7.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondTrailingIcon?.let { icon ->
                    CapstoneIconButton(icon = icon, description = "$sectionTitle action", onClick = null)
                }
                CapstoneIconButton(
                    icon = trailingIcon,
                    description = "$sectionTitle settings",
                    onClick = onTrailingClick
                )
            }
        }
    }
}

@Composable
private fun FilipinoHeritageBackdrop(showSun: Boolean) {
    val landscape = MaterialTheme.colorScheme.primary.copy(alpha = 0.065f)
    val wave = MaterialTheme.colorScheme.secondary.copy(alpha = 0.075f)
    val sun = Color(0xFFF4B71B).copy(alpha = if (showSun) 0.20f else 0.12f)
    Canvas(Modifier.fillMaxSize()) {
        val mountainPath = Path().apply {
            moveTo(0f, size.height * 0.80f)
            lineTo(size.width * 0.18f, size.height * 0.48f)
            lineTo(size.width * 0.32f, size.height * 0.72f)
            lineTo(size.width * 0.47f, size.height * 0.40f)
            lineTo(size.width * 0.67f, size.height * 0.76f)
            lineTo(size.width * 0.83f, size.height * 0.52f)
            lineTo(size.width, size.height * 0.73f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(mountainPath, landscape)

        val wavePath = Path().apply {
            moveTo(0f, size.height * 0.82f)
            cubicTo(
                size.width * 0.20f,
                size.height * 0.66f,
                size.width * 0.34f,
                size.height * 0.98f,
                size.width * 0.55f,
                size.height * 0.82f
            )
            cubicTo(
                size.width * 0.72f,
                size.height * 0.68f,
                size.width * 0.86f,
                size.height * 0.96f,
                size.width,
                size.height * 0.78f
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(wavePath, wave)

        val sunCenter = Offset(size.width * 0.69f, size.height * 0.31f)
        val radius = size.height * 0.095f
        drawCircle(sun, radius, sunCenter)
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val start = Offset(
                sunCenter.x + kotlin.math.cos(angle).toFloat() * radius * 1.35f,
                sunCenter.y + kotlin.math.sin(angle).toFloat() * radius * 1.35f
            )
            val end = Offset(
                sunCenter.x + kotlin.math.cos(angle).toFloat() * radius * 1.95f,
                sunCenter.y + kotlin.math.sin(angle).toFloat() * radius * 1.95f
            )
            drawLine(sun, start, end, strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun HeaderBrandMark(modifier: Modifier = Modifier) {
    val brandColor = MaterialTheme.colorScheme.primary
    val onBrandColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(brandColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val white = onBrandColor
            val yellow = VoxGestDesignTokens.SunGold
            val center = Offset(size.width * 0.48f, size.height * 0.60f)
            drawCircle(white, size.minDimension * 0.23f, center)
            drawCircle(brandColor, size.minDimension * 0.09f, center)
            listOf(0.20f, 0.34f, 0.48f, 0.62f).forEachIndexed { index, x ->
                drawLine(
                    color = white,
                    start = Offset(size.width * x, size.height * (0.57f - index * 0.02f)),
                    end = Offset(size.width * (x + 0.03f), size.height * (0.14f + index * 0.02f)),
                    strokeWidth = size.minDimension * 0.10f,
                    cap = StrokeCap.Round
                )
            }
            repeat(6) { index ->
                val angle = Math.toRadians((-78 + index * 28).toDouble())
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
        }
    }
}

@Composable
private fun CapstoneIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun VoxGestCapstoneCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VoxGestDesignTokens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
internal fun VoxGestExpandableInfoCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VoxGestDesignTokens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    if (expanded) "−" else "+",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                ) {
                    Column(Modifier.padding(16.dp), content = content)
                }
            }
        }
    }
}

@Composable
internal fun VoxGestStatusBadge(
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
            .border(1.dp, contentColor.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun VoxGestCapstoneAction(
    label: String,
    @DrawableRes icon: Int,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(VoxGestDesignTokens.ControlCorner))
            .background(background)
            .border(
                width = 1.dp,
                color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(VoxGestDesignTokens.ControlCorner)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = label,
            tint = foreground.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = foreground.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun VoxGestCapstoneBottomNav(
    items: List<VoxGestBottomNavItem>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val active = item.key == selectedKey
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelected(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(item.icon),
                        contentDescription = item.label,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label.uppercase(),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
