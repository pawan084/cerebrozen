package com.cerebrozen.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Chime
import com.cerebrozen.app.audio.BreathVoice
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.Violet
import com.cerebrozen.app.ui.theme.FunnelHeaderTop
import com.cerebrozen.app.ui.theme.FunnelHeaderBottom
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.Teal
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.delay

// The one breathing implementation (REDESIGN §2.2): every breathe surface —
// Toolkit box breathing, the journaling breathing tool, the onboarding reset —
// hosts this engine with a preset instead of pacing its own orb.

/** Which pacing a breathe surface runs. */
enum class BreathePreset { Box, Color, Reset }

/** Stable phase id — pure data for pacing/cues; the USER copy resolves via
 * [phaseLabelRes] at the composable (i18n: labels were English literals). */
internal enum class BreathKind { IN, HOLD, OUT }

/** One beat of a breathing cycle — pure data, so the pacing is unit-testable. */
internal data class BreathPhase(val kind: BreathKind, val seconds: Int, val expanded: Boolean)

/** Display copy for a phase — localized at the display site. */
internal fun phaseLabelRes(kind: BreathKind): Int = when (kind) {
    BreathKind.IN -> R.string.breathe_phase_in
    BreathKind.HOLD -> R.string.breathe_phase_hold
    BreathKind.OUT -> R.string.breathe_phase_out
}

/** How much longer the Reset exhale runs than its inhale, in seconds. */
internal const val RESET_EXHALE_EXTRA = 2

/** The phase sequence per preset. Box and Color pace with holds; Reset is the
 * gentle onboarding rhythm — in, out, nothing to hold. W27 §4 (Calm study):
 * [secondsPerPhase] is user-selectable — Classic 4s (the long-standing
 * default), Gentle 6s, Slow 8s — scaling every phase equally.
 *
 * Reset exhales [RESET_EXHALE_EXTRA] seconds longer than it inhales. That is
 * the whole mechanism — a longer exhale than inhale is the part of slow
 * breathing with real vagal-tone evidence, and it is what iOS `BreathingPacer
 * .Preset.reset` ("in for four, out for six") and the web wind-down ritual
 * have always paced. Android alone ran it symmetrically, so the same named
 * "two-minute reset" breathed differently on the two phones; corrected
 * 2026-07-29, pace scaling preserved. */
internal fun breathePhases(preset: BreathePreset, secondsPerPhase: Int = 4): List<BreathPhase> = when (preset) {
    BreathePreset.Reset -> listOf(
        BreathPhase(BreathKind.IN, secondsPerPhase, expanded = true),
        BreathPhase(BreathKind.OUT, secondsPerPhase + RESET_EXHALE_EXTRA, expanded = false),
    )
    BreathePreset.Box, BreathePreset.Color -> listOf(
        BreathPhase(BreathKind.IN, secondsPerPhase, expanded = true),
        BreathPhase(BreathKind.HOLD, secondsPerPhase, expanded = true),
        BreathPhase(BreathKind.OUT, secondsPerPhase, expanded = false),
        BreathPhase(BreathKind.HOLD, secondsPerPhase, expanded = false),
    )
}

/** Orb tint per phase — the Color preset shifts through the calm accents; the
 * other presets hold the breathing-orb cyan. Pure, so the cycle is testable. */
internal fun breatheTint(preset: BreathePreset, phase: Int): Color = when (preset) {
    BreathePreset.Color -> listOf(Cyan, Teal, Periwinkle, PeriwinkleSoft)[phase % 4]
    else -> Cyan
}

private fun playBreathingCue(phase: BreathPhase) {
    when (phase.kind) {
        BreathKind.IN -> Chime.playBreathCue(inhale = true)
        BreathKind.OUT -> Chime.playBreathCue(inhale = false)
        // Hold keeps the exact chime it has always rung; playHoldCue only deviates
        // if a real `breathe.hold` cue has been uploaded.
        BreathKind.HOLD -> Chime.playHoldCue()
    }
}

/** The shared pacing orb: phase label above, a per-second count inside the orb,
 * a quiet breaths tally below. The orb is a function of the phase (expand on
 * inhale, hold, contract on exhale) — never a free-running pulse — and holds a
 * steady size under Reduce Motion while the label and count keep guiding. */
/** Seconds of guided breathing after [breaths] complete cycles. Pure. */
internal fun breatheElapsedSeconds(preset: BreathePreset, secondsPerPhase: Int, breaths: Int): Int =
    breathePhases(preset, secondsPerPhase).sumOf { it.seconds } * breaths

/**
 * Whether the two minutes the app keeps promising have actually passed.
 *
 * "Two-minute reset" / "Try a 2-minute reset" / "Fast anxiety-stress reset — 2
 * minutes" appear on five surfaces, and nothing measured or marked two minutes:
 * the Reset preset is an open-ended in/out cycle that runs until you tap away.
 * Rather than delete a genuinely useful piece of information from five places,
 * the claim is now kept — once, quietly, and only for the preset that makes it.
 *
 * Deliberately NOT a running timer or a breath counter. This product tells users
 * elsewhere there is "no streak to break"; putting a clock on a calming exercise
 * would be the same mistake in miniature. One line, at the moment it becomes
 * true, and the breathing carries on either way.
 */
internal fun twoMinutesReached(preset: BreathePreset, secondsPerPhase: Int, breaths: Int): Boolean =
    preset == BreathePreset.Reset &&
        breatheElapsedSeconds(preset, secondsPerPhase, breaths) >= 120

@Composable
fun BreatheEngine(
    preset: BreathePreset,
    modifier: Modifier = Modifier,
    secondsPerPhase: Int = 4,
    // Defaults read the persisted choice: the ritual/tool/onboarding hosts
    // never passed these, so a user who turned breathe haptics off in
    // BreatheScreen was still pulsed every phase everywhere else (audit B9).
    // Hosts that pass explicit values (BreatheScreen's own toggles) still win.
    hapticsOn: Boolean = com.cerebrozen.app.audio.Chime.breatheHapticsEnabled,
    chimeOn: Boolean = com.cerebrozen.app.audio.Chime.breatheChimeEnabled,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val phaseVoice = remember { BreathVoice(context) }
    DisposableEffect(phaseVoice) { onDispose { phaseVoice.dispose() } }
    val phases = remember(preset, secondsPerPhase) { breathePhases(preset, secondsPerPhase) }
    var phase by remember(preset, secondsPerPhase) { mutableIntStateOf(0) }
    var count by remember(preset, secondsPerPhase) { mutableIntStateOf(phases.first().seconds) }
    var breaths by remember(preset) { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()
    val spokenPhase = stringResource(phaseLabelRes(phases[phase].kind))

    // Narration follows the exact same phase state as the label and orb. It is
    // intentionally independent of the optional chime so eyes-closed guidance
    // remains available while the quiet ambient bed continues underneath.
    LaunchedEffect(phase, spokenPhase) { phaseVoice.speak(spokenPhase) }

    // One pacer for every preset: a 1-second tick counts the phase down, then
    // advances it. A gentle haptic marks each phase change — a rhythm you can
    // follow with eyes closed; firmer on the active breaths, softer on holds.
    // W27 §4: the haptic is now user-toggleable, and an OFF-by-default soft
    // chime can mark phase changes too. Both are guidance, not motion —
    // Reduce Motion deliberately leaves them alone.
    // B30: the flags are read through rememberUpdatedState so a mid-session
    // toggle changes behaviour WITHOUT restarting the tick coroutine — keying
    // on them stretched the current second and replayed a cue mid-phase.
    val liveHaptics by androidx.compose.runtime.rememberUpdatedState(hapticsOn)
    val liveChime by androidx.compose.runtime.rememberUpdatedState(chimeOn)
    LaunchedEffect(preset, secondsPerPhase) {
        if (liveChime) playBreathingCue(phases[phase])
        while (true) {
            delay(1_000)
            if (count > 1) {
                count -= 1
            } else {
                val next = (phase + 1) % phases.size
                phase = next
                count = phases[next].seconds
                if (next == 0) breaths += 1
                if (liveHaptics) Haptics.soft(if (phases[next].kind != BreathKind.HOLD) 0.5f else 0.3f)
                if (liveChime) playBreathingCue(phases[next])
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (phases[phase].expanded) {
            if (compact) 1.06f else 1.12f
        } else {
            if (compact) 0.88f else 0.74f
        },
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = secondsPerPhase * if (compact) 1_000 else 900,
            easing = if (compact) CubicBezierEasing(0.37f, 0f, 0.63f, 1f) else FastOutSlowInEasing,
        ),
        label = "breathe-orb-scale",
    )
    val tint by animateColorAsState(
        targetValue = breatheTint(preset, phase),
        animationSpec = if (reduceMotion) snap() else tween(1400, easing = FastOutSlowInEasing),
        label = "breathe-orb-tint",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (phases[phase].expanded) 0.34f else 0.18f,
        animationSpec = if (reduceMotion) snap() else tween(1_200, easing = FastOutSlowInEasing),
        label = "breathe-glow-alpha",
    )
    val phaseProgress = remember(preset, secondsPerPhase) { Animatable(0f) }
    LaunchedEffect(phase, secondsPerPhase, reduceMotion) {
        phaseProgress.snapTo(0f)
        if (!reduceMotion) {
            phaseProgress.animateTo(1f, tween(phases[phase].seconds * 1_000, easing = LinearEasing))
        }
    }
    val ringProgress = if (reduceMotion) {
        ((phases[phase].seconds - count).toFloat() / phases[phase].seconds).coerceIn(0f, 1f)
    } else phaseProgress.value
    val heroHeight = if (compact) 250.dp else 330.dp
    val glowSize = if (compact) 216.dp else 282.dp
    val ringBase = if (compact) 184 else 240
    val ringStep = if (compact) 22 else 30
    val progressSize = if (compact) 238.dp else 300.dp
    val orbSize = if (compact) 164.dp else 214.dp
    val instructionSize = if (compact) 22.sp else 24.sp
    val orbGradient = if (compact) {
        // Constant art, not themed roles: the orb carries [Ink] as its count in
        // BOTH themes, so it has to stay a light object. The stops are the
        // theme-independent plum art constants (white core → deep plum rim).
        listOf(Cream, PeriwinkleSoft, Iris, Violet)
    } else {
        listOf(Color.White, Cream, PeriwinkleSoft, Iris, Violet)
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = phases[phase].kind,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                else (fadeIn(tween(500)) + scaleIn(initialScale = 0.94f)) togetherWith
                    (fadeOut(tween(320)) + scaleOut(targetScale = 1.04f))
            },
            label = "breathingInstruction",
        ) { kind ->
            Text(
                stringResource(phaseLabelRes(kind)),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = instructionSize, fontWeight = FontWeight.SemiBold),
                // Themed, not Color.White: the engine is hosted by the
                // theme-following onboarding funnel too, where a white
                // instruction on Dawn's cream was invisible (caught on the
                // emulator). The breathe screens' own dark chrome renders
                // Night's near-white exactly as before.
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(heroHeight), contentAlignment = Alignment.Center) {
            // E1: a soft aurora glow behind the orb that swells and settles with
            // the SAME phase animatable driving the orb — never a free-running
            // pulse. Under Reduce Motion the scale holds 1f, so the glow is a
            // static halo (read inside graphicsLayer: no per-frame recomposition).
            Box(
                Modifier
                    .size(glowSize)
                    .graphicsLayer {
                        val haloScale = if (compact) 1.08f else 1.18f
                        scaleX = scale * haloScale
                        scaleY = scale * haloScale
                    }
                    .blur(22.dp)
                    .background(
                        Brush.radialGradient(listOf(tint.copy(alpha = glowAlpha), Periwinkle.copy(alpha = 0.2f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            // Static guide rings — the still water the orb breathes inside.
            repeat(if (compact) 1 else 3) { ring ->
                Box(
                    Modifier
                        .size((ringBase + ring * ringStep).dp)
                        .graphicsLayer {
                            val ringScale = if (reduceMotion) 1f else scale * (1f + ring * 0.025f)
                            scaleX = ringScale
                            scaleY = ringScale
                            alpha = if (compact) 0.10f else 0.20f - ring * 0.035f
                        }
                        .clip(CircleShape)
                        .border(1.dp, tint.copy(alpha = 0.34f), CircleShape),
                )
            }
            Canvas(Modifier.size(progressSize)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension / 2f - 4.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Cyan, Periwinkle, Accent2)),
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            val orbCd = stringResource(R.string.breathe_orb_cd, stringResource(phaseLabelRes(phases[phase].kind)))
            Box(
                Modifier
                    .size(orbSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(22.dp, CircleShape, clip = false, ambientColor = tint.copy(alpha = 0.55f), spotColor = Periwinkle.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(orbGradient),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.48f), CircleShape)
                    .semantics { contentDescription = orbCd },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = if (compact) 34.sp else 38.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Ink,
                    )
                    Text(
                        stringResource(R.string.breathe_seconds_remaining),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Ink.copy(alpha = 0.72f),
                    )
                }
            }
        }
        Text(
            if (breaths == 0) stringResource(R.string.breathe_settle)
            else pluralStringResource(R.plurals.breathe_calm_breaths, breaths, breaths),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        // The moment the app's "two minutes" becomes true — said once, then the
        // breathing carries on. See twoMinutesReached for why this is not a timer.
        if (twoMinutesReached(preset, secondsPerPhase, breaths)) {
            Text(
                stringResource(R.string.breathe_two_minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = Cyan,
            )
        }
    }
}

/** Full-screen host for the engine — the `breathe/box` and `breathe/reset`
 * routes. Title and framing vary by preset; the engine does the rest. */
@Composable
fun BreatheScreen(preset: BreathePreset, onBack: () -> Unit) {
    ToolAmbienceEffect(R.raw.ambient_bed)
    val (_, title, intro) = when (preset) {
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
    // W27 §4 (Calm parity, treated as accessibility): a per-phase pace choice,
    // a persisted haptic-guide toggle (default on) and a persisted OFF-by-default
    // soft chime — a rhythm you can follow with eyes closed. Chime and haptics
    // are guidance, not motion, so Reduce Motion leaves them untouched.
    var pace by rememberSaveable { mutableIntStateOf(4) }
    var hapticsOn by remember { mutableStateOf(Chime.breatheHapticsEnabled) }
    var chimeOn by remember { mutableStateOf(Chime.breatheChimeEnabled) }
    val detail = when (preset) {
        BreathePreset.Box -> stringResource(R.string.breathe_box_detail)
        BreathePreset.Color -> stringResource(R.string.breathe_color_detail)
        BreathePreset.Reset -> stringResource(R.string.breathe_reset_detail)
    }
    ImmersiveBreatheFrame(title, detail, intro, onBack) {
        BreatheEngine(
            preset, Modifier.fillMaxWidth(),
            secondsPerPhase = pace, hapticsOn = hapticsOn, chimeOn = chimeOn,
        )
        BreathePaceControl(pace = pace, onPaceChange = { pace = it })
        BreatheGuidanceCard(
            hapticsOn = hapticsOn,
            chimeOn = chimeOn,
            onHaptics = { hapticsOn = it; Chime.breatheHapticsEnabled = it },
            onChime = { chimeOn = it; Chime.breatheChimeEnabled = it },
        )
        BreatheDoneButton(stringResource(R.string.common_done), onBack)
        BreatheWhyCard(stringResource(R.string.breathe_why))
    }
}

@Composable
private fun ImmersiveBreatheFrame(
    title: String,
    detail: String,
    intro: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    // B27: the breathing screens must not burn an animation clock precisely
    // for users who asked for stillness — no transition exists under RM.
    val drift = restingFloat(reduceMotion, still = 0f, initial = -0.05f, target = 0.08f,
        spec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheBackgroundDrift")
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(FunnelHeaderTop, FunnelHeaderBottom)),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.18f), Color.Transparent)),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.5f, size.height * (0.37f + drift)),
            )
            listOf(0.12f to 0.16f, 0.86f to 0.24f, 0.18f to 0.62f, 0.80f to 0.78f).forEachIndexed { index, point ->
                drawCircle(
                    color = if (index % 2 == 0) Cyan.copy(alpha = 0.24f) else Periwinkle.copy(alpha = 0.24f),
                    radius = 2.dp.toPx(),
                    center = Offset(size.width * point.first, size.height * (point.second + drift * 0.25f)),
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            CereBroTopBar(title = title, subtitle = detail, onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .appear(rise = 12f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    intro,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = TextMuted,
                )
                content()
            }
        }
    }
}

@Composable
private fun BreathePaceControl(pace: Int, onPaceChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.breathe_pace_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardFill)
                .border(1.dp, LineStroke, RoundedCornerShape(24.dp)).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                6 to stringResource(R.string.breathe_pace_gentle),
                4 to stringResource(R.string.breathe_pace_classic),
                8 to stringResource(R.string.breathe_pace_slow),
            ).forEach { (value, label) ->
                val selected = pace == value
                // B63: BrandPrimary, not a third near-brand purple on a primary control.
                val fill by animateColorAsState(if (selected) com.cerebrozen.app.ui.theme.BrandPrimary else Color.Transparent, label = "paceFill")
                Box(
                    Modifier.weight(1f).height(44.dp).clip(CircleShape).background(fill)
                        // B49: selected-state semantics, same fix as ChipWrap.
                        .selectable(selected = selected, role = Role.RadioButton) { onPaceChange(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.substringBefore(" ·"), style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun BreatheGuidanceCard(
    hapticsOn: Boolean,
    chimeOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    onChime: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.breathe_settings_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        BreatheSettingRow(Icons.Outlined.Vibration, stringResource(R.string.breathe_haptics_label), hapticsOn, onHaptics)
        BreatheSettingRow(Icons.Outlined.NotificationsNone, stringResource(R.string.breathe_chime_label), chimeOn, onChime)
    }
}

@Composable
private fun BreatheSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    // B50: one row-level toggleable so the switch reaches TalkBack WITH its
    // label (the unmerged siblings announced a nameless "switch, on") — the
    // same pattern PlanScreen already uses.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(AccentSoft)
                .border(1.dp, LineStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(21.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = TextSoft, modifier = Modifier.weight(1f))
        // Semantics cleared: the row is the one accessible toggle; a second
        // focusable switch inside it would double-announce.
        Box(Modifier.clearAndSetSemantics { }) {
            AppSwitch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun BreatheDoneButton(label: String, onClick: () -> Unit) {
    PrimaryButton(label, modifier = Modifier.fillMaxWidth(), onClick = onClick)
}

@Composable
private fun BreatheWhyCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // B28: WhyThisWorks is deliberately unanimated "so it's the same with or
    // without Reduce Motion" — this sibling now follows the same rule.
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "breatheWhyArrow",
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(if (reduceMotion) Modifier else Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy)))
            .clip(shape).background(CardFill).border(1.dp, LineStroke, shape)
            .clickable { expanded = !expanded }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.common_why_this_works), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = Periwinkle,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation },
            )
        }
        if (expanded) Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = TextMuted)
    }
}
