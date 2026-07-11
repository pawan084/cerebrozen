package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.TextSoft

/** Curated calm imagery for the hero cards (the richer ref-design look). Each
 * degrades to a gradient if it can't load. */
object HeroImg {
    const val calm = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=900&q=70"     // misty mountains
    const val journal = "https://images.unsplash.com/photo-1517842645767-c639042777db?w=900&q=70"  // open notebook
    const val sleep = "https://images.unsplash.com/photo-1444703686981-a3abbc4d4fe3?w=900&q=70"     // night sky
    const val mood = "https://images.unsplash.com/photo-1499209974431-9dddcece7f88?w=900&q=70"      // soft light
}

/**
 * The iOS HeroCard: a photographic background (AsyncImage, gradient fallback) with
 * a dark scrim and overlaid eyebrow / serif title / subtitle + optional content.
 */
@Composable
internal fun HeroCard(
    imageUrl: String,
    eyebrow: String,
    title: String,
    subtitle: String = "",
    height: Dp = 200.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    // Slow Ken Burns zoom on the artwork (steady under Reduce Motion).
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "hero")
    val zoom by transition.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "kenburns",
    )
    val imgScale = if (reduceMotion) 1f else zoom
    val base = Modifier
        .fillMaxWidth()
        .height(height)
        .shadow(16.dp, shape, clip = false, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
        .clip(shape)
        // Top-lit bevel edge, matching the glass cards.
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))), shape)
    val mod = if (onClick != null) base.clickable { onClick() } else base
    Box(mod) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Periwinkle, PeriwinkleDeep)),
            ),
        )
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().scale(imgScale),
            )
        }
        // Scrim so text stays legible over any image.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Ink.copy(alpha = 0.13f), Night.copy(alpha = 0.88f))),
            ),
        )
        // Glossy diagonal sheen — a soft top-left highlight, like light on glass.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.35f to Color.Transparent,
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = PeriwinkleSoft)
            Text(
                title, style = MaterialTheme.typography.headlineSmall, color = Cream,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
            content?.invoke(this)
        }
    }
}
