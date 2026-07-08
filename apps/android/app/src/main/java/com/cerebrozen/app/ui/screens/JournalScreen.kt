package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray

private val PROMPTS = listOf(
    "Where did you feel most like yourself today?",
    "Name the worry — then one truer thought beside it.",
    "What's one thing you could set down tonight?",
    "What went better than you expected?",
    "If today had a weather, what was it — and why?",
)

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

/** Authenticate with any screen lock (biometric or PIN/pattern). No lock set
 * up → unlock gracefully, mirroring iOS (emulators/tests never get stuck). */
private fun requestJournalUnlock(activity: FragmentActivity?, onResult: (Boolean) -> Unit) {
    if (activity == null) { onResult(true); return }
    val auths = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(auths) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(true); return
    }
    BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock journal")
            .setSubtitle("Private entries stay behind your screen lock")
            .setAllowedAuthenticators(auths)
            .build(),
    )
}

/** Case-insensitive title/body filter (mirrors iOS journal search). */
internal fun filterEntries(entries: List<Entry>, query: String): List<Entry> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true) }
}

/** Journal: private composer + history, mirrored to /journal (safety-scanned
 * server-side; support surfaces, never blocks). */
@Composable
fun JournalScreen() {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var showSupport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var promptIdx by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // Journal lock (mirrors iOS Face ID lock): opt-in via Privacy & memory;
    // unlocks per visit. Devices without any screen lock unlock gracefully so
    // emulators/tests are never blocked (same contract as iOS).
    val lockOn = Session.prefGet("journal_locked") == "true"
    var unlocked by remember { mutableStateOf(!lockOn) }
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(Unit) { runCatching { entries = parseEntries(Api.journal()) } }

    if (!unlocked) {
        Page("Private to you", "Journal", trailing = Icons.Outlined.MenuBook) {
            SectionCard {
                Text("Journal is locked", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("Your entries stay behind your screen lock.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                PrimaryButton(text = "Unlock", modifier = Modifier.fillMaxWidth()) {
                    requestJournalUnlock(activity) { ok -> if (ok) unlocked = true }
                }
            }
        }
        return
    }

    Page("Private to you", "Journal", trailing = Icons.Outlined.MenuBook) {
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

        SectionCard {
            Text("Release the day", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            AppTextField(title, { title = it }, "Title", singleLine = true)
            AppTextField(body, { body = it }, "What's on your mind?", minLines = 3)
            PrimaryButton(
                text = if (busy) "One moment…" else "Save entry",
                enabled = !busy && title.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                busy = true; status = null
                scope.launch {
                    try {
                        val saved = Api.createJournal(title.trim(), body.trim())
                        showSupport = saved.optString("risk_level", "none") !in listOf("none", "low")
                        title = ""; body = ""
                        status = "Saved — private to you."
                        runCatching { entries = parseEntries(Api.journal()) }
                    } catch (e: Exception) {
                        status = e.message ?: "Couldn't save."
                    } finally {
                        busy = false
                    }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
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

        if (entries.isNotEmpty()) {
            SectionCard {
                Text("History", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                if (entries.size > 3) {
                    AppTextField(query, { query = it }, "Search entries", singleLine = true)
                }
                val shown = filterEntries(entries, query)
                shown.take(10).forEachIndexed { i, e ->
                    Column(
                        Modifier.appear(i, rise = 8f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("${e.title} · ${e.date}", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                        Text(e.body.take(120), style = MaterialTheme.typography.bodySmall, color = TextMuted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (shown.isEmpty()) {
                    Text("No entries match \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }
    }
}
