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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Chime
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

/** The phase sequence per preset. Box and Color pace with holds; Reset is the
 * gentle onboarding rhythm — in, out, nothing to hold. W27 §4 (Calm study):
 * [secondsPerPhase] is user-selectable — Classic 4s (the long-standing
 * default), Gentle 6s, Slow 8s — scaling every phase equally. */
// i18n: pending — pure function, needs context plumbing (phase labels are user copy;
// unit tests assert them directly).
internal fun breathePhases(preset: BreathePreset, secondsPerPhase: Int = 4): List<BreathPhase> = when (preset) {
    BreathePreset.Reset -> listOf(
        BreathPhase("Breathe in", secondsPerPhase, expanded = true),
        BreathPhase("Breathe out", secondsPerPhase, expanded = false),
    )
    BreathePreset.Box, BreathePreset.Color -> listOf(
        BreathPhase("Breathe in", secondsPerPhase, expanded = true),
        BreathPhase("Hold", secondsPerPhase, expanded = true),
        BreathPhase("Breathe out", secondsPerPhase, expanded = false),
        BreathPhase("Hold", secondsPerPhase, expanded = false),
    )
}

/** Orb tint per phase — the Color preset shifts through the calm accents; the
 * other presets hold the breathing-orb cyan. Pure, so the cycle is testable. */
internal fun breatheTint(preset: BreathePreset, phase: Int): Color = when (preset) {
    BreathePreset.Color -> listOf(Cyan, Teal, Periwinkle, PeriwinkleSoft)[phase % 4]
    else -> Cyan
}

// `label` is an internal, unlocalized phase id (see BreathPhase) — safe to match on.
private fun playBreathingCue(phase: BreathPhase) {
    when (phase.label) {
        "Breathe in" -> Chime.playBreathCue(inhale = true)
        "Breathe out" -> Chime.playBreathCue(inhale = false)
        // Hold keeps the exact chime it has always rung; playHoldCue only deviates
        // if a real `breathe.hold` cue has been uploaded.
        else -> Chime.playHoldCue()
    }
}

/** The shared pacing orb: phase label above, a per-second count inside the orb,
 * a quiet breaths tally below. The orb is a function of the phase (expand on
 * inhale, hold, contract on exhale) — never a free-running pulse — and holds a
 * steady size under Reduce Motion while the label and count keep guiding. */
@Composable
fun BreatheEngine(
    preset: BreathePreset,
    modifier: Modifier = Modifier,
    secondsPerPhase: Int = 4,
    hapticsOn: Boolean = true,
    chimeOn: Boolean = false,
    compact: Boolean = false,
) {
    val phases = remember(preset, secondsPerPhase) { breathePhases(preset, secondsPerPhase) }
    var phase by remember(preset, secondsPerPhase) { mutableIntStateOf(0) }
    var count by remember(preset, secondsPerPhase) { mutableIntStateOf(phases.first().seconds) }
    var breaths by remember(preset) { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()

    // One pacer for every preset: a 1-second tick counts the phase down, then
    // advances it. A gentle haptic marks each phase change — a rhythm you can
    // follow with eyes closed; firmer on the active breaths, softer on holds.
    // W27 §4: the haptic is now user-toggleable, and an OFF-by-default soft
    // chime can mark phase changes too. Both are guidance, not motion —
    // Reduce Motion deliberately leaves them alone.
    LaunchedEffect(preset, secondsPerPhase, hapticsOn, chimeOn) {
        if (chimeOn) playBreathingCue(phases[phase])
        while (true) {
            delay(1_000)
            if (count > 1) {
                count -= 1
            } else {
                val next = (phase + 1) % phases.size
                phase = next
                count = phases[next].seconds
                if (next == 0) breaths += 1
                if (hapticsOn) Haptics.soft(if (phases[next].label.startsWith("Breathe")) 0.5f else 0.3f)
                if (chimeOn) playBreathingCue(phases[next])
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
        listOf(Color(0xFFF9FBFF), Color(0xFFDDE8FF), Color(0xFF9EC9FF), Color(0xFF7A5CFF))
    } else {
        listOf(Color.White, Color(0xFFDDE8FF), Color(0xFF64C9FF), Color(0xFF7A5CFF), Color(0xFFB18CFF))
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = phases[phase].label,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                else (fadeIn(tween(500)) + scaleIn(initialScale = 0.94f)) togetherWith
                    (fadeOut(tween(320)) + scaleOut(targetScale = 1.04f))
            },
            label = "breathingInstruction",
        ) { instruction ->
            Text(
                instruction,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = instructionSize, fontWeight = FontWeight.SemiBold),
                color = Color.White,
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
                        Brush.radialGradient(listOf(tint.copy(alpha = glowAlpha), Color(0x337A5CFF), Color.Transparent)),
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
                    brush = Brush.sweepGradient(listOf(Color(0xFF64C9FF), Color(0xFFB18CFF), Color(0xFF7A5CFF))),
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            val orbCd = stringResource(R.string.breathe_orb_cd, phases[phase].label)
            Box(
                Modifier
                    .size(orbSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(22.dp, CircleShape, clip = false, ambientColor = tint.copy(alpha = 0.55f), spotColor = Color(0x667A5CFF))
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
    ImmersiveBreatheFrame(eyebrow, title, detail, intro, onBack) {
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
    eyebrow: String,
    title: String,
    detail: String,
    intro: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val ambient = rememberInfiniteTransition(label = "breatheBackground")
    val drift by ambient.animateFloat(
        initialValue = -0.05f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheBackgroundDrift",
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0D1424), Color(0xFF182447), Color(0xFF241A4A))),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x2E64C9FF), Color.Transparent)),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.5f, size.height * (0.37f + if (reduceMotion) 0f else drift)),
            )
            listOf(0.12f to 0.16f, 0.86f to 0.24f, 0.18f to 0.62f, 0.80f to 0.78f).forEachIndexed { index, point ->
                drawCircle(
                    color = if (index % 2 == 0) Color(0x3D64C9FF) else Color(0x3DB18CFF),
                    radius = 2.dp.toPx(),
                    center = Offset(size.width * point.first, size.height * (point.second + if (reduceMotion) 0f else drift * 0.25f)),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.Transparent, Color(0x22000000), Color(0x66000000))),
            ),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BreatheCompactHeader(eyebrow, title, detail, intro, onBack)
            Column(
                Modifier.fillMaxWidth().appear(rise = 12f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun BreatheCompactHeader(eyebrow: String, title: String, detail: String, intro: String, onBack: () -> Unit) {
    val backLabel = stringResource(R.string.common_back)
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                    .clickable(onClickLabel = backLabel, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = backLabel, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
        Column(Modifier.padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Air, contentDescription = null, tint = Color(0xFF64C9FF), modifier = Modifier.size(14.dp))
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.4.sp), color = Color(0xFF9FCBEA))
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = Color(0xFFD2D9EB))
            Text(stringResource(R.string.breathe_estimated_time), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = Color(0xFF9FCBEA))
            Text(intro, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = Color(0xFFAEB9D0), maxLines = 2)
        }
    }
}

@Composable
private fun BreathePaceControl(pace: Int, onPaceChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.breathe_pace_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xB31A2340))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                6 to stringResource(R.string.breathe_pace_gentle),
                4 to stringResource(R.string.breathe_pace_classic),
                8 to stringResource(R.string.breathe_pace_slow),
            ).forEach { (value, label) ->
                val selected = pace == value
                val fill by animateColorAsState(if (selected) Color(0xFF7158E8) else Color.Transparent, label = "paceFill")
                Box(
                    Modifier.weight(1f).height(44.dp).clip(CircleShape).background(fill)
                        .clickable(role = Role.RadioButton, onClickLabel = label) { onPaceChange(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.substringBefore(" ·"), style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else Color(0xFFAEB9D0))
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
            .background(Brush.linearGradient(listOf(Color(0xD91A2340), Color(0xB823294B))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.breathe_settings_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
        BreatheSettingRow(Icons.Outlined.Vibration, stringResource(R.string.breathe_haptics_label), hapticsOn, onHaptics)
        BreatheSettingRow(Icons.Outlined.NotificationsNone, stringResource(R.string.breathe_chime_label), chimeOn, onChime)
    }
}

@Composable
private fun BreatheSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0x227A5CFF))
                .border(1.dp, Color(0x447A5CFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFBFDFFF), modifier = Modifier.size(21.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = Color(0xFFD2D9EB), modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun BreatheDoneButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = if (pressed) 3.dp else 9.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "breatheDoneElevation",
    )
    Box(
        Modifier.fillMaxWidth().height(48.dp).pressScale(pressed, down = 0.97f)
            .shadow(elevation, RoundedCornerShape(24.dp), clip = false, ambientColor = Color(0x557A5CFF), spotColor = Color(0x4464C9FF))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF7A5CFF), Color(0xFF9D7CFF), Color(0xFF64C9FF))))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BreatheWhyCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "breatheWhyArrow",
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy))
            .clip(shape).background(Color(0xB31A2340)).border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable { expanded = !expanded }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.common_why_this_works), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = Color(0xFFB18CFF),
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation },
            )
        }
        if (expanded) Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = Color(0xFFAEB9D0))
    }
}
