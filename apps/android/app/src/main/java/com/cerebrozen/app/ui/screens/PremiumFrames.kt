package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R

private val PremiumBackground = listOf(Color(0xFF0D1424), Color(0xFF182447), Color(0xFF241A4A))

/** Opt-in frame for non-protected pushed screens. It owns only presentation:
 * navigation callbacks and screen content stay with the caller. */
@Composable
internal fun PremiumSubPage(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumFrame(
        header = { PremiumFrameHeader(eyebrow, title, onBack = onBack) },
        content = content,
    )
}

/** Premium frame for non-protected root/tab surfaces such as Journal and You. */
@Composable
internal fun PremiumPage(
    eyebrow: String,
    title: String,
    trailing: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumFrame(
        header = { PremiumFrameHeader(eyebrow, title, trailing = trailing) },
        content = content,
    )
}

/** Premium opt-in navigation row for remaining hubs. Kept separate from NavRow
 * so protected screens retain their finished presentation. */
@Composable
internal fun PremiumNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = if (emphasis) Color(0xFF64C9FF) else Color(0xFFB18CFF)
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().pressScale(pressed, down = 0.975f)
            .background(Brush.linearGradient(listOf(Color(0xD91A2340), Color(0xB823294B))), shape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(accent.copy(alpha = 0.38f), Color.White.copy(alpha = 0.08f), Color(0x4464C9FF))),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(16.dp)
            .heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(50.dp).background(accent.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB9D0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(40.dp).background(accent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
    }
}

/** Illustrated loading/empty/error surface for remaining screens. */
@Composable
internal fun PremiumStateCard(
    icon: ImageVector,
    message: String,
    accent: Color = Color(0xFF64C9FF),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().appear(rise = 10f)
            .background(Brush.linearGradient(listOf(Color(0xD91A2340), Color(0xB823294B))), shape)
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(64.dp).background(accent.copy(alpha = 0.13f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD2D9EB),
            maxLines = 4,
        )
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, modifier = Modifier.fillMaxWidth(), onClick = onAction)
        }
    }
}

@Composable
private fun PremiumFrame(
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val motion = rememberInfiniteTransition(label = "premiumFrameAmbient")
    val drift by motion.animateFloat(
        initialValue = -0.05f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(7_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "premiumFrameDrift",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(PremiumBackground))) {
        PremiumFrameAmbience(if (reduceMotion) 0f else drift)
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 840.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header()
            Column(
                Modifier.fillMaxWidth().appear(rise = 14f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun PremiumFrameHeader(
    eyebrow: String,
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: ImageVector? = null,
) {
    val backLabel = stringResource(R.string.common_back)
    if (onBack != null) {
        Column(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).background(Color.White.copy(alpha = 0.07f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                        .clickable(onClickLabel = backLabel, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowBackIosNew,
                        contentDescription = backLabel,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                eyebrow.uppercase(),
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color(0xFF9FCBEA).copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 146.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xE62D285B), Color(0xD91C3159), Color(0xD2241A4A))),
                shape,
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x887A5CFF), Color.White.copy(alpha = 0.10f), Color(0x6664C9FF))),
                shape,
            )
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(Color(0x227A5CFF), CircleShape)
                    .border(1.dp, Color(0x447A5CFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color(0xFFBFDFFF), modifier = Modifier.size(22.dp))
            }
            trailing?.let {
                Box(
                    Modifier.size(48.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(it, contentDescription = null, tint = Color(0xFFDDE5FF), modifier = Modifier.size(23.dp))
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(top = 62.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = Color(0xFFB9C8FF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PremiumFrameAmbience(drift: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x2E7A5CFF), Color.Transparent)),
            radius = size.minDimension * 0.7f,
            center = Offset(size.width * 0.82f, size.height * (0.12f + drift)),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x1F64C9FF), Color.Transparent)),
            radius = size.minDimension * 0.5f,
            center = Offset(size.width * 0.05f, size.height * 0.68f),
        )
        listOf(0.12f to 0.14f, 0.88f to 0.28f, 0.76f to 0.63f, 0.18f to 0.84f).forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) Color(0x4464C9FF) else Color(0x44B18CFF),
                radius = 2.2.dp.toPx(),
                center = Offset(size.width * point.first, size.height * (point.second + drift * 0.3f)),
            )
        }
    }
}
