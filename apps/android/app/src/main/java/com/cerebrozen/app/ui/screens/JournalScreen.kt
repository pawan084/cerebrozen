package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

private val PROMPTS = listOf(
    "Where did you feel most like yourself today?",
    "Name the worry — then one truer thought beside it.",
    "What's one thing you could set down tonight?",
    "What went better than you expected?",
    "If today had a weather, what was it — and why?",
)

/** Optional feeling chips for a new entry. Single-select; when chosen the mood is
 * persisted with the entry (appended to the saved body via [Api.createJournal]),
 * so nothing here is decorative — it becomes part of the real, searchable record. */
private val MOODS = listOf("Calm", "Anxious", "Hopeful", "Tired", "Sad", "Grateful")

/** Quick-entry prompts in the composer — the descendants of the retired
 * one-good-thing / intention tool screens (REDESIGN §2.2). */
private val QUICK_PROMPTS = listOf("One good thing", "Tonight's intention")

/** The journal's information architecture (mirrors the redesign): a hub plus three
 * pushed sub-screens for writing, reviewing history, and privacy. */
private enum class JournalMode { Home, Entry, History, Private }

internal data class Entry(val title: String, val body: String, val date: String, val risk: String)

internal fun parseEntries(rows: JSONArray): List<Entry> =
    (0 until rows.length()).map { i ->
        val e = rows.getJSONObject(i)
        Entry(
            e.getString("title"),
            e.getString("body"),
            e.getString("created_at").take(10),
            e.optString("risk_level", "none"),
        )
    }

// The device-credential gate (requestScreenLock) is shared with Settings via
// BiometricGate.kt so unlocking here and toggling the lock in Settings can't drift.

/** Case-insensitive title/body filter (mirrors iOS journal search). */
internal fun filterEntries(entries: List<Entry>, query: String): List<Entry> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true) }
}

/** Journal: private composer + history, mirrored to /journal (safety-scanned
 * server-side; support surfaces, never blocks). Re-skinned to the redesign's
 * multi-mode hub (Home / Entry / History / Private) on our design system. */
@Composable
fun JournalScreen() {
    // Draft survives rotation / tab switch / process death — this is the user's
    // own writing, so losing it silently is the worst thing this screen can do.
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var showSupport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var promptIdx by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(JournalMode.Home) }
    // W10: one-shot bloom per saved entry (Reduce Motion never arms it), and the
    // draft-safe banner — captured at FIRST composition, so it's true only when
    // the fields arrived restored (rotation / process death), never after typing.
    var bloom by remember { mutableIntStateOf(0) }
    val restoredDraft = remember { title.isNotBlank() || body.isNotBlank() }
    var draftBannerDismissed by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    // Journal lock (mirrors iOS Face ID lock): opt-in via Private mode; unlocks
    // per visit. Devices without any screen lock unlock gracefully so
    // emulators/tests are never blocked (same contract as iOS).
    val lockOn = Session.prefGet("journal_locked") == "true"
    var unlocked by remember { mutableStateOf(!lockOn) }
    var journalLocked by remember { mutableStateOf(lockOn) }
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(Unit) { runCatching { entries = parseEntries(Api.journal()) } }

    if (!unlocked) {
        Page("Private to you", "Journal", trailing = Icons.AutoMirrored.Outlined.MenuBook) {
            SectionCard {
                Text("Journal is locked", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("Your entries stay behind your screen lock.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                PrimaryButton(text = "Unlock", modifier = Modifier.fillMaxWidth()) {
                    requestScreenLock(activity) { ok -> if (ok) unlocked = true }
                }
            }
        }
        return
    }

    when (mode) {
        JournalMode.Entry -> {
            SubPage("Private writing", "New entry", onBack = { mode = JournalMode.Home }) {
                // Prompt rotation moved into the composer as a gentle guide.
                SectionCard {
                    Text("Today's prompt", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                    Text(PROMPTS[promptIdx], style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    TextButton(
                        onClick = { promptIdx = (promptIdx + 1) % PROMPTS.size },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Try another", color = Cyan) }
                }
                // W10: a small honest reassurance when the composer opens with a
                // restored draft — dismissible, plain state, never persisted.
                if (restoredDraft && !draftBannerDismissed && (title.isNotBlank() || body.isNotBlank())) {
                    InfoBanner(
                        icon = Icons.Outlined.Save,
                        text = "Your draft is safe — it survived the restart.",
                        onDismiss = { draftBannerDismissed = true },
                    )
                }
                Box {
                SectionCard {
                    Text("Release the day", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    // Quick entries (REDESIGN §2.2): the old one-field tools live on
                    // as prompt chips — tapping prefills the title, and only ever
                    // replaces a blank title or the other prompt, never your words.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_PROMPTS.forEach { prompt ->
                            PickChip(selected = title == prompt, label = prompt) {
                                if (title.isBlank() || title in QUICK_PROMPTS) title = prompt
                            }
                        }
                    }
                    AppTextField(title, { title = it }, "Title", singleLine = true)
                    AppTextField(body, { body = it }, "What's on your mind?", minLines = 3)
                    Text("How are you feeling? (optional)",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    MOODS.chunked(3).forEach { rowMoods ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowMoods.forEach { m ->
                                PickChip(selected = mood == m, label = m) {
                                    mood = if (mood == m) null else m
                                }
                            }
                        }
                    }
                    PrimaryButton(
                        text = if (busy) "One moment…" else "Save entry",
                        enabled = !busy && title.isNotBlank() && body.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        busy = true; status = null
                        scope.launch {
                            try {
                                // Persist the chosen feeling with the entry so the chip is real.
                                val entryBody = body.trim().let { b ->
                                    mood?.let { "$b\n\n— feeling ${it.lowercase()}" } ?: b
                                }
                                val saved = Api.createJournal(title.trim(), entryBody)
                                showSupport = saved.optString("risk_level", "none") !in listOf("none", "low")
                                title = ""; body = ""; mood = null
                                draftBannerDismissed = true   // the draft became an entry
                                status = "Saved — private to you."
                                // W10: the success pulse + a small bloom over the
                                // composer (same calm reward as the Home check-in),
                                // then home. Reduce Motion skips straight there —
                                // the status line is the state change.
                                Haptics.success()
                                runCatching { entries = parseEntries(Api.journal()) }
                                if (!reduceMotion) { bloom++; delay(650) }
                                mode = JournalMode.Home
                            } catch (e: Exception) {
                                status = e.message ?: "Couldn't save."
                            } finally {
                                busy = false
                            }
                        }
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
                }
                // W10: the one-shot bloom rides over the composer card; Reduce
                // Motion never arms it (bloom stays 0).
                if (bloom > 0) BloomRing(bloom, Accent.journal, Modifier.matchParentSize())
                }
            }
            return
        }
        JournalMode.History -> {
            SubPage("Past entries", "History", onBack = { mode = JournalMode.Home }) {
                if (entries.isEmpty()) {
                    SectionCard {
                        Text("No entries yet", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text("Your saved reflections will appear here — private to you.",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                } else {
                    if (entries.size > 3) {
                        AppTextField(query, { query = it }, "Search entries", singleLine = true)
                    }
                    val shown = filterEntries(entries, query)
                    shown.take(20).forEachIndexed { i, e -> JournalEntryCard(e, i) }
                    if (shown.isEmpty()) {
                        Text("No entries match \"${query.trim()}\".",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                }
            }
            return
        }
        JournalMode.Private -> {
            SubPage("Private mode", "Your privacy", onBack = { mode = JournalMode.Home }) {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null,
                                tint = Periwinkle, modifier = Modifier.size(22.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Lock the journal",
                                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                                Text("Require your screen lock to open",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        // Real toggle: wired to the same journal_locked pref the unlock
                        // flow reads. Gated behind the device credential in BOTH
                        // directions — same as the Settings path — so an already-open
                        // session can't silently disable the lock without authenticating.
                        AppSwitch(checked = journalLocked, onCheckedChange = { v ->
                            requestScreenLock(activity) { ok ->
                                if (ok) {
                                    journalLocked = v
                                    Session.prefPut("journal_locked", v.toString())
                                }
                            }
                        })
                    }
                }
                SectionCard {
                    Text("Your entries stay private", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        "Journal entries are yours. Safety scanning quietly surfaces support " +
                            "when something sounds heavy — it never blocks or shares your writing.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Text(
                        "In India, Tele-MANAS is available any time — call or WhatsApp 14416.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
            return
        }
        JournalMode.Home -> Unit
    }

    Page("Private to you", "Journal", trailing = Icons.AutoMirrored.Outlined.MenuBook) {
        HeroCard(
            imageUrl = HeroImg.journal,
            eyebrow = "Today's prompt",
            title = PROMPTS[promptIdx],
            subtitle = "A gentle starting point — or write about anything.",
            height = 220.dp,
        ) {
            TextButton(
                onClick = { promptIdx = (promptIdx + 1) % PROMPTS.size },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("Try another", color = Cyan) }
        }

        NavRow("New entry", "Private writing, safety-aware", Icons.Outlined.Edit) {
            mode = JournalMode.Entry
        }
        NavRow("History", "Your past entries, searchable", Icons.Outlined.History) {
            mode = JournalMode.History
        }
        NavRow("Private mode", "Lock the journal, manage privacy", Icons.Outlined.Lock) {
            mode = JournalMode.Private
        }

        // Safety contract: support is surfaced, the entry is never blocked.
        if (showSupport) {
            SectionCard {
                Text("You don't have to carry this alone", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    "That entry sounded heavy. If things feel urgent, real people can help right now — in India, call or WhatsApp Tele-MANAS at 14416, or dial your local emergency number.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

/** A single history entry as a glass row: an accent bar (warm when the entry was
 * flagged for support), date, title, and a two-line preview. Real data only. */
@Composable
private fun JournalEntryCard(entry: Entry, index: Int) {
    val elevated = entry.risk !in listOf("none", "low")
    Row(
        Modifier
            .fillMaxWidth()
            .appear(index, rise = 8f)
            .glass()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (elevated) Warm else Periwinkle),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.date, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.body.isNotBlank()) {
                Text(entry.body.take(120), style = MaterialTheme.typography.bodySmall, color = TextMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (elevated) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Warm.copy(alpha = 0.18f))
                    .border(1.dp, LineStroke, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Support", style = MaterialTheme.typography.labelMedium, color = Warm)
            }
        }
    }
}
