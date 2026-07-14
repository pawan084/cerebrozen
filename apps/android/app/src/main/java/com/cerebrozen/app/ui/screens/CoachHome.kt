package com.cerebrozen.app.ui.screens

/* The CereBroZen coaching surfaces: the Today home and the Actions tab.
 *
 * Actions are the product's spine (docs/COACHING_FLOW.md): every session ends
 * in a saved commitment, and this tab is where those commitments live between
 * sessions. ActionsStore keeps them on-device (Session prefs, JSON) — the
 * coach turn wiring appends cards here; the engine remains the source of
 * truth and re-syncs on session close. */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import com.cerebrozen.app.net.Events
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

// ── the on-device commitments store ─────────────────────────────────────────

data class ActionItem(val id: String, val text: String, val status: String)

object ActionsStore {
    private const val PREF_KEY = "coach_actions"
    val items = mutableStateListOf<ActionItem>()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val arr = JSONArray(Session.prefGet(PREF_KEY)?.ifBlank { "[]" } ?: "[]")
            items.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    ActionItem(
                        id = o.optString("id"),
                        text = o.optString("text"),
                        status = o.optString("status", "active"),
                    ),
                )
            }
        }
    }

    fun add(id: String, text: String) {
        load()
        if (text.isBlank() || items.any { it.id == id }) return
        items.add(0, ActionItem(id, text, "active"))
        persist()
        Events.report(Events.ACTION_SAVED)
    }

    fun setStatus(id: String, status: String) {
        load()
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) {
            items[i] = items[i].copy(status = status)
            persist()
            if (status == "done") Events.report(Events.ACTION_COMPLETED)
        }
    }

    fun openCount(): Int {
        load()
        return items.count { it.status == "active" }
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("text", a.text).put("status", a.status))
        }
        runCatching { Session.prefPut(PREF_KEY, arr.toString()) }
    }
}

// ── Today: the coaching home ─────────────────────────────────────────────────

@Composable
fun TodayHome(onOpen: (String) -> Unit) {
    ActionsStore.load()
    val open = ActionsStore.openCount()
    Page(eyebrow = "CereBroZen", title = "Today") {
        FocusCard {
            Text("Coached in the moments that matter", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Two minutes before the conversation you keep postponing changes how it goes.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
            PrimaryButton("Start a session") { onOpen("coach") }
        }
        SectionCard(onClick = { onOpen("actions") }) {
            Text("Commitments", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                if (open == 0) "Nothing open — your next session ends with one concrete step."
                else if (open == 1) "1 open commitment waiting on you."
                else "$open open commitments waiting on you.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        SectionCard(onClick = { onOpen("journeys") }) {
            Text("Journeys", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Multi-week practice for the skills that used to take a decade: feedback, delegation, influence.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        SectionCard(onClick = { onOpen("toolkit") }) {
            Text("Reset toolkit", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Two minutes between meetings: breathe, ground, or play something calm.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        SectionCard(onClick = { onOpen("sleep") }) {
            Text("Rest & recovery", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Sleep scenes, soundscapes, and the mixer — sustainable performance is rested performance.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        SectionCard(onClick = { onOpen("humansupport") }) {
            Text("Need a human?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Talking to a person is always an option — support paths live here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
    }
}

// ── Actions: the commitments tab ─────────────────────────────────────────────

@Composable
fun ActionsScreen(onOpen: (String) -> Unit) {
    ActionsStore.load()
    val items = ActionsStore.items
    Page(eyebrow = "Follow-through", title = "Actions") {
        if (items.isEmpty()) {
            SectionCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmptyStateArt(kind = "journal")
                    Text(
                        "No commitments yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        "Every coaching session ends with one small, concrete step. It lands here — and your coach asks how it went.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                    PrimaryButton("Start a session") { onOpen("coach") }
                }
            }
        } else {
            items.forEach { action ->
                SectionCard(
                    onClick = {
                        ActionsStore.setStatus(
                            action.id,
                            if (action.status == "active") "done" else "active",
                        )
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            action.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.status == "done") TextMuted else TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (action.status == "done") "done" else "open",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (action.status == "done") Ok else Accent.talk,
                        )
                    }
                }
            }
            Text(
                "Tap a commitment to mark it done — your coach will ask about open ones at your next check-in.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}
