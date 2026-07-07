package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.launch
import org.json.JSONObject

// Consent keys + localized labels/hints live in ConsentNotice.kt
// (CONSENT_KEY_ORDER — the DPDP notice contract shared with iOS/web).

/** A tappable settings row (leading icon + title + subtitle + chevron). */
@Composable
internal fun NavRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null,
                        tint = if (emphasis) Cyan else Periwinkle, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = if (emphasis) Cyan else TextSoft)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = TextMuted2)
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Cyan else TextSoft)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (selected) Text("✓", style = MaterialTheme.typography.titleMedium, color = Cyan)
        }
    }
}

private val COMPANIONS = listOf(
    "Calm Guide" to "Steady and soothing — grounds you first, never rushes",
    "Warm Friend" to "Encouraging and familiar — like a friend who gets it",
    "Straight Talker" to "Clear and direct — kind, but skips the padding",
    "Quiet Coach" to "Action-first — one small concrete step at a time",
)

@Composable
fun CompanionStyleScreen(onBack: () -> Unit) {
    var current by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { current = Api.me().optString("companion") } }
    SubPage("How CereBro talks with you", "Companion style", onBack) {
        Text("Same care, different voice. Change it any time — conversations adapt from the next message.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        COMPANIONS.forEach { (name, detail) ->
            SelectableRow(name, detail, selected = current == name) {
                current = name
                scope.launch { runCatching { Api.updateProfile(JSONObject().put("companion", name)) } }
            }
        }
    }
}

private val REGIONS = listOf(
    "" to "Automatic (device)", "IN" to "India", "US" to "United States",
    "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia",
    "NZ" to "New Zealand", "EU" to "Europe",
)

@Composable
fun CrisisRegionScreen(onBack: () -> Unit) {
    var region by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { region = Api.me().optString("region") } }
    SubPage("Urgent support uses this", "Crisis region", onBack) {
        Text("Sets which emergency lines the crisis screen shows.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        REGIONS.forEach { (code, label) ->
            SelectableRow(label, "", selected = region == code) {
                region = code
                scope.launch { runCatching { Api.updateProfile(JSONObject().put("region", code)) } }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val consent = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    // DPDP s.5(3): the consent notice is readable in English or an
    // Eighth-Schedule language, picked right on the notice (ConsentNotice.kt).
    var noticeLang by remember { mutableStateOf("en") }
    val notice = noticeFor(noticeLang)
    LaunchedEffect(Unit) {
        runCatching { val c = Api.consent(); CONSENT_KEY_ORDER.forEach { consent[it] = c.optBoolean(it) } }
        runCatching { noticeLang = defaultNoticeCode(Api.me().optString("language")) }
    }
    SubPage("Control what CereBro remembers", notice.title, onBack) {
        Text(notice.caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        ChipWrap(NOTICE_CODES.map { noticeFor(it).nativeName }, notice.nativeName) { picked ->
            noticeLang = NOTICE_CODES.first { noticeFor(it).nativeName == picked }
        }
        SectionCard {
            CONSENT_KEY_ORDER.forEach { key ->
                val cat = notice.categories.getValue(key)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(cat.label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                        Text(cat.hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(checked = consent[key] == true, onCheckedChange = { v ->
                        consent[key] = v
                        scope.launch { runCatching { Api.updateConsent(JSONObject().put(key, v)) } }
                    })
                }
            }
        }
    }
}

@Composable
fun PremiumScreen(onBack: () -> Unit) = SubPage("CereBro Premium", "Go deeper", onBack) {
    Text("Unlock the full library, longer programs, and unlimited voice — same private-by-design promise.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    PlanCard("Annual", "₹2,999 / year", "Save 37% · 7-day free trial", featured = true)
    PlanCard("Monthly", "₹399 / month", "Cancel anytime", featured = false)
    Button(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) { Text("Start free trial") }
    Text("Billing isn't wired on Android yet — Play Billing lands with Play Console setup. On iOS this is live via StoreKit.",
        style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
private fun PlanCard(name: String, price: String, note: String, featured: Boolean) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = if (featured) Cyan else TextSoft)
            Text(price, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        Text(note, style = MaterialTheme.typography.labelSmall, color = if (featured) Warm else TextMuted)
    }
}

@Composable
fun HumanSupportScreen(onBack: () -> Unit) = SubPage("Beyond the app", "Human support", onBack) {
    Text("CereBro is a companion, not a clinician. When you want a real person, these help you find one.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    InfoCard("Talk to a coach", "Non-clinical support for everyday stress and habits. Coach directory is coming to India first.")
    InfoCard("Find a therapist", "For clinical needs, a licensed professional is the right call. We'll surface vetted directories, never diagnoses.")
    InfoCard("In a crisis", "If things feel urgent, open Urgent support for region-aware helplines — available offline.")
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cerebro", Context.MODE_PRIVATE) }
    var on by remember { mutableStateOf(prefs.getBoolean("reminder_on", false)) }

    fun persist(value: Boolean) { on = value; prefs.edit().putBoolean("reminder_on", value).apply() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { com.cerebrozen.app.notify.Reminders.schedule(context); persist(true) }
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        com.cerebrozen.app.notify.Reminders.schedule(context); persist(true)
    }

    SubPage("A gentle daily nudge", "Daily reminder", onBack) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Daily check-in reminder", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text("Once a day around 9am — gentle, no streak-pressure.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                Switch(checked = on, onCheckedChange = {
                    if (it) enable() else { com.cerebrozen.app.notify.Reminders.cancel(context); persist(false) }
                })
            }
        }
        TextButton(onClick = { com.cerebrozen.app.notify.Reminders.show(context) }) {
            Text("Send a test reminder now", color = Periwinkle)
        }
        Text("Delivered on-device via a scheduled alarm — no server or FCM required.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) = SubPage("How we handle your data", "Privacy policy", onBack) {
    InfoCard("Private by design", "Your journal, chat and sleep contents stay yours. Support tooling sees counts and account state — never the words.")
    InfoCard("Your controls", "Toggle what's remembered in Privacy & memory, export a full copy any time, or delete everything permanently.")
    InfoCard("No selling, ever", "We don't sell your data or use third-party ad SDKs. Analytics are first-party aggregates.")
    Text("Full policy at cerebrozen.in/privacy.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
fun DataExportScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubPage("Your data, your copy", "Export my data", onBack) {
        Text("Download everything CereBro has stored for you — mood, journal, sleep and chat.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                runCatching { Api.exportData() }
                    .onSuccess { status = "Exported ${it.length} characters of your data." }
                    .onFailure { status = it.message ?: "Export failed." }
                busy = false
            }
        }) { Text(if (busy) "Preparing…" else "Export my data") }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ok) }
    }
}

@Composable
fun AccountDeletionScreen(onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubPage("This can't be undone", "Delete account", onBack) {
        Text("Permanently erase your account and everything in it. There's no recovery.",
            style = MaterialTheme.typography.bodyMedium, color = Danger)
        if (!confirm) {
            Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete my account") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { scope.launch { runCatching { Api.deleteAccount() }; Session.signOut() } }) {
                    Text("Delete forever")
                }
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextMuted) }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
