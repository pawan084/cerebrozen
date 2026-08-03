package com.cerebrozen.app.ui.breathing

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.cerebrozen.app.ui.screens.PrimaryButton
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.CardShadow
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val InhaleTop = Color(0xFF34D399)
private val InhaleBottom = Color(0xFF10B981)
private val HoldTop = Color(0xFF22D3EE)
private val HoldBottom = Color(0xFF06B6D4)
private val ExhaleTop = Color(0xFFA78BFA)
private val ExhaleBottom = Color(0xFF8B5CF6)

@Composable
fun BreathLoopsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model: BreathLoopsViewModel = viewModel(
        factory = BreathLoopsViewModel.factory(context.applicationContext as Application),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeSignal by remember { mutableIntStateOf(0) }

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
        state.core.voiceEnabled, ttsReady, resumeSignal,
    ) {
        val engine = ttsHolder[0]
        if (state.core.mode == BreathScreenMode.Active && state.core.voiceEnabled && ttsReady && position != null) {
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeaderButton(onBack)
        Text(stringResource(R.string.breath_loops_title), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Text(stringResource(R.string.breath_loops_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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
                TextButton(onClick = model::clearHistory) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = TextMuted2, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.breath_history_clear), color = TextMuted2)
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

@Composable
private fun PatternCard(pattern: BreathPattern, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) ChipFill else CardFill)
            .border(if (selected) 2.dp else 1.dp, if (selected) Periwinkle else LineStroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(patternName(pattern), style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.breath_rounds, pattern.rounds), color = Periwinkle,
                style = MaterialTheme.typography.labelSmall)
        }
        Text(patternDescription(pattern), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pattern.phases.forEach { phase ->
                Text(
                    "${phaseLabel(phase.type)} ${phase.seconds}s",
                    style = MaterialTheme.typography.labelSmall, color = TextSoft,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(VeilWell)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        Text(stringResource(R.string.breath_duration_seconds, pattern.plannedDurationSeconds),
            style = MaterialTheme.typography.labelSmall, color = TextMuted2)
    }
}

@Composable
private fun ActiveSession(state: BreathLoopsCoreState, model: BreathLoopsViewModel) {
    val position = requireNotNull(state.position)
    val phase = position.phase
    val targetSize = if (orbExpanded(position)) 220.dp else 132.dp
    val orbSize by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetSize,
        animationSpec = tween(phase.seconds * 1_000, easing = LinearEasing),
        label = "breath-orb-size",
    )
    val colors = phaseGradient(phase.type)
    val muteVoiceDescription = stringResource(R.string.breath_mute_voice)
    val enableVoiceDescription = stringResource(R.string.breath_enable_voice)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(model::stop)
            Text(
                stringResource(R.string.breath_round_progress, position.displayRound, position.pattern.rounds),
                color = TextMuted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(VeilWell).clickable { model.toggleVoice() }
                    .semantics {
                        role = Role.Button
                        contentDescription = if (state.voiceEnabled) muteVoiceDescription else enableVoiceDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.voiceEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    null, tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(280.dp).clip(CircleShape).background(colors.first().copy(alpha = 0.10f)))
            Box(
                Modifier.size(orbSize).clip(CircleShape)
                    .background(Brush.linearGradient(colors))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.remainingSeconds.toString(), style = MaterialTheme.typography.displaySmall,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Text(phaseLabel(phase.type), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Text(patternName(position.pattern), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(
            if (state.voiceEnabled) stringResource(R.string.breath_voice_on) else stringResource(R.string.breath_voice_off),
            style = MaterialTheme.typography.labelSmall, color = TextMuted2,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = model::stop, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.breath_stop), color = TextMuted)
        }
    }
}

@Composable
private fun CompletionScreen(pattern: BreathPattern, model: BreathLoopsViewModel) {
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
private fun HeaderButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(VeilWell).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.ArrowBackIosNew, stringResource(R.string.common_back), tint = TextPrimary,
            modifier = Modifier.size(20.dp))
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
        Text(stringResource(R.string.breath_history_rounds, record.rounds),
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
    BreathPattern.FourSevenEight -> stringResource(R.string.breath_pattern_478)
    BreathPattern.Coherent -> stringResource(R.string.breath_pattern_coherent)
    BreathPattern.Triangle -> stringResource(R.string.breath_pattern_triangle)
}

@Composable
private fun patternDescription(pattern: BreathPattern): String = when (pattern) {
    BreathPattern.Box -> stringResource(R.string.breath_pattern_box_desc)
    BreathPattern.FourSevenEight -> stringResource(R.string.breath_pattern_478_desc)
    BreathPattern.Coherent -> stringResource(R.string.breath_pattern_coherent_desc)
    BreathPattern.Triangle -> stringResource(R.string.breath_pattern_triangle_desc)
}
