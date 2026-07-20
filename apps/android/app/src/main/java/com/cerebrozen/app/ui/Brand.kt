package com.cerebrozen.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.rememberReduceMotion
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.Violet
import kotlin.math.floor
import kotlin.math.sin

/**
 * The CereBro C-ring mark, drawn on a Canvas so it scales and animates cleanly:
 * an open lavender→cyan ring, a glowing orb inside, and a soft highlight.
 * Mirrors the shared brand SVG (apps/web/public/brand/cerebro-mark.svg).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 96.dp, showGlow: Boolean = true) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringR = s * 0.36f
        val orbR = s * 0.19f
        // Soft outer glow — omit it in compact placements (nav/header) where the
        // extra bloom would spill past the mark's bounds.
        if (showGlow) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x338A7BF0), Color(0x00000000)),
                    center = c, radius = s * 0.5f,
                ),
                radius = s * 0.5f, center = c,
            )
        }
        // Open C-ring (gap on the right)
        drawArc(
            brush = Brush.linearGradient(
                listOf(Color(0xFFCBB6FF), Color(0xFF8FE6EE)),
                start = Offset(0f, 0f), end = Offset(this.size.width, this.size.height),
            ),
            startAngle = 52f, sweepAngle = 256f, useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = s * 0.1f, cap = StrokeCap.Round),
        )
        // Orb
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, Color(0xFFDFE0FF), Color(0xFF8A7BF0), Color(0xFF5B52C9)),
                center = Offset(c.x - orbR * 0.28f, c.y - orbR * 0.34f), radius = orbR * 1.5f,
            ),
            radius = orbR, center = c,
        )
        // Highlight
        drawCircle(
            Color.White.copy(alpha = 0.38f), radius = orbR * 0.22f,
            center = Offset(c.x - orbR * 0.4f, c.y - orbR * 0.42f),
        )
    }
}

/** A layered glow orb — an outer bloom, a luminous core, and a top-left specular
 * highlight. The signature brand hero element for welcome / breathing surfaces. */
@Composable
fun GlowOrb(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    scale: Float = 1f,
    core: Color = Cyan,
) {
    Box(modifier.size(size).scale(scale), contentAlignment = Alignment.Center) {
        // Outer bloom.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(core.copy(alpha = 0.35f), Color.Transparent))))
        // Luminous core.
        Box(
            Modifier.fillMaxSize(0.72f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, core, PeriwinkleDeep))),
        )
        // Top-left specular highlight.
        Box(
            Modifier.fillMaxSize(0.30f).offset(x = size * -0.13f, y = size * -0.13f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))),
        )
    }
}

// Deterministic pseudo-random in 0..1 from an index + salt, so the starfield is
// stable across recompositions (no flicker) without needing a stored seed.
private fun starRand(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

// W24: the splash settle curves, kept pure so the choreography is testable by
// inspection. One 0→1 progress drives everything (≤900ms, one-shot).
/** Orb scale: a spring-like arrival — 0.92 rises with an ease-out AND a gentle overshoot
 *  past 1.0 (peak ~1.04 mid-settle), then comes to rest at exactly 1.0 by ~90%. Reads more
 *  alive than a flat ease-out, and stays at rest before the wordmark finishes. */
internal fun splashOrbScale(t: Float): Float {
    val k = (t / 0.9f).coerceIn(0f, 1f)
    val eased = 1f - (1f - k) * (1f - k)                        // 0.92 → 1.0
    val overshoot = 0.06f * sin(k * Math.PI.toFloat())         // 0 at both ends, +peak mid
    return 0.92f + 0.08f * eased + overshoot
}

/** Glow bloom: swells past its resting strength mid-settle, then eases back —
 * the orb "arrives" with a soft breath of light (rests at 1). */
internal fun splashGlowBloom(t: Float): Float =
    1f + 0.9f * sin(t.coerceIn(0f, 1f) * Math.PI.toFloat())

/** Wordmark: fades up beneath the orb over the last ~55%. */
internal fun splashWordmarkAppear(t: Float): Float = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** A brief branded launch: a night scene — starfield + soft aurora ribbons —
 * with the orb settling into the wordmark: the mark scales 0.92→1 under a soft
 * glow bloom while "CereBro" fades up beneath (one-shot, ≤900ms). Reduce
 * Motion: the final frame, statically — never blank. */
@Composable
fun Splash() {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(900))
    }
    val appear = progress.value
    val transition = rememberInfiniteTransition(label = "splash")
    val animatedDrift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val drift = if (reduceMotion) 0.5f else animatedDrift

    // TalkBack gets a spoken brand moment instead of silence (#29).
    val loadingLabel = stringResource(R.string.splash_loading)
    // Time-of-day sky (#21): still a NIGHT scene (this is a calm brand), but the top of the
    // gradient and the aurora carry a hint of the hour — a warm rose at dawn, periwinkle by
    // day, violet at dusk — so the daily companion greets you a little differently each launch.
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val skyAccent = when (hour) {
        in 5..7 -> Color(0xFFB56B7A)   // dawn rose
        in 8..16 -> Color(0xFF6C7BD8)  // day periwinkle
        in 17..20 -> Color(0xFF7A5AA8) // dusk violet
        else -> Color(0xFF2A2E5C)      // deep night
    }
    val topColor = lerp(NightMid, skyAccent, 0.22f)
    Box(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            }
            .background(Brush.verticalGradient(listOf(topColor, Night))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft aurora ribbons across the upper sky, tinted toward the hour (#21).
            val ribbonColors = listOf(Periwinkle, Violet, Cyan).map { lerp(it, skyAccent, 0.3f) }
            for (r in 0 until 3) {
                val yBase = h * (0.16f + r * 0.09f)
                val sway = h * 0.05f * (drift - 0.5f) * (if (r % 2 == 0) 1f else -1f)
                val path = Path().apply {
                    moveTo(-w * 0.1f, yBase)
                    quadraticTo(w * 0.3f, yBase - h * 0.06f + sway, w * 0.55f, yBase)
                    quadraticTo(w * 0.82f, yBase + h * 0.06f - sway, w * 1.1f, yBase - h * 0.02f)
                }
                drawPath(
                    path,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, ribbonColors[r].copy(alpha = 0.20f * appear), Color.Transparent),
                    ),
                    style = Stroke(width = 46f, cap = StrokeCap.Round),
                )
            }
            // A calm starfield in the top two-thirds — each star twinkles on its OWN phase
            // (not one global fade), so the sky is alive rather than a single sheet.
            for (i in 0 until 46) {
                val x = starRand(i, 1) * w
                val y = starRand(i, 2) * h * 0.72f
                val rad = 0.6f + starRand(i, 3) * 1.4f
                val phase = starRand(i, 5)
                val twinkle = 0.6f + 0.4f * (0.5f + 0.5f * sin((drift + phase) * (2f * Math.PI.toFloat())))
                val a = (0.25f + starRand(i, 4) * 0.55f) * appear * twinkle
                drawCircle(Color.White.copy(alpha = a), radius = rad, center = Offset(x, y))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The orb settles in (0.92 → 1) beneath a glow that blooms past its
            // resting strength and eases back — one calm arrival, then stillness.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp)
                        .alpha((0.5f * splashGlowBloom(appear) * appear).coerceAtMost(1f))
                        .background(
                            Brush.radialGradient(listOf(Color(0x668A7BF0), Color(0x00000000))),
                            CircleShape,
                        ),
                )
                BrandMark(
                    Modifier.scale(splashOrbScale(appear)).alpha(appear),
                    size = 112.dp,
                )
            }
            Spacer(Modifier.height(22.dp))
            // "Cere" solid, "Bro" in an iris→periwinkle sweep (mirrors the iOS wordmark) —
            // fading up beneath the orb over the settle's back half, with a single light GLINT
            // travelling across "Bro" once as it arrives, then settling to the plain gradient.
            val g = ((appear - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val glint = sin(g * Math.PI.toFloat())              // 0 at both ends, 1 mid-sweep
            val glintPos = 0.15f + g * 0.7f                     // white band travels 0.15 → 0.85
            val broBrush = Brush.linearGradient(
                0f to Iris,
                glintPos to lerp(Periwinkle, Color.White, glint * 0.9f),
                1f to Periwinkle,
            )
            val wordmark = buildAnnotatedString {
                withStyle(SpanStyle(color = TextPrimary)) { append("Cere") }
                withStyle(SpanStyle(brush = broBrush)) { append("Bro") }
            }
            val textT = splashWordmarkAppear(appear)
            Text(
                wordmark,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .offset(y = 8.dp * (1f - textT))
                    .alpha(textT),
            )
            // Earned greeting (#22): a returning (signed-in) account is welcomed back with a
            // time-of-day line that matches the tinted sky; a first-run user sees only the
            // wordmark. Staggered a beat behind the wordmark for a gentle settle.
            if (Session.signedIn) {
                val greeting = stringResource(
                    when {
                        hour < 12 -> R.string.talk_good_morning
                        hour < 17 -> R.string.talk_good_afternoon
                        else -> R.string.talk_good_evening
                    },
                )
                val greetT = ((appear - 0.6f) / 0.4f).coerceIn(0f, 1f)
                Spacer(Modifier.height(10.dp))
                Text(
                    greeting,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary.copy(alpha = 0.62f),
                    modifier = Modifier.offset(y = 6.dp * (1f - greetT)).alpha(greetT),
                )
            }
        }
    }
}
