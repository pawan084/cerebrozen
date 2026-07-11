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

/** The overlay itself — host once, at the app root, above everything else. */
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
            progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        onFinished()
    }
    val p = progress.value
    // Fade the whole flourish out over its last stretch so it dissolves, not pops.
    val overlayAlpha = if (p > 0.82f) (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f) else 1f
    Box(Modifier.fillMaxSize().alpha(overlayAlpha), contentAlignment = Alignment.Center) {
        if (!reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val n = 14
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension * 0.34f
                for (i in 0 until n) {
                    val ang = (i.toFloat() / n) * 2f * Math.PI.toFloat()
                    val r = maxR * p
                    val x = cx + cos(ang) * r
                    val y = cy + sin(ang) * r
                    drawCircle(
                        color = PARTICLE_COLORS[i % PARTICLE_COLORS.size].copy(alpha = (1f - p).coerceIn(0f, 1f)),
                        radius = 8f * (1f - p * 0.5f),
                        center = Offset(x, y),
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
