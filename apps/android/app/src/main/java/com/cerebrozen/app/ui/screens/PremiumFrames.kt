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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.FieldFill

private val PremiumBackground: List<Color> get() = listOf(
    Night, Night, Night,
)

private val ReferenceSerif = FontFamily(Font(R.font.newsreader))

/** Opt-in frame for non-protected pushed screens. It owns only presentation:
 * navigation callbacks and screen content stay with the caller. */
@Composable
internal fun PremiumSubPage(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    /** The crisis door. Optional only because a handful of screens ARE the
     * crisis surface; everything else should pass it (design rule §1). */
    onUrgent: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumFrame(
        header = { PremiumFrameHeader(eyebrow, title, onBack = onBack, onUrgent = onUrgent) },
        content = content,
    )
}

/** Premium frame for non-protected root/tab surfaces such as Journal and You. */
@Composable
internal fun PremiumPage(
    eyebrow: String,
    title: String,
    trailing: ImageVector? = null,
    onUrgent: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumFrame(
        header = { PremiumFrameHeader(eyebrow, title, trailing = trailing, onUrgent = onUrgent) },
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
    val accent = if (emphasis) Cyan else Periwinkle
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().pressScale(pressed, down = 0.975f)
            .background(CardFill, shape)
            .border(1.dp, LineStroke.copy(alpha = .62f), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(15.dp)
            .heightIn(min = 70.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(44.dp).background(accent.copy(alpha = 0.11f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(19.dp))
    }
}

/** Illustrated loading/empty/error surface for remaining screens. */
@Composable
internal fun PremiumStateCard(
    icon: ImageVector,
    message: String,
    accent: Color = Cyan,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().appear(rise = 10f)
            .background(CardFill, shape)
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
            color = TextSoft,
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
    Column(Modifier.fillMaxSize().background(Night)) {
        header()
        Column(
            Modifier.fillMaxWidth().weight(1f).widthIn(max = 840.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 112.dp).appear(rise = 14f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Delegates to [CereBroTopBar] — this used to be its own 66dp row with the back
 * arrow tinted from a raw hex and no button role on the tap target. */
@Composable
private fun PremiumFrameHeader(
    eyebrow: String,
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: ImageVector? = null,
    onUrgent: (() -> Unit)? = null,
) = CereBroTopBar(
    title = title,
    subtitle = eyebrow,
    onBack = onBack,
    onUrgent = onUrgent,
    trailing = trailing,
)

@Composable
private fun PremiumFrameAmbience(drift: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Periwinkle.copy(alpha = 0.18f), Color.Transparent)),
            radius = size.minDimension * 0.7f,
            center = Offset(size.width * 0.82f, size.height * (0.12f + drift)),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.12f), Color.Transparent)),
            radius = size.minDimension * 0.5f,
            center = Offset(size.width * 0.05f, size.height * 0.68f),
        )
        listOf(0.12f to 0.14f, 0.88f to 0.28f, 0.76f to 0.63f, 0.18f to 0.84f).forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) Cyan.copy(alpha = 0.27f) else Periwinkle.copy(alpha = 0.27f),
                radius = 2.2.dp.toPx(),
                center = Offset(size.width * point.first, size.height * (point.second + drift * 0.3f)),
            )
        }
    }
}
