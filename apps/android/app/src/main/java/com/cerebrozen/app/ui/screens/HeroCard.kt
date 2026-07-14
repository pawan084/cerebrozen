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
import com.cerebrozen.app.ui.theme.ArtScrim
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.PeriwinkleSoft

/**
 * The iOS HeroCard: generative [HeroArt] as the default background (W21 —
 * always-designed, no network), with an optional photographic layer on top
 * when a caller passes a real [imageUrl] (Coil failures fall through to the
 * art), then the dark scrim and overlaid eyebrow / serif title / subtitle.
 * [kind] picks the art's accent family + motif ("sleep", "program", …).
 */
@Composable
internal fun HeroCard(
    eyebrow: String,
    title: String,
    subtitle: String = "",
    kind: String = "",
    imageUrl: String = "",
    height: Dp = 200.dp,
    alive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val base = Modifier
        .fillMaxWidth()
        .height(height)
        .shadow(16.dp, shape, clip = false, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
        .clip(shape)
        // Top-lit bevel edge, matching the glass cards.
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))), shape)
    val mod = if (onClick != null) base.clickable { onClick() } else base
    Box(mod) {
        // The designed default: deterministic generative art. [alive] opts the
        // hero into the W24 living-art loop (imperceptibly slow ambience;
        // Reduce Motion renders it exactly as static as before).
        HeroArt(kind = kind, title = title, modifier = Modifier.fillMaxSize(), alive = alive)
        if (imageUrl.isNotBlank()) {
            // E4: Ken Burns whisper — the photo drifts 1.0 → 1.04 over ~14s and
            // settles back, forever. Static under Reduce Motion (the transition
            // never even runs); the generative art never animates either way.
            val reduceMotion = rememberReduceMotion()
            val imgScale = if (reduceMotion) 1f else {
                val transition = rememberInfiniteTransition(label = "hero")
                val zoom by transition.animateFloat(
                    1f, 1.04f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "kenburns",
                )
                zoom
            }
            AsyncImage(
                model = imageUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().scale(imgScale),
            )
        }
        // Scrim so text stays legible over any art or image — ArtScrim, not the
        // themed Night token: the hero keeps its dark night art in both themes.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Ink.copy(alpha = 0.13f), ArtScrim.copy(alpha = 0.88f))),
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
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ArtTextSoft)
            }
            content?.invoke(this)
        }
    }
}
