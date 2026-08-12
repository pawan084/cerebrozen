package com.cerebrozen.app.ui.breathing

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.screens.Celebrations
import com.cerebrozen.app.ui.screens.CereBroTopBar
import com.cerebrozen.app.ui.screens.PrimaryButton
import com.cerebrozen.app.ui.screens.rememberReduceMotion
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.CardShadow
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.InfoSoft
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.OkSoft
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.Violet
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// Phase tints. These are read as the orb's GLOW, RING and SHADOW colour on a
// themed page (never as the orb's own fill, and never as text), so they follow
// the theme exactly like breatheTint() does on the sibling Breathe screen —
// the earlier "kept by design" set was the retired emerald/indigo palette,
// which sat on a ground the app no longer paints. Getters, so a theme flip
// re-resolves them.
private val InhaleTop: Color get() = Ok
private val InhaleBottom: Color get() = OkSoft
private val HoldTop: Color get() = Cyan
private val HoldBottom: Color get() = InfoSoft
private val ExhaleTop: Color get() = Periwinkle
private val ExhaleBottom: Color get() = AccentSoft

/**
 * The breathing surface — a pattern picker, the paced session, and its summary.
 *
 * [startPattern] skips the picker and begins that pattern immediately. The
 * entry points that use it have already named what they are giving you —
 * Explore's hero, "Begin 2-minute breathing", the Toolkit's reset row and the
 * `cerebro://breathe` nudge all promise a two-minute reset — so putting a
 * chooser in front of them would make four surfaces stop meaning what they say.
 * When a session started that way ends, the screen leaves rather than dropping
 * the user on a picker they never asked for.
 */
@Composable
fun BreathLoopsScreen(onBack: () -> Unit, startPattern: BreathPattern? = null) {
    val context = LocalContext.current
    val model: BreathLoopsViewModel = viewModel(
        factory = BreathLoopsViewModel.factory(context.applicationContext as Application),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeSignal by remember { mutableIntStateOf(0) }

    // Kick the named pattern off once. Keyed on startPattern rather than Unit so
    // a config change does not restart a session that is already running — the
    // ViewModel outlives the composition and still holds it.
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startPattern) {
        if (startPattern != null && !launched) {
            launched = true
            model.start(startPattern)
        }
    }
    // stop() and done() both return the machine to Picker. That is the right
    // destination when the user chose the pattern here, and the wrong one when
    // they arrived mid-promise — so for a direct launch, the end of the session
    // is the end of the screen. Gated on having actually SEEN Active, because
    // the frame between launching and the state arriving is still Picker.
    var wasActive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.core.mode) {
        if (state.core.mode == BreathScreenMode.Active) {
            wasActive = true
        } else if (wasActive && startPattern != null && state.core.mode == BreathScreenMode.Picker) {
            onBack()
        }
    }

    var ttsReady by remember { mutableStateOf(false) }
    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) ttsHolder[0]?.language = Locale.getDefault()
        }
        ttsHolder[0] = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsHolder[0] = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { model.reconcile(); resumeSignal++ }
                Lifecycle.Event.ON_STOP -> ttsHolder[0]?.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val position = state.core.position
    val spokenPhase = position?.let { phaseLabel(it.phase.type) }
    LaunchedEffect(
        state.core.mode, position?.phaseIndex, position?.roundIndex,
        state.core.voiceEnabled, state.core.paused, ttsReady, resumeSignal,
    ) {
        val engine = ttsHolder[0]
        if (state.core.mode == BreathScreenMode.Active && state.core.voiceEnabled &&
            !state.core.paused && ttsReady && position != null
        ) {
            engine?.speak(spokenPhase, TextToSpeech.QUEUE_FLUSH, null, "breath-phase")
        } else {
            engine?.stop()
        }
    }

    BackHandler {
        when (state.core.mode) {
            BreathScreenMode.Active -> model.stop()
            BreathScreenMode.Completed -> model.done()
            BreathScreenMode.Picker -> onBack()
        }
    }

    when (state.core.mode) {
        BreathScreenMode.Picker -> PickerScreen(state, model, onBack)
        BreathScreenMode.Active -> ActiveSession(state.core, model)
        BreathScreenMode.Completed -> CompletionScreen(state.core.selectedPattern, model)
    }
}

@Composable
private fun PickerScreen(state: BreathLoopsUiState, model: BreathLoopsViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CereBroTopBar(
            title = stringResource(R.string.breath_loops_title),
            subtitle = stringResource(R.string.breath_loops_subtitle),
            onBack = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        BreathPattern.entries.forEach { pattern ->
            val selected = pattern == state.core.selectedPattern
            PatternCard(pattern, selected) { model.select(pattern) }
        }
        PrimaryButton(
            text = stringResource(R.string.breath_loops_start, patternName(state.core.selectedPattern)),
            modifier = Modifier.fillMaxWidth(),
        ) { model.start() }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.breath_history_title), style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary, modifier = Modifier.weight(1f))
            if (state.history.isNotEmpty()) {
                // B71: the app's own arm/confirm pattern (patterns hide,
                // memory delete, account delete) — one tap arms, the second
                // deletes, and looking away disarms.
                var armed by remember { mutableStateOf(false) }
                LaunchedEffect(armed) { if (armed) { delay(4_000); armed = false } }
                TextButton(onClick = {
                    if (armed) { model.clearHistory(); armed = false } else armed = true
                }) {
                    Icon(Icons.Outlined.DeleteOutline, null,
                        tint = if (armed) com.cerebrozen.app.ui.theme.Danger else TextMuted2,
                        modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (armed) R.string.breath_history_clear_confirm else R.string.breath_history_clear),
                        color = if (armed) com.cerebrozen.app.ui.theme.Danger else TextMuted2,
                    )
                }
            }
        }
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.breath_history_empty), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            state.history.forEach { record -> HistoryRow(record) }
        }
        Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternCard(pattern: BreathPattern, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(CardFill)
            .background(if (selected) Periwinkle.copy(alpha = 0.055f) else Color.Transparent)
            .border(1.dp, if (selected) Periwinkle.copy(alpha = 0.72f) else LineStroke, shape)
            // B57: selected-state semantics — the check icon's "Selected"
            // cd was the only signal, read out of context after the body.
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(patternName(pattern), style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                modifier = Modifier.weight(1f))
            if (selected) Icon(
                Icons.Outlined.Check, contentDescription = stringResource(R.string.common_selected_cd),
                tint = Periwinkle, modifier = Modifier.size(19.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            pattern.phases.forEach { phase ->
                Text(
                    "${phaseLabel(phase.type)} ${phase.seconds}s",
                    style = MaterialTheme.typography.labelSmall, color = TextSoft,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(VeilWell)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            "${stringResource(R.string.breath_duration_seconds, pattern.plannedDurationSeconds)}  ·  ${stringResource(R.string.breath_rounds, pattern.rounds)}",
            style = MaterialTheme.typography.labelSmall, color = TextMuted2,
        )
    }
}

@Composable
private fun ActiveSession(state: BreathLoopsCoreState, model: BreathLoopsViewModel) {
    val position = requireNotNull(state.position)
    val phase = position.phase
    val colors = phaseGradient(phase.type)
    val reduceMotion = rememberReduceMotion()
    val expanded = orbExpanded(position)
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (expanded) 1.12f else 0.74f,
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = phase.seconds * 900,
            easing = FastOutSlowInEasing,
        ),
        label = "breath-loop-orb-scale",
    )
    val tint by animateColorAsState(
        targetValue = colors.first(),
        animationSpec = if (reduceMotion) snap() else tween(1_400, easing = FastOutSlowInEasing),
        label = "breath-loop-orb-tint",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.34f else 0.18f,
        animationSpec = if (reduceMotion) snap() else tween(1_200, easing = FastOutSlowInEasing),
        label = "breath-loop-glow",
    )
    // Keyed on paused too: pausing cancels the animateTo (the ring freezes at its
    // current sweep), and resuming continues from that value over the remaining
    // seconds instead of snapping back to zero.
    val phaseProgress = remember { Animatable(0f) }
    LaunchedEffect(position.phaseIndex, position.roundIndex, reduceMotion, state.paused) {
        if (state.paused) return@LaunchedEffect
        if (phaseProgress.value >= 1f || state.remainingSeconds >= phase.seconds) phaseProgress.snapTo(0f)
        if (!reduceMotion) {
            val remainingMillis = state.remainingSeconds.coerceIn(1, phase.seconds) * 1_000
            phaseProgress.animateTo(1f, tween(remainingMillis, easing = LinearEasing))
        }
    }
    LaunchedEffect(position.phaseIndex, position.roundIndex) { Haptics.tap() }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val ringProgress = if (reduceMotion) {
        ((phase.seconds - state.remainingSeconds).toFloat() / phase.seconds).coerceIn(0f, 1f)
    } else phaseProgress.value
    val muteVoiceDescription = stringResource(R.string.breath_mute_voice)
    val enableVoiceDescription = stringResource(R.string.breath_enable_voice)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CereBroTopBar(
            title = patternName(position.pattern),
            subtitle = stringResource(R.string.breath_round_progress, position.displayRound, position.pattern.rounds),
            onBack = model::stop,
            trailing = if (state.voiceEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
            trailingLabel = if (state.voiceEnabled) muteVoiceDescription else enableVoiceDescription,
            onTrailingClick = model::toggleVoice,
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (state.paused) stringResource(R.string.breath_paused) else phaseLabel(phase.type),
                style = MaterialTheme.typography.displaySmall, color = TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(282.dp).graphicsLayer {
                        scaleX = scale * 1.18f
                        scaleY = scale * 1.18f
                    }.blur(22.dp).background(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = glowAlpha), Periwinkle.copy(alpha = 0.2f), Color.Transparent),
                        ), CircleShape,
                    ),
                )
                repeat(3) { ring ->
                    Box(
                        Modifier.size((240 + ring * 30).dp).graphicsLayer {
                            val ringScale = if (reduceMotion) 1f else scale * (1f + ring * 0.025f)
                            scaleX = ringScale
                            scaleY = ringScale
                            alpha = 0.20f - ring * 0.035f
                        }.clip(CircleShape).border(1.dp, tint.copy(alpha = 0.34f), CircleShape),
                    )
                }
                Canvas(Modifier.size(300.dp)) {
                    val stroke = 4.dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension / 2f - stroke,
                        style = Stroke(3.dp.toPx()))
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Cyan, Periwinkle, Accent2),
                        ), startAngle = -90f,
                        sweepAngle = 360f * ringProgress, useCenter = false,
                        style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
                Box(
                    Modifier.size(214.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .shadow(22.dp, CircleShape, clip = false,
                            ambientColor = tint.copy(alpha = 0.55f), spotColor = Periwinkle.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(
                            // Constant art: the orb carries [Ink] as its count in
                            // both themes, so it must stay a light object.
                            listOf(Color.White, Cream, PeriwinkleSoft, Iris, Violet),
                        )).border(1.dp, Color.White.copy(alpha = 0.48f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.remainingSeconds.toString(), style = MaterialTheme.typography.displaySmall,
                            color = Ink, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.breathe_seconds_remaining),
                            style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.72f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(patternName(position.pattern), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(
                if (state.voiceEnabled) stringResource(R.string.breath_voice_on) else stringResource(R.string.breath_voice_off),
                style = MaterialTheme.typography.labelSmall, color = TextMuted2,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(CardFill).border(1.dp, Periwinkle.copy(alpha = 0.55f), RoundedCornerShape(27.dp))
                    .clickable(onClick = model::togglePause),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    null, tint = TextPrimary, modifier = Modifier.size(19.dp),
                )
                Text(
                    stringResource(if (state.paused) R.string.breath_resume else R.string.breath_pause),
                    style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                )
            }
            Box(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(27.dp))
                    .clickable(onClick = model::stop),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.breath_stop), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        }
        }
    }
}

@Composable
private fun CompletionScreen(pattern: BreathPattern, model: BreathLoopsViewModel) {
    LaunchedEffect(Unit) {
        // Celebrations.trigger() fires Haptics.success() itself — a second
        // direct call here double-pulsed one moment, against Haptics.kt's own
        // "never fire it twice for the same moment" rule (audit B8).
        Celebrations.trigger()
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(Periwinkle), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, tint = OnPrimary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.breath_complete_title), style = MaterialTheme.typography.displaySmall,
            color = TextPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.breath_complete_body, pattern.rounds, patternName(pattern)),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
        PrimaryButton(stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth(), onClick = model::done)
    }
}

@Composable
private fun HistoryRow(record: BreathingSessionRecord) {
    val date = remember(record.completedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.completedAtEpochMillis))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(patternName(record.pattern), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(date, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(pluralStringResource(R.plurals.breath_history_rounds, record.rounds, record.rounds),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle)
    }
}

private fun orbExpanded(position: BreathingPosition): Boolean = when (position.phase.type) {
    BreathPhaseType.Inhale -> true
    BreathPhaseType.Exhale -> false
    BreathPhaseType.Hold -> position.phaseIndex > 0 && position.pattern.phases[position.phaseIndex - 1].type == BreathPhaseType.Inhale
}

private fun phaseGradient(type: BreathPhaseType): List<Color> = when (type) {
    BreathPhaseType.Inhale -> listOf(InhaleTop, InhaleBottom)
    BreathPhaseType.Hold -> listOf(HoldTop, HoldBottom)
    BreathPhaseType.Exhale -> listOf(ExhaleTop, ExhaleBottom)
}

@Composable
private fun phaseLabel(type: BreathPhaseType): String = when (type) {
    BreathPhaseType.Inhale -> stringResource(R.string.breathe_phase_in)
    BreathPhaseType.Hold -> stringResource(R.string.breathe_phase_hold)
    BreathPhaseType.Exhale -> stringResource(R.string.breathe_phase_out)
}

@Composable
private fun patternName(pattern: BreathPattern): String = when (pattern) {
    BreathPattern.Box -> stringResource(R.string.breath_pattern_box)
    BreathPattern.Reset -> stringResource(R.string.breath_pattern_reset)
    BreathPattern.Coherent -> stringResource(R.string.breath_pattern_coherent)
    BreathPattern.Triangle -> stringResource(R.string.breath_pattern_triangle)
}

@Composable
private fun patternDescription(pattern: BreathPattern): String = when (pattern) {
    BreathPattern.Box -> stringResource(R.string.breath_pattern_box_desc)
    BreathPattern.Reset -> stringResource(R.string.breath_pattern_reset_desc)
    BreathPattern.Coherent -> stringResource(R.string.breath_pattern_coherent_desc)
    BreathPattern.Triangle -> stringResource(R.string.breath_pattern_triangle_desc)
}
