package com.cerebrozen.app.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.TextPrimary

/**
 * The CereBro C-ring mark, drawn on a Canvas so it scales and animates cleanly:
 * an open lavender→cyan ring, a glowing orb inside, and a soft highlight.
 * Mirrors the shared brand SVG (apps/web/public/brand/cerebro-mark.svg).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringR = s * 0.36f
        val orbR = s * 0.19f
        // Soft outer glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x338A7BF0), Color(0x00000000)),
                center = c, radius = s * 0.5f,
            ),
            radius = s * 0.5f, center = c,
        )
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

/** A brief branded launch: the mark scales + fades in over the night gradient. */
@Composable
fun Splash() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900)) }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NightMid, Night))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(
                Modifier.scale(0.82f + 0.18f * progress.value).alpha(progress.value),
                size = 112.dp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "CereBro",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                modifier = Modifier.alpha(progress.value),
            )
        }
    }
}
