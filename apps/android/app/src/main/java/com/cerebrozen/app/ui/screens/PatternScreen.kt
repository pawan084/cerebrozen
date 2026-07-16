package com.cerebrozen.app.ui.screens

/* "What your coach has learned" — the transparency screen.
 *
 * Two promises, and the second is what makes the first worth anything:
 *   1. every statement shows the BASIS that produced it (the counts, not a vibe);
 *   2. you can delete the lot, without losing your own journal.
 *
 * The engine derives these from the person's own records only, and refuses to speak below
 * its sample sizes (services/engine/app/stores/patterns.py). "Nothing yet" is a real,
 * common answer, so the empty state is written as reassurance rather than an error.
 *
 * Why this sits next to the employer-visibility story: an employee is being asked to talk
 * candidly inside software their employer pays for. "Here is everything it thinks about
 * you, and here is the button that erases it" is the most load-bearing screen in the app
 * for that, and it was the one screen the B2C strip left orphaned — Api.patterns() and
 * Api.deleteMemory() have existed all along with nothing calling them.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One learned statement and the counts behind it. */
internal data class Pattern(val statement: String, val basis: String)

/** Parse the engine's dashboard. Pure — no Android, no network: the shape is the contract
 *  and it is worth a test of its own. Malformed rows are dropped rather than rendered as a
 *  claim with no basis, which is the one thing this screen must never show. */
internal fun parsePatterns(body: JSONObject): List<Pattern> {
    val rows = body.optJSONArray("patterns") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val o = rows.optJSONObject(i) ?: return@mapNotNull null
        val statement = o.optString("statement").trim()
        val basis = o.optString("basis").trim()
        if (statement.isEmpty() || basis.isEmpty()) null else Pattern(statement, basis)
    }
}

/** The engine reports per-location counts; the surviving copy speaks in "messages" and
 *  "insights". Map rather than reword: the string is already translated into Hindi, and
 *  "transcripts"/"agentic_context" are our storage vocabulary, not a person's. */
internal fun deletedCounts(report: JSONObject): Pair<Int, Int> {
    val d = report.optJSONObject("deleted") ?: return 0 to 0
    val messages = d.optInt("transcripts") + d.optInt("checkpoints") + d.optInt("checkpoint_writes")
    val insights = d.optInt("agentic_context") + d.optInt("dynamic_vars")
    return messages to insights
}

/** Which categories the engine was allowed to read, as human words. */
internal fun sourceLabels(body: JSONObject): List<String> {
    val src = body.optJSONObject("sources") ?: return emptyList()
    return buildList {
        if (src.optBoolean("mood_history")) add("check-ins")
        if (src.optBoolean("journal_memory")) add("journal")
        if (src.optBoolean("sleep_history")) add("sleep")
    }
}

@Composable
fun PatternScreen(onBack: () -> Unit) {
    var patterns by remember { mutableStateOf<List<Pattern>>(emptyList()) }
    var sources by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    /* Tap-twice to confirm, which is what the surviving copy already says
       ("Tap again to confirm"). Lighter than the typed gate the web app uses for account
       deletion, and rightly so: this is recoverable in the sense that matters — nothing
       the person themselves wrote is lost. */
    var armed by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val failedCopy = stringResource(R.string.patterns_delete_failed)
    // The success line is formatted with counts inside a coroutine, where stringResource
    // cannot be called — so resolve through the Context instead.
    val ctx = LocalContext.current

    suspend fun load() {
        loading = true
        runCatching { Api.patterns() }
            .onSuccess { body ->
                patterns = parsePatterns(body)
                sources = sourceLabels(body)
                failed = false
            }
            .onFailure { failed = true }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    PremiumSubPage(
        stringResource(R.string.patterns_eyebrow),
        stringResource(R.string.patterns_title),
        onBack,
    ) {
        Text(
            stringResource(R.string.patterns_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft,
        )

        Text(
            stringResource(R.string.patterns_remembers_title),
            style = MaterialTheme.typography.titleMedium, color = TextSoft,
        )

        when {
            loading -> Text(stringResource(R.string.patterns_loading),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)

            failed -> SectionCard {
                Text(stringResource(R.string.patterns_error_fallback),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            // Not an error. The engine stays quiet below its sample sizes on purpose, so
            // "nothing yet" is the honest and common answer for a new member — and the
            // copy already says why ("no guesses, ever").
            patterns.isEmpty() -> SectionCard {
                Text(stringResource(R.string.patterns_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            else -> patterns.forEach { p ->
                SectionCard {
                    Text(p.statement, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Spacer(Modifier.height(6.dp))
                    // The basis is the whole point: without it this is a horoscope.
                    Text(p.basis, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                }
            }
        }

        if (sources.isNotEmpty() && !loading && !failed) {
            Text(
                stringResource(R.string.patterns_sources, sources.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
            )
        }

        Spacer(Modifier.height(4.dp))

        SectionCard {
            Text(stringResource(R.string.patterns_delete_title),
                style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Spacer(Modifier.height(6.dp))
            // Says exactly what survives. "Delete everything" is a different button, in
            // Privacy, and confusing the two is how somebody loses their diary.
            Text(stringResource(R.string.patterns_delete_body),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(12.dp))
            DangerButton(
                text = when {
                    wiping -> stringResource(R.string.patterns_loading)
                    armed -> stringResource(R.string.patterns_confirm)
                    else -> stringResource(R.string.patterns_delete_title)
                },
                enabled = !wiping,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!armed) {
                    armed = true
                    status = null
                    return@DangerButton
                }
                wiping = true
                scope.launch {
                    runCatching { Api.deleteMemory() }
                        .onSuccess { report ->
                            val (messages, insights) = deletedCounts(report)
                            status = ctx.getString(R.string.patterns_cleared, messages, insights)
                            armed = false
                            load()
                        }
                        // The engine answers 500 when its re-scan finds anything left.
                        // Never round that up: telling someone the coach forgot when it
                        // did not is the failure the whole verify step exists to prevent.
                        .onFailure { status = failedCopy }
                    wiping = false
                }
            }
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}
