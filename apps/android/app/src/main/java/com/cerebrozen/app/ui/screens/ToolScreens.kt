package com.cerebro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Ok
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch

// Native tools (iOS ToolsViews + MicroActivities parity): CBT reframe,
// one good thing, intention set, DBT TIPP. Written work mirrors to the
// journal so it feeds reflections/insights like iOS.

@Composable
fun ToolsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) = SubPage("Small resets", "Tools", onBack) {
    Text("Two-minute practices for the moment you're in.",
        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    NavRow("Untangle a thought", "CBT reframe — from stuck to balanced", icon = Icons.Outlined.Psychology) { onOpen("cbt") }
    NavRow("One good thing", "Name something that went right", icon = Icons.Outlined.Star) { onOpen("onegoodthing") }
    NavRow("Tomorrow's intention", "Set one clear point for tomorrow", icon = Icons.Outlined.WbTwilight) { onOpen("intention") }
    NavRow("TIPP skill", "DBT reset for very intense moments", icon = Icons.Outlined.SelfImprovement) { onOpen("tipp") }
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
                { v -> values.value = values.value.toMutableList().also { it[i] = v } },
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
                    .onSuccess { saved = true }
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
