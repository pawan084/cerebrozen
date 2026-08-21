package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.DisabledTextInk
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
        // In-flight guard: `saved` only flips on success, so rapid taps
        // before the network returned queued identical writes (audit B15).
        var saving by remember { mutableStateOf(false) }
        SectionCard(
            onClick = {
                if (!saved && !saving) {
                    saving = true
                    scope.launch {
                        runCatching { Api.createJournal(journalTitle, journalBody) }
                            .onSuccess { saved = true; Celebrations.trigger() }
                            .onFailure { status = it.userMessage(saveFailed) }
                        saving = false
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
@OptIn(ExperimentalFoundationApi::class)
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
    // Saveable: four fields of unguarded personal writing were lost on any
    // activity recreation (audit B3).
    val values = rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { ArrayList(it) },
            restore = { it.toList() },
        ),
    ) { mutableStateOf(List(fields.size) { "" }) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PremiumSubPage(eyebrow, title, onBack) {
        ToolAmbienceEffect(R.raw.rain)
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AmbienceToggle()
        SectionCard(quiet = true) {
            fields.forEachIndexed { i, (label, _) ->
                val bringIntoView = remember { BringIntoViewRequester() }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = if (i < fields.lastIndex) 4.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Periwinkle,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextSoft,
                        )
                    }
                    AppTextField(
                        values.value[i],
                        { v ->
                            values.value = values.value.toMutableList().also { it[i] = v }
                            saved = false
                        },
                        label = "",
                        minLines = 2,
                        placeholderText = stringResource(R.string.tool_field_hint),
                        modifier = Modifier
                            .bringIntoViewRequester(bringIntoView)
                            .onFocusChanged { focus ->
                                if (focus.isFocused) scope.launch {
                                    // Wait for the IME inset animation, then
                                    // move the full field above the keyboard.
                                    delay(250)
                                    bringIntoView.bringIntoView()
                                }
                            },
                    )
                }
            }
        }
        val saveFailed = stringResource(R.string.common_save_failed)
        var saving by remember { mutableStateOf(false) }   // B15: one write per tap
        PrimaryButton(
            text = if (saved) stringResource(R.string.tool_saved_cta) else stringResource(R.string.tool_save_cta),
            enabled = !saved && !saving && values.value.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            saving = true
            scope.launch {
                runCatching { Api.createJournal(journalTitle, compose(values.value)) }
                    .onSuccess {
                        saved = true
                        values.value = List(fields.size) { "" }
                        Celebrations.trigger()
                    }
                    .onFailure { status = it.userMessage(saveFailed) }
                saving = false
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

/**
 * Two one-field journaling tools, salvaged from PR #2 (3 weeks stale, pre-i18n and
 * pre-Dawn, so the branch itself was not mergeable). Only the prompts survived; the
 * rendering is today's [JournalingTool], the copy lives in strings.xml, and each
 * carries the "why this works" provenance every other tool here has.
 */
@Composable
fun OneGoodThingScreen(onBack: () -> Unit) {
    val composeTemplate = stringResource(R.string.onegood_compose_format)
    JournalingTool(
        eyebrow = stringResource(R.string.onegood_eyebrow),
        title = stringResource(R.string.onegood_title),
        intro = stringResource(R.string.onegood_intro),
        journalTitle = stringResource(R.string.onegood_journal_title),
        onBack = onBack,
        compose = { v -> composeTemplate.format(v[0]) },
        fields = listOf(stringResource(R.string.onegood_field) to ""),
        provenance = stringResource(R.string.onegood_why),
    )
}

@Composable
fun IntentionScreen(onBack: () -> Unit) {
    val composeTemplate = stringResource(R.string.intention_compose_format)
    JournalingTool(
        eyebrow = stringResource(R.string.intention_eyebrow),
        title = stringResource(R.string.intention_title),
        intro = stringResource(R.string.intention_intro),
        journalTitle = stringResource(R.string.intention_journal_title),
        onBack = onBack,
        compose = { v -> composeTemplate.format(v[0]) },
        fields = listOf(stringResource(R.string.intention_field) to ""),
        provenance = stringResource(R.string.intention_why),
    )
}

/** DBT TIPP — a guided walkthrough, no data collected. */
@Composable
fun TippScreen(onBack: () -> Unit, onUrgent: () -> Unit = {}) {
    val steps = listOf(
        Triple(stringResource(R.string.tipp_step1_title), stringResource(R.string.tipp_step1_how), stringResource(R.string.tipp_step1_why)),
        Triple(stringResource(R.string.tipp_step2_title), stringResource(R.string.tipp_step2_how), stringResource(R.string.tipp_step2_why)),
        Triple(stringResource(R.string.tipp_step3_title), stringResource(R.string.tipp_step3_how), stringResource(R.string.tipp_step3_why)),
        Triple(stringResource(R.string.tipp_step4_title), stringResource(R.string.tipp_step4_how), stringResource(R.string.tipp_step4_why)),
    )
    var idx by rememberSaveable { mutableIntStateOf(0) }   // B42: walkthrough keeps its step
    val (heading, how, why) = steps[idx]
    PremiumSubPage(stringResource(R.string.tipp_eyebrow), stringResource(R.string.tipp_title), onBack) {
        Text(
            stringResource(R.string.tipp_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft,
        )
        val stepAccent = listOf(Cyan, Periwinkle, Ok, Cyan)[idx]
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(stepAccent.copy(alpha = 0.10f), CardFill),
                    ),
                )
                .border(1.dp, stepAccent.copy(alpha = 0.30f), RoundedCornerShape(26.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(stepAccent.copy(alpha = 0.16f))
                        .border(1.dp, stepAccent.copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = stepAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.tipp_progress, idx + 1, steps.size),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(heading, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
            Text(how, style = MaterialTheme.typography.bodyLarge, color = TextSoft)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(VeilWell).padding(13.dp),
            ) {
                Text(why, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(steps.size) { step ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(if (step <= idx) stepAccent else LineStroke),
                    )
                }
            }
        }
        if (idx < steps.size - 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TippPreviousButton(enabled = idx > 0, modifier = Modifier.weight(1f)) { idx-- }
                PrimaryButton(stringResource(R.string.common_next), modifier = Modifier.weight(1f)) { idx++ }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.tipp_done), modifier = Modifier.fillMaxWidth(), onClick = onBack)
                TippPreviousButton(enabled = true, modifier = Modifier.fillMaxWidth()) { idx-- }
            }
        }
        // This is the one screen in the app that names self-harm, and it is
        // reached at "a 9 or 10 when thinking feels impossible". It used to raise
        // that and then hand the user *directions* — "Urgent support lives in the
        // You tab" — with no crisis affordance anywhere on the screen: back out,
        // find the tab, find the row, from memory, in the worst moment. The house
        // rule is that a risk signal always pairs with a pathway, so the note is
        // now the pathway itself.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Periwinkle.copy(alpha = 0.07f))
                .border(1.dp, Periwinkle.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.tipp_urge_action),
                    role = Role.Button,
                    onClick = onUrgent,
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(stringResource(R.string.tipp_urge_note),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(
                stringResource(R.string.tipp_urge_action),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = Periwinkle,
            )
        }
        WhyThisWorks(stringResource(R.string.tipp_why))
    }
}

@Composable
private fun TippPreviousButton(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
            .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(26.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.tipp_previous),
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) TextPrimary else DisabledTextInk,
            maxLines = 1,
        )
    }
}
