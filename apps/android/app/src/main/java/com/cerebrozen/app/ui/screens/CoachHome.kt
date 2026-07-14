package com.cerebrozen.app.ui.screens

/* The CereBroZen coaching surfaces: the Today home and the Actions tab.
 *
 * Actions are the product's spine (docs/COACHING_FLOW.md): every session ends
 * in a saved commitment, and this tab is where those commitments live between
 * sessions. ActionsStore keeps them on-device (Session prefs, JSON) — the
 * coach turn wiring appends cards here; the engine remains the source of
 * truth and re-syncs on session close. */

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Events
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.ChipFill
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

/** The living presence orb (Mira reference, coral-skinned): a slow breathing
 * core with a soft aura. Reduce Motion holds it steady at full size. */
@Composable
private fun PresenceOrb(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val scale = if (reduceMotion) 1f else {
        val breathe = rememberInfiniteTransition(label = "presence-orb")
        val s by breathe.animateFloat(
            initialValue = 0.94f, targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "orb-scale",
        )
        s
    }
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(96.dp).scale(scale * 1.18f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(BrandPrimary.copy(alpha = 0.35f), BrandPrimary.copy(alpha = 0f)))),
        )
        Box(
            Modifier.size(72.dp).scale(scale).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFFFCD9D4), BrandPrimary, androidx.compose.ui.graphics.Color(0xFFB03A3A)),
                    ),
                ),
        )
    }
}

/** Time- and state-aware presence copy: one greeting, one line, two actions. */
private fun presenceLines(name: String?, openActions: Int, hour: Int): Pair<String, String> {
    val who = name?.takeIf { it.isNotBlank() }?.split(" ")?.first()
    val greeting = when {
        hour < 5 -> if (who != null) "Still up, $who?" else "Still up?"
        hour < 12 -> if (who != null) "Good morning, $who" else "Good morning"
        hour < 17 -> if (who != null) "Good afternoon, $who" else "Good afternoon"
        else -> if (who != null) "Good evening, $who" else "Good evening"
    }
    val say = when {
        openActions == 1 -> "One commitment is still open — want to check it off, or talk through what's in the way?"
        openActions > 1 -> "$openActions commitments are open. Want to talk one through, or take a minute to reset first?"
        hour < 5 -> "Whatever has you up — we can think it through, or just breathe for a minute."
        hour >= 17 -> "Anything from today worth a few minutes before tomorrow?"
        else -> "What's the moment in front of you? Two minutes of prep changes how it goes."
    }
    return greeting to say
}

@Composable
fun TodayHome(onOpen: (String) -> Unit) {
    ActionsStore.load()
    val open = ActionsStore.openCount()
    var userName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { userName = Api.me().optString("name") }
    }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, say) = presenceLines(userName, open, hour)
    Page(eyebrow = "CereBroZen", title = greeting) {
        FocusCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresenceOrb()
                Text(
                    say,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { PrimaryButton("Talk it through") { onOpen("coach") } }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                            .background(ChipFill)
                            .clickable { onOpen("breathe/reset") }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Breathe", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                }
            }
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
        if (hour >= 20 || hour < 3) {
            SectionCard(onClick = { onOpen("winddown") }) {
                Text("Wind down for tonight", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Four small steps to close the day — a few unhurried minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
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
