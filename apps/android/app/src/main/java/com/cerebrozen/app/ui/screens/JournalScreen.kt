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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate

/** The rotating writing prompts, resolved from resources in composition. */
@Composable
private fun journalPrompts(): List<String> = listOf(
    stringResource(R.string.journal_prompt_1),
    stringResource(R.string.journal_prompt_2),
    stringResource(R.string.journal_prompt_3),
    stringResource(R.string.journal_prompt_4),
    stringResource(R.string.journal_prompt_5),
)

/**
 * The three string resources a state-tuned hero needs: an eyebrow that names the
 * day, a title, and the prompt itself.
 */
internal data class TunedPrompt(
    @androidx.annotation.StringRes val tag: Int,
    @androidx.annotation.StringRes val title: Int,
    @androidx.annotation.StringRes val prompt: Int,
)

/**
 * Today's check-in reshapes the Journal hero (iOS parity —
 * `JournalPrompts.tuned(toMood:)` in JournalViews.swift). Returns null for any
 * other feeling, and the rotating prompts stand.
 *
 * **Case-insensitive on purpose.** The clients disagree about casing: Android and
 * iOS post "Anxious", the browser client posts "anxious", and both land in the
 * same `mood_logs.mood` column — seen in the dev database. iOS's `switch` on the
 * exact string silently misses the lowercase rows, so a web check-in tunes
 * nothing. Matching on the lowercased value means whichever client logged the
 * feeling, the journal responds to it.
 */
internal fun tunedPromptFor(mood: String?): TunedPrompt? = when (mood?.trim()?.lowercase()) {
    "anxious" -> TunedPrompt(
        R.string.journal_tuned_anxious_tag,
        R.string.journal_tuned_anxious_title,
        R.string.journal_tuned_anxious_prompt,
    )
    "low" -> TunedPrompt(
        R.string.journal_tuned_low_tag,
        R.string.journal_tuned_low_title,
        R.string.journal_tuned_low_prompt,
    )
    "tired" -> TunedPrompt(
        R.string.journal_tuned_tired_tag,
        R.string.journal_tuned_tired_title,
        R.string.journal_tuned_tired_prompt,
    )
    else -> null
}

/**
 * True when [iso] falls on today's date **in the reader's own timezone**.
 *
 * `created_at` arrives in UTC, so comparing its date directly would be wrong for
 * anyone east or west of it: a 22:00 IST check-in is stamped 16:30 UTC the same
 * day, but an 02:00 IST one is stamped the *previous* UTC day and would stop
 * tuning the hero exactly when someone journals late at night. Unparseable or
 * missing input is simply not today.
 */
internal fun isToday(iso: String?, today: LocalDate = LocalDate.now()): Boolean =
    runCatching {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDate() == today
    }.getOrDefault(false)

/** Optional feeling chips for a new entry. Single-select; when chosen the mood is
 * persisted with the entry (appended to the saved body via [Api.createJournal]),
 * so nothing here is decorative — it becomes part of the real, searchable record. */
@Composable
private fun journalMoods(): List<String> = listOf(
    stringResource(R.string.journal_mood_calm), stringResource(R.string.journal_mood_anxious),
    stringResource(R.string.journal_mood_hopeful), stringResource(R.string.journal_mood_tired),
    stringResource(R.string.journal_mood_sad), stringResource(R.string.journal_mood_grateful),
)

/** Quick-entry prompts in the composer — the descendants of the retired
 * one-good-thing / intention tool screens (REDESIGN §2.2). */
@Composable
private fun quickPrompts(): List<String> = listOf(
    stringResource(R.string.journal_quick_good_thing),
    stringResource(R.string.journal_quick_intention),
)

/** The journal's information architecture (mirrors the redesign): a hub plus three
 * pushed sub-screens for writing, reviewing history, and privacy. */
private enum class JournalMode { Home, Entry, History, Private, Read }

internal data class Entry(
    val title: String,
    val body: String,
    val date: String,
    val risk: String,
    val tags: List<String> = emptyList(),
)

internal fun parseEntries(rows: JSONArray): List<Entry> =
    (0 until rows.length()).map { i ->
        val e = rows.getJSONObject(i)
        val tagArray = e.optJSONArray("tags")
        Entry(
            e.getString("title"),
            e.getString("body"),
            e.getString("created_at").take(10),
            e.optString("risk_level", "none"),
            (0 until (tagArray?.length() ?: 0)).mapNotNull { tagArray?.optString(it)?.takeIf(String::isNotBlank) },
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

/** How many entries fall in [today]'s calendar month — the honest number the
 * hub states (Entry.date is the "yyyy-MM-dd" prefix). Unparseable dates are
 * not counted. Pure. */
internal fun entriesThisMonth(entries: List<Entry>, today: LocalDate): Int =
    entries.count { e ->
        runCatching { LocalDate.parse(e.date) }.getOrNull()
            ?.let { it.year == today.year && it.month == today.month } == true
    }

/** Words in a draft — the quiet session-feel counter under the composer. Pure. */
internal fun wordCount(s: String): Int =
    s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

/** Journal: private composer + history, mirrored to /journal (safety-scanned
 * server-side; support surfaces, never blocks). Re-skinned to the redesign's
 * multi-mode hub (Home / Entry / History / Private) on our design system.
 * [startInEntry] lands straight in the composer — the Home check-in's
 * "Say more in Journal" bridge used to drop people at the hub. */
@Composable
fun JournalScreen(
    startInEntry: Boolean = false,
    onOpen: (String) -> Unit = {},
    // Set on the journal/new route: the composer's back arrow then leaves the
    // route (matching system back) instead of revealing the hub — the two back
    // affordances on one screen used to go two different places (audit A53).
    onExit: (() -> Unit)? = null,
) {
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
    // Server-side search. The device holds only the most recent page, so
    // "I wrote about this months ago" is exactly the search that would miss
    // if it ran on-device alone. Debounced, and purely additive: the local
    // filter still answers instantly and still works with no signal.
    var remoteHits by remember { mutableStateOf(listOf<Entry>()) }
    var searching by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tag by remember { mutableStateOf<String?>(null) }
    var mood by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(if (startInEntry) JournalMode.Entry else JournalMode.Home) }
    // The entry being read in full. Android could WRITE journal entries and never
    // read one back: history rows were not tappable, so the only view of your own
    // writing was a 120-character, two-line preview. iOS has had JournalDetailView
    // since the start; this is the missing half of the feature, not a new one.
    var reading by remember { mutableStateOf<Entry?>(null) }
    // The draft-safe banner — captured at FIRST composition, so it's true only when
    // the fields arrived restored (rotation / process death), never after typing.
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
    val prompts = journalPrompts()

    LaunchedEffect(Unit) {
        runCatching { entries = parseEntries(Api.journal()) }
        runCatching {
            val used = Api.journalTags()
            tags = (0 until used.length()).mapNotNull { used.optString(it).takeIf(String::isNotBlank) }
        }
    }

    // Debounced server search. 350ms: long enough that typing a word is one
    // request rather than five, short enough that it feels like the list is
    // keeping up. Blank queries clear rather than fetch everything again.
    LaunchedEffect(query, tag) {
        val q = query.trim()
        if (q.length < 2 && tag == null) {
            remoteHits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        runCatching { parseEntries(Api.searchJournal(q, tag.orEmpty())) }
            .onSuccess { remoteHits = it }
            // Offline is not an error here — the local filter already answered.
            .onFailure { remoteHits = emptyList() }
        searching = false
    }

    // Today's check-in reshapes the hero (iOS parity). Best-effort: if the call
    // fails the rotation stands, so an offline journal never loses its prompt.
    var todayMood by remember { mutableStateOf<String?>(null) }
    // "Try another" opts out of the tuned prompt into the rotation — the tuned
    // hero is a starting point, not a verdict about the day.
    var tunedDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            val latest = Api.moods().optJSONObject(0)
            todayMood = latest?.takeIf { isToday(it.optString("created_at")) }?.optString("mood")
        }
    }
    val tuned = if (tunedDismissed) null else tunedPromptFor(todayMood)

    if (!unlocked) {
        PremiumPage(stringResource(R.string.journal_eyebrow), stringResource(R.string.journal_title), trailing = Icons.AutoMirrored.Outlined.MenuBook) {
            SectionCard {
                Text(stringResource(R.string.journal_locked_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.journal_locked_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                PrimaryButton(text = stringResource(R.string.journal_unlock), modifier = Modifier.fillMaxWidth()) {
                    requestScreenLock(activity) { ok -> if (ok) unlocked = true }
                }
            }
        }
        return
    }

    when (mode) {
        JournalMode.Entry -> {
            PremiumSubPage(
                stringResource(R.string.journal_entry_eyebrow),
                stringResource(R.string.journal_entry_title),
                onBack = { if (startInEntry && onExit != null) onExit() else mode = JournalMode.Home },
            ) {
                // Prompt rotation moved into the composer as a gentle guide.
                SectionCard {
                    Text(stringResource(R.string.journal_prompt_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                    Text(prompts[promptIdx], style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    TextButton(
                        onClick = { promptIdx = (promptIdx + 1) % prompts.size },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text(stringResource(R.string.journal_try_another), color = Cyan) }
                }
                // W10: a small honest reassurance when the composer opens with a
                // restored draft — dismissible, plain state, never persisted.
                if (restoredDraft && !draftBannerDismissed && (title.isNotBlank() || body.isNotBlank())) {
                    InfoBanner(
                        icon = Icons.Outlined.Save,
                        text = stringResource(R.string.journal_draft_safe),
                        onDismiss = { draftBannerDismissed = true },
                    )
                }
            SectionCard {
                Text(stringResource(R.string.journal_release_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                // Quick entries (REDESIGN §2.2): the old one-field tools live on
                // as prompt chips — tapping prefills the title, and only ever
                // replaces a blank title or the other prompt, never your words.
                val quick = quickPrompts()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { prompt ->
                        PickChip(selected = title == prompt, label = prompt) {
                            if (title.isBlank() || title in quick) title = prompt
                        }
                    }
                }
                AppTextField(title, { title = it }, stringResource(R.string.journal_title_label), singleLine = true)
                AppTextField(body, { body = it }, stringResource(R.string.journal_body_label), minLines = 3)
                // A quiet session-feel counter — never a target, just the fact.
                if (wordCount(body) > 0) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.journal_word_count, wordCount(body), wordCount(body),
                        ),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    )
                }
                Text(stringResource(R.string.journal_feeling_label),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                // FlowRow, not chunked(3) + Row: a fixed three-per-row grid has no
                // way to give, so a longer translation or a large system font size
                // pushes the third chip off the edge. (Not reproducible on this
                // device — the OEM blocks both `settings put system font_scale`
                // and `wm density` — so this is hardened by construction rather
                // than from a screenshot.)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                run {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        journalMoods().forEach { m ->
                            PickChip(selected = mood == m, label = m) {
                                mood = if (mood == m) null else m
                            }
                        }
                    }
                }
                val feelingTemplate = stringResource(R.string.journal_entry_feeling_format)
                val savedStatus = stringResource(R.string.journal_saved)
                val saveFailed = stringResource(R.string.common_save_failed)
                PrimaryButton(
                    text = if (busy) stringResource(R.string.common_one_moment) else stringResource(R.string.journal_save_cta),
                    enabled = !busy && title.isNotBlank() && body.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    busy = true; status = null
                    scope.launch {
                        try {
                            // Persist the chosen feeling with the entry so the chip is real.
                            val entryBody = body.trim().let { b ->
                                mood?.let { feelingTemplate.format(b, it.lowercase()) } ?: b
                            }
                            // Null = queued offline. The safety scan runs
                            // server-side, so there is no verdict yet; showing
                            // the support card anyway would be inventing one,
                            // and hiding it forever would be worse — the scan
                            // happens when the entry syncs.
                            val saved = Api.createJournal(title.trim(), entryBody)
                            showSupport = saved?.optString("risk_level", "none")
                                ?.let { it !in listOf("none", "low") } == true
                            title = ""; body = ""; mood = null
                            draftBannerDismissed = true   // the draft became an entry
                            status = savedStatus
                            // The shared app-root flourish (Celebration is
                            // reuse-only — Breathing/CBT fire the same one).
                            // It owns the success haptic and its own Reduce
                            // Motion branch, so nothing is re-implemented here.
                            Celebrations.trigger()
                            runCatching { entries = parseEntries(Api.journal()) }
                            if (!reduceMotion) delay(650)
                            mode = JournalMode.Home
                        } catch (e: Exception) {
                            status = e.message ?: saveFailed
                        } finally {
                            busy = false
                        }
                    }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            }
            }
            return
        }
        JournalMode.Read -> {
            val e = reading
            if (e == null) {
                mode = JournalMode.History
            } else {
                SubPage(e.date, e.title.ifBlank { stringResource(R.string.journal_untitled) },
                    onBack = { mode = JournalMode.History }) {
                    if (e.body.isBlank()) {
                        SectionCard {
                            Text(stringResource(R.string.journal_read_empty_title),
                                style = MaterialTheme.typography.titleMedium, color = TextSoft)
                            Text(stringResource(R.string.journal_read_empty_body),
                                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    } else {
                        SectionCard {
                            // The whole entry, never truncated — this screen exists
                            // precisely because the preview was all there was.
                            Text(e.body, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                        }
                    }
                    // H20: after reading there was nothing to do but back out.
                    // Writing again is the natural next step on this tab.
                    PrimaryButton(
                        text = stringResource(R.string.journal_new_title),
                        modifier = Modifier.fillMaxWidth(),
                    ) { reading = null; mode = JournalMode.Entry }
                }
            }
            return
        }
        JournalMode.History -> {
            PremiumSubPage(stringResource(R.string.journal_history_eyebrow), stringResource(R.string.journal_history_title), onBack = { mode = JournalMode.Home }) {
                if (entries.isEmpty()) {
                    SectionCard {
                        // W24: a small one-shot art illustration above the copy.
                        EmptyStateArt(kind = "journal")
                        Text(stringResource(R.string.journal_no_entries_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text(stringResource(R.string.journal_no_entries_body),
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                } else {
                    if (entries.size > 3) {
                        AppTextField(query, { query = it }, stringResource(R.string.journal_search_label), singleLine = true)
                    }
                    // Tag chips, only the ones this user has actually used.
                    // Offered rather than typed, so "work", "Work" and "work "
                    // don't quietly become three different tags.
                    if (tags.isNotEmpty()) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.take(8).forEach { t ->
                                PickChip(selected = tag == t, label = t) { tag = if (tag == t) null else t }
                            }
                        }
                    }
                    // Local filter first — it is instant and works offline. The
                    // server search below only adds what the device cannot know
                    // about: entries older than the page we hold.
                    val local = filterEntries(entries, query)
                        .filter { tag == null || it.tags.contains(tag) }
                    val shown = if (local.isEmpty() && remoteHits.isNotEmpty()) remoteHits else local
                    shown.take(20).forEachIndexed { i, e ->
                        JournalEntryCard(e, i) { reading = e; mode = JournalMode.Read }
                    }
                    if (shown.isEmpty()) {
                        Text(
                            if (searching) stringResource(R.string.journal_searching)
                            else stringResource(R.string.journal_no_match, query.trim()),
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        )
                    } else if (local.isEmpty()) {
                        // Say where these came from: they are not in the list the
                        // user was just scrolling.
                        Text(
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.journal_found_older, shown.size, shown.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        )
                    }
                }
            }
            return
        }
        JournalMode.Private -> {
            PremiumSubPage(stringResource(R.string.journal_private_eyebrow), stringResource(R.string.journal_private_title), onBack = { mode = JournalMode.Home }) {
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
                                Text(stringResource(R.string.journal_lock_title),
                                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                                Text(stringResource(R.string.journal_lock_hint),
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
                    Text(stringResource(R.string.journal_private_body_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        stringResource(R.string.journal_private_body),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Text(
                        stringResource(R.string.journal_telemanas),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
            return
        }
        JournalMode.Home -> Unit
    }

    PremiumPage(stringResource(R.string.journal_eyebrow), stringResource(R.string.journal_title), trailing = Icons.AutoMirrored.Outlined.MenuBook) {
        HeroCard(
            kind = "journal",   // no dedicated motif — the brand orb family
            eyebrow = stringResource(tuned?.tag ?: R.string.journal_prompt_header),
            title = if (tuned != null) stringResource(tuned.title) else prompts[promptIdx],
            subtitle = if (tuned != null) stringResource(tuned.prompt)
                       else stringResource(R.string.journal_hero_subtitle),
            height = 220.dp,
            // The prompt is an invitation — tapping it accepts: the composer
            // opens with the prompt as the title (never overwriting a draft).
            onClick = {
                if (title.isBlank()) {
                    title = if (tuned != null) "" else prompts[promptIdx]
                }
                mode = JournalMode.Entry
            },
        ) {
            // The prompt card is the page's hero and it is WRITABLE — the pill
            // says so (tapping anywhere on the card already opened the composer,
            // but nothing admitted it).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        // From a tuned hero the first tap moves to the rotation; after
                        // that it advances through it as it always did.
                        if (tuned != null) tunedDismissed = true
                        else promptIdx = (promptIdx + 1) % prompts.size
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(stringResource(R.string.journal_try_another), color = Cyan) }
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .border(1.dp, com.cerebrozen.app.ui.theme.ArtTextSoft.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        stringResource(R.string.journal_hero_write).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = com.cerebrozen.app.ui.theme.ArtTextSoft,
                    )
                }
            }
        }

        // Writing is what this tab is FOR — it gets the one primary button.
        // History and Private stay quiet rows.
        PrimaryButton(
            text = stringResource(R.string.journal_new_title),
            modifier = Modifier.fillMaxWidth(),
        ) { mode = JournalMode.Entry }

        val monthCount = entriesThisMonth(entries, LocalDate.now())
        NavRow(
            stringResource(R.string.journal_history_title),
            if (monthCount > 0)
                androidx.compose.ui.res.pluralStringResource(R.plurals.journal_month_count, monthCount, monthCount)
            else stringResource(R.string.journal_history_subtitle),
            Icons.Outlined.History,
        ) {
            mode = JournalMode.History
        }
        NavRow(
            stringResource(R.string.journal_private_mode),
            // The row carries its state — a lock you can't see is a lock you
            // don't trust.
            if (journalLocked) stringResource(R.string.journal_lock_state_on)
            else stringResource(R.string.journal_lock_state_off),
            Icons.Outlined.Lock,
        ) {
            mode = JournalMode.Private
        }

        // Safety contract: support is surfaced, the entry is never blocked.
        // It renders ABOVE the recent entries — after a heavy entry this card
        // is the point of the page, not a footnote below the fold — and it
        // acts: a one-tap dial of the region's crisis line plus the full
        // support directory, never risk stated without a tappable pathway.
        if (showSupport) {
            val supportRegion by rememberCrisisRegion()
            val supportLine = primaryCrisisLine(supportRegion)
            val supportIsUrl = isSupportUrl(supportLine.target)
            SectionCard {
                Text(stringResource(R.string.journal_support_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    stringResource(R.string.journal_support_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ctx = LocalContext.current
                    val callCd =
                        if (supportIsUrl) stringResource(R.string.crisis_open_cd, stringResource(supportLine.nameRes))
                        else stringResource(R.string.you_support_call_cd,
                            stringResource(supportLine.nameRes), supportLine.target)
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, Warm.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { openSupportTarget(ctx, supportLine.target) }
                            .semantics { contentDescription = callCd }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (supportIsUrl) stringResource(supportLine.nameRes)
                            else stringResource(R.string.talk_crisis_call, supportLine.target),
                            style = MaterialTheme.typography.labelLarge, color = Warm)
                    }
                    TextButton(onClick = { onOpen("crisis") }) {
                        Text(stringResource(R.string.journal_support_more),
                            style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                    }
                }
            }
        }

        // Your writing, on the tab named for it: the two most recent entries
        // inline (History keeps the full list). A journal hub that never shows
        // a word you wrote feels empty even when it isn't.
        if (entries.isNotEmpty()) {
            Text(stringResource(R.string.journal_recent_header),
                style = MaterialTheme.typography.titleMedium, color = TextSoft)
            entries.take(2).forEachIndexed { i, e ->
                JournalEntryCard(e, i) { reading = e; mode = JournalMode.Read }
            }
        } else {
            // The section exists before the first entry too — an anchored
            // promise instead of silent nothing.
            Text(
                stringResource(R.string.journal_recent_empty),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

/** A single history entry as a glass row: an accent bar (warm when the entry was
 * flagged for support), date, title, and a two-line preview. Real data only. */
@Composable
private fun JournalEntryCard(entry: Entry, index: Int, onOpen: () -> Unit) {
    val elevated = entry.risk !in listOf("none", "low")
    val openCd = stringResource(R.string.journal_open_entry_cd, entry.title.ifBlank { entry.date })
    Row(
        Modifier
            .fillMaxWidth()
            .appear(index, rise = 8f)
            .glass()
            .clickable(onClick = onOpen)
            .semantics { contentDescription = openCd }
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
                Text(stringResource(R.string.journal_support_badge), style = MaterialTheme.typography.labelMedium, color = Warm)
            }
        }
    }
}
