package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
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
 * parity): rationale, optimistic step toggles, regenerate from fresh signals.
 * Re-skinned to the teammate's plan look — a gradient rationale hero and one
 * numbered gradient card per step — rebuilt on our design tokens/components. */
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

    val defaultTitle = stringResource(R.string.plan_default_title)
    PremiumSubPage(
        stringResource(R.string.plan_eyebrow),
        plan?.optString("title")?.ifBlank { defaultTitle } ?: defaultTitle,
        onBack,
    ) {
        val p = plan
        if (p == null && loadError) {
            PremiumStateCard(
                icon = Icons.Outlined.CloudOff,
                message = stringResource(R.string.plan_load_error),
                accent = com.cerebrozen.app.ui.theme.Danger,
                actionLabel = stringResource(R.string.common_try_again),
                onAction = { load() },
            )
        } else if (p == null) {
            PremiumStateCard(
                icon = Icons.Outlined.AutoAwesome,
                message = stringResource(R.string.plan_loading),
            )
        } else {
            // Rationale hero — a photographic gradient card in the teammate's look.
            HeroCard(
                kind = "program",
                eyebrow = stringResource(R.string.plan_hero_eyebrow),
                title = p.optString("focus").ifBlank { stringResource(R.string.today_plan_eyebrow) },
                subtitle = p.optString("rationale")
                    .ifBlank {
                        stringResource(
                            R.string.plan_rationale_fallback,
                            p.optString("focus").ifBlank { stringResource(R.string.plan_your_goals) },
                        )
                    },
            ) {
                Text(
                    stringResource(R.string.plan_steps_summary, steps.count { it.done }, steps.size),
                    style = MaterialTheme.typography.labelSmall, color = TextSoft,
                )
            }

            if (steps.isEmpty()) {
                PremiumStateCard(
                    icon = Icons.Outlined.AutoAwesome,
                    message = stringResource(R.string.plan_no_steps),
                    accent = Periwinkle,
                )
            }

            // One numbered gradient card per step — render ALL steps (no take()).
            steps.forEachIndexed { index, step ->
                Row(
                    Modifier.fillMaxWidth()
                        .glass()
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
                        )
                        .padding(cardPadding()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gradient step thumbnail carrying the step's position.
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                            .background(Brush.linearGradient(listOf(Periwinkle, PeriwinkleDeep))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            step.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSoft,
                            textDecoration = if (step.done) TextDecoration.LineThrough else null,
                        )
                        if (step.detail.isNotBlank()) {
                            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    // Reflects state; the Role.Checkbox toggle lives on the row.
                    Checkbox(checked = step.done, onCheckedChange = null)
                }
            }

            val updateFailed = stringResource(R.string.plan_update_failed)
            PrimaryButton(
                text = if (busy) stringResource(R.string.plan_updating) else stringResource(R.string.plan_update_cta),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                busy = true; status = null
                scope.launch {
                    runCatching { adopt(Api.regeneratePlan()) }
                        .onFailure { status = it.message ?: updateFailed }
                    busy = false
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }
    }
}
