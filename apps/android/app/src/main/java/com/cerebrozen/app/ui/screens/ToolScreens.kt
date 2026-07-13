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
import androidx.compose.ui.res.stringResource
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

    PremiumSubPage(stringResource(R.string.breathing_eyebrow), stringResource(R.string.breathing_title), onBack) {
        ToolAmbienceEffect(R.raw.drone)
        Text(stringResource(R.string.breathing_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AmbienceToggle()
        BreatheEngine(BreathePreset.Box, Modifier.fillMaxWidth())
        val journalTitle = stringResource(R.string.breathing_journal_title)
        val journalBody = stringResource(R.string.breathing_journal_body)
        val saveFailed = stringResource(R.string.common_save_failed)
        SectionCard(
            onClick = {
                if (!saved) {
                    scope.launch {
                        runCatching { Api.createJournal(journalTitle, journalBody) }
                            .onSuccess { saved = true; Celebrations.trigger() }
                            .onFailure { status = it.message ?: saveFailed }
                    }
                }
            },
        ) {
            Text(if (saved) stringResource(R.string.breathing_saved) else stringResource(R.string.breathing_save),
                style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(stringResource(R.string.breathing_save_hint),
                style = MaterialTheme.typography.labelSmall, color = TextSoft)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        PrimaryButton(text = stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth()) { onBack() }
        WhyThisWorks(stringResource(R.string.breathe_why))
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
    PremiumSubPage(eyebrow, title, onBack) {
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
        val saveFailed = stringResource(R.string.common_save_failed)
        PrimaryButton(
            text = if (saved) stringResource(R.string.tool_saved_cta) else stringResource(R.string.tool_save_cta),
            enabled = !saved && values.value.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            scope.launch {
                runCatching { Api.createJournal(journalTitle, compose(values.value)) }
                    .onSuccess { saved = true; Celebrations.trigger() }
                    .onFailure { status = it.message ?: saveFailed }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        provenance?.let { WhyThisWorks(it) }
    }
}

@Composable
fun CbtReframeScreen(onBack: () -> Unit) {
    val composeTemplate = stringResource(R.string.cbt_compose_format)
    JournalingTool(
        eyebrow = stringResource(R.string.cbt_eyebrow),
        title = stringResource(R.string.cbt_title),
        intro = stringResource(R.string.cbt_intro),
        journalTitle = stringResource(R.string.cbt_journal_title),
        onBack = onBack,
        compose = { v -> composeTemplate.format(v[0], v[1], v[2], v[3]) },
        fields = listOf(
            stringResource(R.string.cbt_field_thought) to "",
            stringResource(R.string.cbt_field_supports) to "",
            stringResource(R.string.cbt_field_against) to "",
            stringResource(R.string.cbt_field_balanced) to "",
        ),
        provenance = stringResource(R.string.cbt_why),
    )
}

/** DBT TIPP — a guided walkthrough, no data collected. */
@Composable
fun TippScreen(onBack: () -> Unit) {
    val steps = listOf(
        Triple(stringResource(R.string.tipp_step1_title), stringResource(R.string.tipp_step1_how), stringResource(R.string.tipp_step1_why)),
        Triple(stringResource(R.string.tipp_step2_title), stringResource(R.string.tipp_step2_how), stringResource(R.string.tipp_step2_why)),
        Triple(stringResource(R.string.tipp_step3_title), stringResource(R.string.tipp_step3_how), stringResource(R.string.tipp_step3_why)),
        Triple(stringResource(R.string.tipp_step4_title), stringResource(R.string.tipp_step4_how), stringResource(R.string.tipp_step4_why)),
    )
    var idx by remember { mutableIntStateOf(0) }
    val (heading, how, why) = steps[idx]
    PremiumSubPage(stringResource(R.string.tipp_eyebrow), stringResource(R.string.tipp_title), onBack) {
        Text(
            stringResource(R.string.tipp_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        SectionCard {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = Cyan)
            Text(how, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Text(why, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = idx > 0, onClick = { idx-- }) { Text(stringResource(R.string.tipp_previous), color = TextMuted) }
            Text(stringResource(R.string.tipp_progress, idx + 1, steps.size), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (idx < steps.size - 1) {
                TextButton(onClick = { idx++ }) { Text(stringResource(R.string.common_next), color = Periwinkle) }
            } else {
                TextButton(onClick = onBack) { Text(stringResource(R.string.tipp_done), color = Ok) }
            }
        }
        Text(stringResource(R.string.tipp_urge_note),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
        WhyThisWorks(stringResource(R.string.tipp_why))
    }
}
