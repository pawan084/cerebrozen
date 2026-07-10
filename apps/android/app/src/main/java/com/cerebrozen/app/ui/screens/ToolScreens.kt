package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Native tools (iOS ToolsViews + MicroActivities parity): CBT reframe,
// one good thing, intention set, DBT TIPP. Written work mirrors to the
// journal so it feeds reflections/insights like iOS.

@Composable
fun ToolsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) = SubPage("Small resets", "Tools", onBack) {
    Text("Two-minute practices for the moment you're in.",
        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    NavRow("A minute to breathe", "Follow the orb — in for four, out for four", icon = Icons.Outlined.Air) { onOpen("breathing") }
    NavRow("Untangle a thought", "CBT reframe — from stuck to balanced", icon = Icons.Outlined.Psychology) { onOpen("cbt") }
    NavRow("One good thing", "Name something that went right", icon = Icons.Outlined.Star) { onOpen("onegoodthing") }
    NavRow("Tomorrow's intention", "Set one clear point for tomorrow", icon = Icons.Outlined.WbTwilight) { onOpen("intention") }
    NavRow("TIPP skill", "DBT reset for very intense moments", icon = Icons.Outlined.SelfImprovement) { onOpen("tipp") }
}

/** A one-minute paced-breathing exercise: a slow-pulsing orb counts you through
 * in / hold / out / hold, and you can save the practice to your journal. The orb
 * pulse honours Reduce Motion (holds a steady size), mirroring the calm-motion
 * policy elsewhere. */
@Composable
fun BreathingScreen(onBack: () -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val totalSeconds = 120
    val phases = listOf("Breathe in", "Hold", "Breathe out", "Hold")
    val phase = phases[(elapsed / 4) % phases.size]
    val count = 4 - (elapsed % 4)

    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "breathing-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "breathing-orb-scale",
    )
    val orbScale = if (reduceMotion) 1f else pulse

    LaunchedEffect(Unit) {
        while (elapsed < totalSeconds) {
            delay(1000)
            elapsed += 1
        }
    }

    SubPage("A minute to breathe", "Breathing", onBack) {
        Text("Follow the orb — in for four, hold, out for four.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(
            phase,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size((118 + index * 56).dp)
                        .clip(CircleShape)
                        .border(1.dp, LineStroke, CircleShape),
                )
            }
            Box(
                Modifier
                    .size(144.dp)
                    .scale(orbScale)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, Cyan))),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.displaySmall, color = Ink)
            }
        }
        SectionCard(
            onClick = {
                if (!saved) {
                    scope.launch {
                        runCatching { Api.createJournal("Breathing", "Took a minute to breathe and settle.") }
                            .onSuccess { saved = true; Celebrations.trigger() }
                            .onFailure { status = it.message ?: "Couldn't save." }
                    }
                }
            },
        ) {
            Text(if (saved) "Reflection saved" else "Save reflection",
                style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Add this practice to your private journal",
                style = MaterialTheme.typography.labelSmall, color = TextSoft)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        PrimaryButton(text = "Done", modifier = Modifier.fillMaxWidth()) { onBack() }
    }
}

/** A small tool that writes its result to the journal (title + composed body). */
@Composable
private fun JournalingTool(
    eyebrow: String,
    title: String,
    intro: String,
    journalTitle: String,
    onBack: () -> Unit,
    compose: (List<String>) -> String,
    fields: List<Pair<String, String>>,   // label → placeholder-ish hint
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SubPage(eyebrow, title, onBack) {
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        fields.forEachIndexed { i, (label, _) ->
            AppTextField(
                values.value[i],
                { v ->
                    values.value = values.value.toMutableList().also { it[i] = v }
                    saved = false   // editing after a save re-arms the button
                },
                label,
                minLines = 2,
            )
        }
        PrimaryButton(
            text = if (saved) "Saved to your journal" else "Save to journal",
            enabled = !saved && values.value.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            scope.launch {
                runCatching { Api.createJournal(journalTitle, compose(values.value)) }
                    .onSuccess { saved = true; Celebrations.trigger() }
                    .onFailure { status = it.message ?: "Couldn't save." }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

@Composable
fun CbtReframeScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "Untangle a thought",
    title = "From stuck to balanced",
    intro = "Catch the thought, look at it honestly, and write the fairer version. This is the whole skill.",
    journalTitle = "Balanced thought",
    onBack = onBack,
    compose = { v ->
        "The thought: ${v[0]}\n\nWhat supports it: ${v[1]}\n\nWhat doesn't: ${v[2]}\n\nA more balanced thought: ${v[3]}"
    },
    fields = listOf(
        "The thought that's looping" to "",
        "Evidence it's true" to "",
        "Evidence it's not the whole story" to "",
        "A more balanced thought" to "",
    ),
)

@Composable
fun OneGoodThingScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "One good thing",
    title = "Name a small win",
    intro = "Anything counts — a kind word, a finished task, a decent cup of tea. Naming it makes it stick.",
    journalTitle = "One good thing",
    onBack = onBack,
    compose = { v -> "One good thing today: ${v[0]}" },
    fields = listOf("What went right?" to ""),
)

@Composable
fun IntentionScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "Tomorrow's intention",
    title = "One clear point",
    intro = "Not a to-do list — one thing that would make tomorrow feel steadier.",
    journalTitle = "Intention",
    onBack = onBack,
    compose = { v -> "Tomorrow I will: ${v[0]}" },
    fields = listOf("Tomorrow I will…" to ""),
)

/** DBT TIPP — a guided walkthrough, no data collected. */
@Composable
fun TippScreen(onBack: () -> Unit) {
    val steps = listOf(
        Triple("T — Temperature", "Hold something cold, splash cool water on your face, or step into cooler air.", "Cold triggers the dive reflex and slows your heart rate fast."),
        Triple("I — Intense exercise", "60–90 seconds of anything vigorous: star jumps, fast stairs, a brisk walk.", "Burns off the adrenaline that keeps the alarm ringing."),
        Triple("P — Paced breathing", "Exhale longer than you inhale: in for 4, out for 6–8, for a minute.", "Long exhales switch on the calming branch of your nervous system."),
        Triple("P — Paired muscle relaxation", "Tense a muscle group as you inhale, release completely as you exhale.", "Teaches the body the difference between holding and letting go."),
    )
    var idx by remember { mutableIntStateOf(0) }
    val (heading, how, why) = steps[idx]
    SubPage("For very intense moments", "TIPP", onBack) {
        Text(
            "A DBT skill for when emotion is at a 9 or 10 and thinking feels impossible. Work the four steps in order.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        SectionCard {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = Cyan)
            Text(how, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Text(why, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = idx > 0, onClick = { idx-- }) { Text("Previous", color = TextMuted) }
            Text("${idx + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (idx < steps.size - 1) {
                TextButton(onClick = { idx++ }) { Text("Next", color = Periwinkle) }
            } else {
                TextButton(onClick = onBack) { Text("Done — steadier", color = Ok) }
            }
        }
        Text("If the urge to hurt yourself is present, please also reach out — Urgent support lives in the You tab.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
