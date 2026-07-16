package com.cerebrozen.app.ui.screens

/* The journal you can finally READ.
 *
 * Api.createJournal has been firing from the breathing and reset tools all along, so people
 * have entries. Api.journal() — the read — had no caller, because the screen was deleted in
 * the B2C strip and nothing replaced it. You could write a journal on this phone and never
 * see it again. PRODUCT.md's matrix claimed journaling shipped on Android; it was corrected
 * on 2026-07-16 to say "writes only", and this is the other half.
 *
 * BiometricGate.kt was orphaned by the same deletion: Settings still toggles
 * `journal_locked`, and until now nothing read it — a lock switch that locked nothing.
 * Settings' own comment still says the toggle exists "so the Settings and in-Journal lock
 * toggles share one implementation". This is the in-Journal side coming back.
 *
 * Consent-gated like every wellness read: `journal_memory`. Declined means the screen says
 * so plainly rather than showing an empty list, which would read as "you have written
 * nothing" when the truth is "we are not keeping it".
 */

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray

/** One entry as the engine stores it (`stores/wellness.py`: `{id, ts, title, body, ...}`). */
internal data class JournalEntry(val id: String, val title: String, val body: String, val ts: String)

/**
 * Parse the engine's journal, newest first.
 *
 * The engine stores oldest-first and hands the list back unchanged, so the reversal is
 * this screen's job — a history that opens on something you wrote weeks ago is not a
 * history anybody reads.
 */
internal fun parseJournal(rows: JSONArray): List<JournalEntry> {
    val out = mutableListOf<JournalEntry>()
    for (i in 0 until rows.length()) {
        val o = rows.optJSONObject(i) ?: continue
        val body = o.optString("body").trim()
        if (body.isEmpty()) continue
        out.add(
            JournalEntry(
                id = o.optString("id").ifBlank { o.optString("entry_id") },
                title = o.optString("title").trim(),
                body = body,
                ts = o.optString("ts"),
            ),
        )
    }
    return out.reversed()
}

/** "14 Jul 2026" in the reader's own zone — a late-night entry belongs to the day they
 *  had, not the day UTC was having. Unparseable stamps render blank rather than crash. */
internal fun entryDate(ts: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    runCatching {
        OffsetDateTime.parse(ts).atZoneSameInstant(zone)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }.getOrDefault("")

@Composable
fun JournalScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    // The lock Settings has been offering all along, finally attached to something.
    val locked = remember { Session.prefGet("journal_locked") == "true" }
    var unlocked by remember { mutableStateOf(!locked) }
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var allowed by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(unlocked) {
        if (!unlocked) return@LaunchedEffect
        // Absence is not refusal — the engine's own rule for a token minted before the
        // claim existed.
        allowed = runCatching { Api.consent().optBoolean("journal_memory", true) }.getOrDefault(true)
        if (allowed) {
            runCatching { Api.journal() }
                .onSuccess { entries = parseJournal(it); failed = false }
                .onFailure { failed = true }
        }
        loading = false
    }

    PremiumSubPage(
        stringResource(R.string.journal_eyebrow),
        stringResource(R.string.journal_title),
        onBack,
    ) {
        if (!unlocked) {
            SectionCard {
                Text(stringResource(R.string.journal_locked_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.journal_locked_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.journal_unlock)) {
                    requestScreenLock(ctx as? FragmentActivity) { ok -> if (ok) unlocked = true }
                }
            }
            return@PremiumSubPage
        }

        when {
            loading -> Text(stringResource(R.string.patterns_loading),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)

            // Not an empty list: "you have written nothing" and "we are not keeping it"
            // are different sentences, and only one of them is true here.
            !allowed -> SectionCard {
                Text(stringResource(R.string.journal_memory_off_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.journal_memory_off_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            failed -> SectionCard {
                Text(stringResource(R.string.patterns_error_fallback),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            entries.isEmpty() -> SectionCard {
                Text(stringResource(R.string.journal_no_entries_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.journal_no_entries_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            else -> entries.forEach { e ->
                SectionCard {
                    if (e.title.isNotBlank()) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(entryDate(e.ts), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(e.body, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                }
            }
        }
    }
}
