package com.cerebrozen.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Gradients
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Violet

/**
 * The living app backdrop — the Android port of the iOS `AppBackground`: a deep
 * night base with three large, blurred brand-tinted orbs that drift and breathe
 * slowly for a calm sense of depth. The primary orb takes an [accent] that shifts
 * per section. Motion is suppressed under Reduce Motion (the orbs sit still), and
 * the `blur` softening only renders on API 31+ — below that the orbs are already
 * soft radial gradients, so they degrade gracefully.
 *
 * W27 §2 (Calm study): while audio plays, the primary orb's tint drifts toward
 * the playing kind's `artAccent` — `Player.audibleKind()` is the signal (the
 * kind the starting screen declared, or "soundscape" for the mixer). The
 * visual reacts to WHAT is playing, never to the waveform; it's a single slow
 * color lerp (~1200ms), zero extra motion. Reduce Motion snaps the color.
 *
 * @param sceneBehind true when a [SceneVideo] is playing underneath. The base
 *   plate is normally an OPAQUE gradient — it is the app's page floor — so a
 *   video behind it would be perfectly invisible. With a scene present the plate
 *   becomes a scrim instead: dark enough to hold text contrast over moving
 *   footage, sheer enough to let the scene read through. The orbs and sheen are
 *   already translucent and stay exactly as they are, so the app keeps its own
 *   colour identity on top of whatever video is uploaded. Defaults to false —
 *   with no scene (the shipping default) this composable is byte-for-byte what
 *   it was.
 */
/** How much of the page floor survives over a scene video. Tuned on-device against
 * the Sleep tab's cards: below ~0.5 the body copy starts to lose the brighter
 * frames of the footage. */
internal const val SCENE_SCRIM_ALPHA = 0.55f

@Composable
internal fun AuroraBackground(
    accent: Color = Periwinkle,
    modifier: Modifier = Modifier,
    sceneBehind: Boolean = false,
) {
    val reduceMotion = rememberReduceMotion()
    val primaryTint by animateColorAsState(
        targetValue = accent,
        animationSpec = if (reduceMotion) snap() else tween(1200, easing = FastOutSlowInEasing),
        label = "aurora-playing-tint",
    )
    // Slow 11s breathe/drift — but only when motion is allowed; under Reduce Motion
    // the orbs hold a still mid-position and no animation runs at all.
    val driftAnim = remember { Animatable(0.5f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            driftAnim.snapTo(0.5f)
        } else {
            driftAnim.snapTo(0f)
            driftAnim.animateTo(
                1f,
                infiniteRepeatable(tween(11_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            )
        }
    }
    val drift = driftAnim.value

    // Dawn reads as a soft light sky: the same drifting orbs, but gentler —
    // the themed accents are deep on Dawn, so lower alphas keep them as pale
    // lavender/teal washes over the cream base instead of muddy stains.
    val night = AppTheme.isNight
    val orbAlphas = if (night) floatArrayOf(0.16f, 0.12f, 0.05f) else floatArrayOf(0.10f, 0.08f, 0.04f)
    // The page floor. Opaque normally; a scrim when a scene video is playing under
    // it, or the video could never be seen. SCENE_SCRIM_ALPHA is a legibility
    // number, not a taste one: body text and card strokes have to stay readable
    // over footage we don't control, and a wellness app that makes you squint has
    // already failed. Erring dark is the safe direction.
    val plate: Modifier = if (sceneBehind) {
        Modifier.background(Night.copy(alpha = SCENE_SCRIM_ALPHA))
    } else {
        Modifier.background(Gradients.night)
    }
    Box(modifier.fillMaxSize().then(plate)) {
        AuroraOrb(primaryTint.copy(alpha = orbAlphas[0]), size = 380.dp, blur = 100.dp,
            fromX = -70, fromY = -180, toX = -130, toY = -240, drift = drift)
        AuroraOrb(Violet.copy(alpha = orbAlphas[1]), size = 320.dp, blur = 110.dp,
            fromX = 90, fromY = 30, toX = 140, toY = -60, drift = drift)
        AuroraOrb(Cyan.copy(alpha = orbAlphas[2]), size = 260.dp, blur = 90.dp,
            fromX = -50, fromY = 320, toX = 70, toY = 250, drift = drift)
        // Soft top-lit sheen, mirroring the iOS top radial highlight. On Dawn
        // it's a brighter white glow — morning light rather than a lavender haze.
        val sheen = if (night) TextSoft.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-120).dp).size(420.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(sheen, Color.Transparent))),
        )
    }
}

@Composable
private fun BoxScope.AuroraOrb(
    color: Color,
    size: Dp,
    blur: Dp,
    fromX: Int,
    fromY: Int,
    toX: Int,
    toY: Int,
    drift: Float,
) {
    val x = (fromX + (toX - fromX) * drift).dp
    val y = (fromY + (toY - fromY) * drift).dp
    val scale = 0.95f + 0.17f * drift
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(x = x, y = y)
            .size(size)
            .scale(scale)
            .blur(blur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color, Color.Transparent))),
    )
}
