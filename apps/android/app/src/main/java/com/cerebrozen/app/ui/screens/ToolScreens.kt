package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch

// Native tools (iOS ToolsViews + MicroActivities parity): the journaling
// breathing practice, CBT reframe, and DBT TIPP. Written work mirrors to the
// journal so it feeds reflections/insights like iOS. The Tools hub itself
// merged into ToolkitScreen (REDESIGN §2.2); the one-field tools (one good
// thing, intention) became Journal quick-entry chips.

/** A guided breathing practice you can save to your journal. The pacing orb is
 * the shared [BreatheEngine] (Box preset) — this screen adds the ambience bed
 * and the reflection save on top. */
@Composable
fun BreathingScreen(onBack: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    SubPage("A minute to breathe", "Breathing", onBack) {
        ToolAmbienceEffect(R.raw.drone)
        Text("Follow the orb — in for four, hold, out for four.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AmbienceToggle()
        BreatheEngine(BreathePreset.Box, Modifier.fillMaxWidth())
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
        WhyThisWorks(
            "Paced breathing is used in clinical distress-tolerance and relaxation protocols. " +
                "Slowing the breath activates the body's calming response.",
        )
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
    provenance: String? = null,           // "why this works" footer (REDESIGN §2.4)
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SubPage(eyebrow, title, onBack) {
        ToolAmbienceEffect(R.raw.rain)
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AmbienceToggle()
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
        provenance?.let { WhyThisWorks(it) }
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
    provenance = "Reframing is a core cognitive-behavioural (CBT) technique — the approach " +
        "with the strongest evidence among app-delivered tools (Linardon et al. 2024, World Psychiatry).",
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
        WhyThisWorks(
            "TIPP comes from dialectical behaviour therapy (DBT) — skills clinicians teach " +
                "for riding out intense moments.",
        )
    }
}
