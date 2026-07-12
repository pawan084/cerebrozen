package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.Warm
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A tiny, app-wide "well done" flourish — the Android mirror of the iOS
 * `.celebration` modifier. Any screen fires [Celebrations.trigger] on a completed
 * action (a check-in, a saved night, a finished tool); a single host at the app
 * root plays a particle bloom + a springing checkmark medallion and a success
 * haptic, then clears itself. Motion is suppressed under Reduce Motion (the
 * medallion still appears, briefly).
 */
object Celebrations {
    var active by mutableStateOf(false)
        private set

    fun trigger() { active = true }
    fun clear() { active = false }
}

private val PARTICLE_COLORS = listOf(Periwinkle, Cyan, Warm, Iris)

// Deterministic pseudo-random in 0..1 (same fold Brand.kt's starfield uses) so
// the constellation is stable within a bloom — no per-frame flicker.
private fun bloomRand(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

/** Draws one soft four-point star (the ContentArt sparkle shape). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBloomStar(
    center: Offset,
    r: Float,
    color: Color,
    alpha: Float,
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color.copy(alpha = alpha.coerceIn(0f, 1f)))
}

/** The overlay itself — host once, at the app root, above everything else.
 * W24: a calm constellation-bloom — soft star points drift outward from the
 * medallion on staggered starts and dissolve (one-shot, ≤1.4s). Reduce Motion
 * keeps the medallion-only appearance. */
@Composable
internal fun Celebration(onFinished: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        Haptics.success()
        if (reduceMotion) {
            progress.snapTo(1f)
            kotlinx.coroutines.delay(650)
        } else {
            progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        }
        onFinished()
    }
    val p = progress.value
    // Fade the whole flourish out over its last stretch so it dissolves, not pops.
    val overlayAlpha = if (p > 0.82f) (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f) else 1f
    Box(Modifier.fillMaxSize().alpha(overlayAlpha), contentAlignment = Alignment.Center) {
        if (!reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val n = 11
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension * 0.36f
                for (i in 0 until n) {
                    // Each star gets its own angle jitter, reach, size and a
                    // staggered start, so the ring reads as a constellation
                    // blooming, not a burst.
                    val delay = 0.22f * bloomRand(i, 1)
                    val t = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                    if (t <= 0f) continue
                    val eased = 1f - (1f - t) * (1f - t)   // gentle decelerate
                    val ang = (i.toFloat() / n + 0.04f * (bloomRand(i, 2) - 0.5f)) * 2f * Math.PI.toFloat()
                    val reach = maxR * (0.72f + 0.28f * bloomRand(i, 3)) * eased
                    val center = Offset(cx + cos(ang) * reach, cy + sin(ang) * reach)
                    val starR = (7f + 6f * bloomRand(i, 4)) * (1f - 0.35f * t)
                    drawBloomStar(
                        center = center,
                        r = starR,
                        color = PARTICLE_COLORS[i % PARTICLE_COLORS.size],
                        alpha = 0.9f * (1f - t),
                    )
                }
            }
        }
        // Medallion pops in over the first quarter, then holds.
        val medallionScale = if (reduceMotion) 1f else (p * 4f).coerceAtMost(1f)
        Box(
            Modifier.size(84.dp).scale(medallionScale).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Periwinkle, Iris)))
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(40.dp))
        }
    }
}
