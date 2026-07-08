package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class PlanStep(val id: String, val title: String, val detail: String, val order: Int, val done: Boolean)

internal fun parsePlanSteps(plan: JSONObject): List<PlanStep> {
    val arr = plan.optJSONArray("steps") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        PlanStep(s.optString("id"), s.optString("title"), s.optString("detail"), s.optInt("order"), s.optBoolean("done"))
    }.sortedBy { it.order }
}

/** The agentic daily plan (ref DAILY PLAN route; iOS DailyPlanView / web /plan
 * parity): rationale, optimistic step toggles, regenerate from fresh signals. */
@Composable
fun PlanScreen(onBack: () -> Unit) {
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var steps by remember { mutableStateOf(listOf<PlanStep>()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    // Distinct from the initial null-not-error case so a failed load surfaces a
    // retry instead of an eternal "Loading…".
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun adopt(p: JSONObject?) { plan = p; steps = p?.let(::parsePlanSteps) ?: emptyList() }
    fun load() {
        loadError = false
        scope.launch {
            runCatching { adopt(Api.activePlan()) }.onFailure { loadError = true }
        }
    }
    LaunchedEffect(Unit) { load() }

    SubPage(
        "Agentic plan · adapts to your check-ins",
        plan?.optString("title")?.ifBlank { "Daily plan" } ?: "Daily plan",
        onBack,
    ) {
        val p = plan
        if (p == null && loadError) {
            Text(
                "We couldn't load your plan just now.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            PrimaryButton(text = "Try again", modifier = Modifier.fillMaxWidth()) { load() }
        } else if (p == null) {
            Text("Loading your plan…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            SectionCard {
                Text("Why this plan", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    p.optString("rationale").ifBlank { "Built around ${p.optString("focus").ifBlank { "your goals" }}." },
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                Text(
                    "${steps.count { it.done }} of ${steps.size} steps done · updates from your check-ins and sleep diary",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
            SectionCard {
                if (steps.isEmpty()) {
                    Text(
                        "No steps yet — update your plan to generate some.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
                steps.forEach { step ->
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = step.done,
                                role = Role.Checkbox,
                                onValueChange = { done ->
                                    // Optimistic; the server response reconciles.
                                    steps = steps.map { if (it.id == step.id) it.copy(done = done) else it }
                                    scope.launch {
                                        runCatching { adopt(Api.togglePlanStep(step.id, done)) }
                                            .onFailure {
                                                steps = steps.map { s -> if (s.id == step.id) s.copy(done = !done) else s }
                                            }
                                    }
                                },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = step.done,
                            onCheckedChange = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                step.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                                textDecoration = if (step.done) TextDecoration.LineThrough else null,
                            )
                            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
            PrimaryButton(
                text = if (busy) "Updating…" else "Update plan from my latest check-ins",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                busy = true; status = null
                scope.launch {
                    runCatching { adopt(Api.regeneratePlan()) }
                        .onFailure { status = it.message ?: "Couldn't update." }
                    busy = false
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }
    }
}
