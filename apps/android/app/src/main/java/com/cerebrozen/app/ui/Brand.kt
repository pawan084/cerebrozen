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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.screens.rememberReduceMotion
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Periwinkle
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

// Deterministic pseudo-random in 0..1 from an index + salt, so the starfield is
// stable across recompositions (no flicker) without needing a stored seed.
private fun starRand(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

/** A brief branded launch: a night scene — starfield + soft aurora ribbons — with
 * the mark breathing in over it. The ribbons drift gently (steady under Reduce
 * Motion); the whole scene fades in. */
@Composable
fun Splash() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900)) }
    val appear = progress.value

    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "splash")
    val animatedDrift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val drift = if (reduceMotion) 0.5f else animatedDrift

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NightMid, Night))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft aurora ribbons across the upper sky.
            val ribbonColors = listOf(Periwinkle, Violet, Cyan)
            for (r in 0 until 3) {
                val yBase = h * (0.16f + r * 0.09f)
                val sway = h * 0.05f * (drift - 0.5f) * (if (r % 2 == 0) 1f else -1f)
                val path = Path().apply {
                    moveTo(-w * 0.1f, yBase)
                    quadraticBezierTo(w * 0.3f, yBase - h * 0.06f + sway, w * 0.55f, yBase)
                    quadraticBezierTo(w * 0.82f, yBase + h * 0.06f - sway, w * 1.1f, yBase - h * 0.02f)
                }
                drawPath(
                    path,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, ribbonColors[r].copy(alpha = 0.20f * appear), Color.Transparent),
                    ),
                    style = Stroke(width = 46f, cap = StrokeCap.Round),
                )
            }
            // A calm starfield in the top two-thirds.
            for (i in 0 until 46) {
                val x = starRand(i, 1) * w
                val y = starRand(i, 2) * h * 0.72f
                val rad = 0.6f + starRand(i, 3) * 1.4f
                val a = (0.25f + starRand(i, 4) * 0.55f) * appear
                drawCircle(Color.White.copy(alpha = a), radius = rad, center = Offset(x, y))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(
                Modifier.scale(0.82f + 0.18f * appear).alpha(appear),
                size = 112.dp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "CereBro",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                modifier = Modifier.alpha(appear),
            )
        }
    }
}
