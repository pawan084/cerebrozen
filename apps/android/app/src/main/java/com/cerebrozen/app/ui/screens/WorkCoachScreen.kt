package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Work coaching — organisation-sponsored members talk a work problem through,
 * then turn the conversation into a plan with a task list.
 *
 * The transcript lives HERE, not on the server (see backend
 * `services/workcoach.py`): work turns are stateless by design so they never
 * enter the wellness chat history, memory, insights or export. The only
 * persisted artefact is the plan, which the existing plan screen renders.
 *
 * Deliberately simpler than TalkScreen — no voice, no widgets, no streaming.
 * The point of this surface is the plan at the end, not the conversation.
 */
private data class WorkTurn(val role: String, val text: String)

@Composable
fun WorkCoachScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    // rememberSaveable, both of them (design system §6): "state that a user
    // typed survives rotation/process death — losing user writing is the worst
    // defect class in this app". A coaching transcript is exactly that, and the
    // server deliberately keeps none of it. Turns flatten to [role, text, …]
    // (MAX_TURNS caps the bundle well under the transaction limit).
    val turnsSaver = listSaver<List<WorkTurn>, String>(
        save = { it.flatMap { t -> listOf(t.role, t.text) } },
        restore = { flat -> flat.chunked(2).map { WorkTurn(it[0], it[1]) } },
    )
    var turns by rememberSaveable(stateSaver = turnsSaver) { mutableStateOf(listOf<WorkTurn>()) }
    var draft by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var crisis by remember { mutableStateOf(false) }
    // The created plan's steps, shown inline so the payoff is visible where the
    // conversation happened; "Open your day" then jumps to the real plan.
    var planSteps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var planTitle by remember { mutableStateOf("") }
    // Captured in composition — userMessage takes a plain String, and a
    // coroutine body cannot call stringResource (the TalkScreen pattern).
    val sendFailed = stringResource(R.string.work_send_failed)
    val planFailed = stringResource(R.string.work_plan_failed)

    fun history(): JSONArray {
        val arr = JSONArray()
        turns.forEach { arr.put(JSONObject().put("role", it.role).put("text", it.text)) }
        return arr
    }

    fun send() {
        val text = draft.trim()
        if (text.isBlank() || busy) return
        busy = true; status = null
        val prior = history()
        turns = turns + WorkTurn("user", text)
        draft = ""
        scope.launch {
            try {
                val res = Api.workChat(prior, text)
                turns = turns + WorkTurn("assistant", res.optString("reply"))
                if (res.optString("risk_level") == "crisis") crisis = true
                planSteps = null   // the conversation moved on; the old preview is stale
            } catch (e: Exception) {
                turns = turns.dropLast(1)
                draft = text   // a failed send keeps its words
                status = e.userMessage(sendFailed)
            } finally { busy = false }
        }
    }

    fun makePlan() {
        if (busy || turns.none { it.role == "user" }) return
        busy = true; status = null
        scope.launch {
            try {
                val plan = Api.workPlan(history())
                planTitle = plan.optString("title")
                val steps = plan.optJSONArray("steps") ?: JSONArray()
                planSteps = (0 until steps.length()).map {
                    val s = steps.getJSONObject(it)
                    s.optString("title") to s.optString("detail")
                }
            } catch (e: Exception) {
                status = e.userMessage(planFailed)
            } finally { busy = false }
        }
    }

    PremiumSubPage(
        // The one-line promise rides the SUBTITLE slot; the bar's big line is
        // the screen's short name. The old order put the two-line work_title
        // sentence into a single-line bar, which ellipsized it to
        // "Talk it through,…" in every locale (2026-08-24 screen review) —
        // a headline whose whole second half is its payoff, cut at the comma.
        stringResource(R.string.work_bar_sub),
        stringResource(R.string.work_eyebrow),
        onBack,
        onUrgent = { onOpen("crisis") },
    ) {
        // The privacy line FIRST — for a corporate surface, "your employer
        // cannot see this" is the sentence everything else depends on. It
        // mirrors the backend boundary (no rows, no org read paths), so it is
        // a description, not a promise.
        Text(
            stringResource(R.string.work_privacy_line),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )

        if (turns.isEmpty()) {
            SectionCard {
                Text(stringResource(R.string.work_empty_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.work_empty_sub), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
        turns.forEach { t ->
            val mine = t.role == "user"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Box(
                    Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (mine) Periwinkle.copy(alpha = 0.16f) else FieldFill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(t.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
        if (crisis) {
            // The door, not just the suffix in the reply text.
            PickChip(selected = false, label = stringResource(R.string.work_crisis_chip)) { onOpen("crisis") }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }

        planSteps?.let { steps ->
            SectionCard {
                Text(planTitle.ifBlank { stringResource(R.string.work_plan_ready) },
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                steps.forEachIndexed { i, (title, detail) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${i + 1}. $title", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        if (detail.isNotBlank()) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.work_open_plan), modifier = Modifier.fillMaxWidth()) {
                    onOpen("plan")
                }
            }
        }

        // ONE primary action (design system §6): the plan is this screen's
        // job, so it alone wears the white pill — two pills side by side had
        // Send and the plan CTA shouting over each other. Send is the shared
        // circular composer control (Talk's anatomy, §5-lifted into Common).
        if (turns.any { it.role == "user" }) {
            PrimaryButton(
                stringResource(R.string.work_make_plan),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { makePlan() }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                AppTextField(
                    draft, { draft = it.take(2000) },
                    label = "",
                    placeholderText = stringResource(R.string.work_composer_hint),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            }
            SendButton(enabled = !busy && draft.isNotBlank(), busy = busy) { send() }
        }
        Text(
            stringResource(R.string.work_boundary_note),
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
    }
}
