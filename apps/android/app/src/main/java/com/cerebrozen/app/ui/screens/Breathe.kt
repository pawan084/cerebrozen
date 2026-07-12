package com.cerebrozen.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.Teal
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

// The one breathing implementation (REDESIGN §2.2): every breathe surface —
// Toolkit box breathing, the journaling breathing tool, the onboarding reset —
// hosts this engine with a preset instead of pacing its own orb.

/** Which pacing a breathe surface runs. */
enum class BreathePreset { Box, Color, Reset }

/** One beat of a breathing cycle — pure data, so the pacing is unit-testable. */
internal data class BreathPhase(val label: String, val seconds: Int, val expanded: Boolean)

/** The phase sequence per preset. Box and Color pace 4-4-4-4 with holds; Reset
 * is the gentle onboarding rhythm — four in, four out, nothing to hold. */
// i18n: pending — pure function, needs context plumbing (phase labels are user copy;
// unit tests assert them directly).
internal fun breathePhases(preset: BreathePreset): List<BreathPhase> = when (preset) {
    BreathePreset.Reset -> listOf(
        BreathPhase("Breathe in", 4, expanded = true),
        BreathPhase("Breathe out", 4, expanded = false),
    )
    BreathePreset.Box, BreathePreset.Color -> listOf(
        BreathPhase("Breathe in", 4, expanded = true),
        BreathPhase("Hold", 4, expanded = true),
        BreathPhase("Breathe out", 4, expanded = false),
        BreathPhase("Hold", 4, expanded = false),
    )
}

/** Orb tint per phase — the Color preset shifts through the calm accents; the
 * other presets hold the breathing-orb cyan. Pure, so the cycle is testable. */
internal fun breatheTint(preset: BreathePreset, phase: Int): Color = when (preset) {
    BreathePreset.Color -> listOf(Cyan, Teal, Periwinkle, PeriwinkleSoft)[phase % 4]
    else -> Cyan
}

/** The shared pacing orb: phase label above, a per-second count inside the orb,
 * a quiet breaths tally below. The orb is a function of the phase (expand on
 * inhale, hold, contract on exhale) — never a free-running pulse — and holds a
 * steady size under Reduce Motion while the label and count keep guiding. */
@Composable
fun BreatheEngine(preset: BreathePreset, modifier: Modifier = Modifier) {
    val phases = remember(preset) { breathePhases(preset) }
    var phase by remember(preset) { mutableIntStateOf(0) }
    var count by remember(preset) { mutableIntStateOf(phases.first().seconds) }
    var breaths by remember(preset) { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()

    // One pacer for every preset: a 1-second tick counts the phase down, then
    // advances it. A gentle haptic marks each phase change — a rhythm you can
    // follow with eyes closed; firmer on the active breaths, softer on holds.
    LaunchedEffect(preset) {
        while (true) {
            delay(1_000)
            if (count > 1) {
                count -= 1
            } else {
                val next = (phase + 1) % phases.size
                phase = next
                count = phases[next].seconds
                if (next == 0) breaths += 1
                Haptics.soft(if (phases[next].label.startsWith("Breathe")) 0.5f else 0.3f)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (phases[phase].expanded) 1.12f else 0.74f,
        animationSpec = if (reduceMotion) snap() else tween(3800, easing = FastOutSlowInEasing),
        label = "breathe-orb-scale",
    )
    val tint by animateColorAsState(
        targetValue = breatheTint(preset, phase),
        animationSpec = if (reduceMotion) snap() else tween(1400, easing = FastOutSlowInEasing),
        label = "breathe-orb-tint",
    )

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            phases[phase].label,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            // E1: a soft aurora glow behind the orb that swells and settles with
            // the SAME phase animatable driving the orb — never a free-running
            // pulse. Under Reduce Motion the scale holds 1f, so the glow is a
            // static halo (read inside graphicsLayer: no per-frame recomposition).
            Box(
                Modifier
                    .size(236.dp)
                    .graphicsLayer {
                        scaleX = scale * 1.24f
                        scaleY = scale * 1.24f
                    }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.20f), Color.Transparent))),
            )
            // Static guide rings — the still water the orb breathes inside.
            repeat(3) { ring ->
                Box(
                    Modifier
                        .size((122 + ring * 54).dp)
                        .clip(CircleShape)
                        .border(1.dp, LineStroke, CircleShape),
                )
            }
            val orbCd = stringResource(R.string.breathe_orb_cd, phases[phase].label)
            Box(
                Modifier
                    .size(146.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, tint)))
                    .border(1.dp, tint.copy(alpha = 0.55f), CircleShape)
                    .semantics { contentDescription = orbCd },
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.displaySmall, color = Ink)
            }
        }
        Text(
            if (breaths == 0) stringResource(R.string.breathe_settle)
            else pluralStringResource(R.plurals.breathe_calm_breaths, breaths, breaths),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

/** Full-screen host for the engine — the `breathe/box` and `breathe/reset`
 * routes. Title and framing vary by preset; the engine does the rest. */
@Composable
fun BreatheScreen(preset: BreathePreset, onBack: () -> Unit) {
    val (eyebrow, title, intro) = when (preset) {
        BreathePreset.Box -> Triple(
            stringResource(R.string.breathe_box_eyebrow), stringResource(R.string.breathe_box_title),
            stringResource(R.string.breathe_box_intro),
        )
        BreathePreset.Color -> Triple(
            stringResource(R.string.breathe_color_eyebrow), stringResource(R.string.breathe_color_title),
            stringResource(R.string.breathe_color_intro),
        )
        BreathePreset.Reset -> Triple(
            stringResource(R.string.breathe_reset_eyebrow), stringResource(R.string.breathe_reset_title),
            stringResource(R.string.breathe_reset_intro),
        )
    }
    SubPage(eyebrow, title, onBack) {
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        BreatheEngine(preset, Modifier.fillMaxWidth())
        PrimaryButton(text = stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth()) { onBack() }
        WhyThisWorks(stringResource(R.string.breathe_why))
    }
}
