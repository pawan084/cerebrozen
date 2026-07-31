package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate

internal data class UserGoal(
    val id: String,
    val title: String,
    val why: String,
    /** active | achieved | released — the screen now asks for resolved ones too. */
    val status: String = "active",
)

/** No streak field, deliberately: [recentDays] is a 7-day window, so a gap is
 * just a gap. The server never sends a chain and this screen never draws one. */
internal data class UserHabit(
    val id: String,
    val title: String,
    val cue: String,
    val recentDays: List<String>,
    val doneToday: Boolean,
)

internal fun parseGoals(arr: JSONArray): List<UserGoal> =
    (0 until arr.length()).mapNotNull { i ->
        val g = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = g.optString("id")
        if (id.isBlank()) null
        else UserGoal(
            id, g.optString("title"), g.optString("why"),
            g.optString("status").ifBlank { "active" },
        )
    }

internal fun parseHabits(arr: JSONArray): List<UserHabit> =
    (0 until arr.length()).mapNotNull { i ->
        val h = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = h.optString("id")
        if (id.isBlank()) return@mapNotNull null
        val days = h.optJSONArray("recent_days")
        UserHabit(
            id,
            h.optString("title"),
            h.optString("cue"),
            (0 until (days?.length() ?: 0)).map { days!!.optString(it) },
            h.optBoolean("done_today"),
        )
    }

/** The last seven calendar days, oldest first. */
internal fun lastSevenDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
    (6 downTo 0).map { today.minusDays(it.toLong()) }

/**
 * The two things in this app the USER defines — everything else (plan,
 * patterns, suggestions) is generated for them.
 *
 * Deliberately not gamified: seven day-dots and a plain count, no streak. A
 * broken-chain counter on mental-health activity is exactly what the
 * anti-dark-pattern rules here exist to prevent.
 */
// FlowRow is still experimental; the codebase already opts in the same way in
// OnboardingScreen.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    var goals by remember { mutableStateOf<List<UserGoal>>(emptyList()) }
    var habits by remember { mutableStateOf<List<UserHabit>>(emptyList()) }
    var goalDraft by remember { mutableStateOf("") }
    var habitDraft by remember { mutableStateOf("") }
    var cueDraft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.goals_save_failed)

    LaunchedEffect(reload) {
        if (!Session.signedIn) { loading = false; return@LaunchedEffect }
        runCatching { parseGoals(Api.goals(includeResolved = true)) }.onSuccess { goals = it }
        runCatching { parseHabits(Api.habits()) }.onSuccess { habits = it }
        loading = false
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { error = null }
                .onFailure { error = it.message ?: saveFailed }
            reload++
        }
    }

    SubPage(stringResource(R.string.goals_eyebrow), stringResource(R.string.goals_title), onBack) {
        Text(stringResource(R.string.goals_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }

        if (!Session.signedIn) {
            SectionCard {
                Text(stringResource(R.string.goals_signed_out),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            return@SubPage
        }
        if (loading) {
            Text(stringResource(R.string.patterns_loading),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }

        val active = goals.filter { it.status == "active" }
        val resolved = goals.filter { it.status != "active" }

        SectionCard {
            Text(stringResource(R.string.goals_section),
                style = MaterialTheme.typography.titleMedium, color = TextSoft)
            if (active.isEmpty()) {
                Text(stringResource(R.string.goals_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            active.forEachIndexed { i, goal ->
                // A hairline between entries: two goals used to run together with
                // no boundary at all, which is unreadable when they share a title
                // — and this account has two called "Sleep before midnight".
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LineStroke))
                }
                Column(Modifier.fillMaxWidth()) {
                    Text(goal.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (goal.why.isNotBlank()) {
                        Text(goal.why, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    // FlowRow, not Row: three labelled actions do not fit one
                    // 720px line (found on a real CPH2681 — "Let it go" broke
                    // across three lines), and Hindi is longer still. Wrapping
                    // is the only thing that survives both.
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = { act { Api.decomposeGoal(goal.id) } }) {
                            Text(stringResource(R.string.goals_make_plan), color = Periwinkle, maxLines = 1)
                        }
                        TextButton(onClick = { act { Api.setGoalStatus(goal.id, "achieved") } }) {
                            Text(stringResource(R.string.goals_done), color = Ok, maxLines = 1)
                        }
                        // Letting a goal go is an outcome, not a failure.
                        TextButton(onClick = { act { Api.setGoalStatus(goal.id, "released") } }) {
                            Text(stringResource(R.string.goals_release), color = TextMuted, maxLines = 1)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = goalDraft,
                onValueChange = { goalDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.goals_add_hint)) },
                singleLine = true,
            )
            TextButton(
                enabled = goalDraft.isNotBlank(),
                onClick = {
                    val title = goalDraft.trim()
                    act { Api.addGoal(title) }
                    goalDraft = ""
                },
            ) { Text(stringResource(R.string.common_add), color = Periwinkle) }
        }

        // What you finished or let go, and the way back.
        //
        // "Done" and "Let it go" sit one tap away from "Make today's plan" and
        // used to remove a goal from the app permanently — the server kept it and
        // has always offered `include_resolved`, but nothing here ever asked. Undo
        // rather than a confirm dialog: retiring a goal is usually deliberate, so
        // the fix is to make a mis-tap cheap, not to interrogate everyone who
        // means it.
        if (resolved.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.goals_resolved_section),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.goals_resolved_body),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                resolved.forEachIndexed { i, goal ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LineStroke))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(goal.title, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            Text(
                                stringResource(
                                    if (goal.status == "achieved") R.string.goals_status_achieved
                                    else R.string.goals_status_released,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (goal.status == "achieved") Ok else TextMuted2,
                            )
                        }
                        TextButton(onClick = { act { Api.setGoalStatus(goal.id, "active") } }) {
                            Text(stringResource(R.string.goals_restore), color = Periwinkle, maxLines = 1)
                        }
                    }
                }
            }
        }

        SectionCard {
            Text(stringResource(R.string.habits_section),
                style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(stringResource(R.string.habits_body),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (habits.isEmpty()) {
                Text(stringResource(R.string.habits_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            habits.forEach { habit ->
                val countLabel = stringResource(R.string.habits_count, habit.recentDays.size)
                Column(Modifier.fillMaxWidth()) {
                    Text(habit.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (habit.cue.isNotBlank()) {
                        Text(habit.cue, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics { contentDescription = countLabel },
                    ) {
                        lastSevenDays().forEach { day ->
                            val hit = habit.recentDays.contains(day.toString())
                            Box(
                                Modifier.size(22.dp).clip(CircleShape)
                                    .background(if (hit) Periwinkle else CardFill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfWeek.name.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hit) Ink else TextMuted,
                                )
                            }
                        }
                    }
                    Text(countLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    TextButton(onClick = { act { Api.setHabitToday(habit.id, !habit.doneToday) } }) {
                        Text(
                            if (habit.doneToday) stringResource(R.string.habits_done_today)
                            else stringResource(R.string.habits_mark),
                            color = Periwinkle,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = habitDraft,
                onValueChange = { habitDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.habits_add_hint)) },
                singleLine = true,
            )
            // An implementation intention gets its own field — it is the one
            // habit mechanism with decent evidence behind it.
            OutlinedTextField(
                value = cueDraft,
                onValueChange = { cueDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.habits_cue_hint)) },
                singleLine = true,
            )
            TextButton(
                enabled = habitDraft.isNotBlank(),
                onClick = {
                    val title = habitDraft.trim()
                    val cue = cueDraft.trim()
                    act { Api.addHabit(title, cue) }
                    habitDraft = ""; cueDraft = ""
                },
            ) { Text(stringResource(R.string.common_add), color = Periwinkle) }
        }
    }
}
