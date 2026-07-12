package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Analytics
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.ThemeMode
import com.cerebrozen.app.ui.theme.prefValue
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
// The device-credential gate (requestScreenLock) lives in BiometricGate.kt so the
// Settings and in-Journal lock toggles share one implementation.

/** A tappable settings row (leading icon + title + subtitle + chevron). */
@Composable
internal fun NavRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    SectionCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
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
    SectionCard(onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
    }) {
        Row(
            Modifier.fillMaxWidth(),
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
                val prev = current
                current = name
                scope.launch {
                    runCatching { Api.updateProfile(JSONObject().put("companion", name)) }
                        .onFailure { current = prev }   // don't show a choice the server didn't accept
                }
            }
        }
    }
}

private val THEME_CHOICES = listOf(
    Triple(ThemeMode.System, "System default", "Follows your phone's light/dark setting"),
    Triple(ThemeMode.Night, "Always night", "The deep-indigo look, day and night"),
    Triple(ThemeMode.Dawn, "Always dawn", "A warm, light look for daytime"),
)

/** You → Appearance: pick Night, Dawn, or follow the system (REDESIGN §4.1).
 * The choice persists as `theme_mode`; Sleep always keeps the night palette. */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    SubPage("Night or dawn", "Appearance", onBack) {
        Text("Dawn is a light, warm look that's easier to read in daylight. Sleep always stays night — it's a wind-down space.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        THEME_CHOICES.forEach { (mode, title, subtitle) ->
            SelectableRow(title, subtitle, selected = AppTheme.mode == mode) {
                AppTheme.mode = mode
                Session.prefPut("theme_mode", mode.prefValue())
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
                val prev = region
                region = code
                scope.launch {
                    runCatching { Api.updateProfile(JSONObject().put("region", code)) }
                        .onFailure { region = prev }
                }
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
    var loaded by remember { mutableStateOf(false) }
    var consentError by remember { mutableStateOf<String?>(null) }
    val notice = noticeFor(noticeLang)
    val activity = LocalContext.current as? FragmentActivity
    LaunchedEffect(Unit) {
        runCatching { val c = Api.consent(); CONSENT_KEY_ORDER.forEach { consent[it] = c.optBoolean(it) } }
        runCatching { noticeLang = defaultNoticeCode(Api.me().optString("language")) }
        loaded = true
    }
    SubPage("Control what CereBro remembers", notice.title, onBack) {
        Text(notice.caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        ChipWrap(NOTICE_CODES.map { noticeFor(it).nativeName }, notice.nativeName) { picked ->
            noticeLang = NOTICE_CODES.first { noticeFor(it).nativeName == picked }
        }
        if (!loaded) {
            Text("Loading your choices…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            val layoutDir = if (noticeLang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                SectionCard {
                    CONSENT_KEY_ORDER.forEach { key ->
                        val cat = notice.categories.getValue(key)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(cat.label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                                Text(cat.hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            AppSwitch(checked = consent[key] == true, onCheckedChange = { v ->
                                // Optimistic, but reconciled: if the server write
                                // fails we revert the switch and say so, so the UI
                                // never claims a consent state the backend rejected
                                // (DPDP integrity — the toggle must not lie).
                                val prev = consent[key] == true
                                consent[key] = v
                                consentError = null
                                scope.launch {
                                    runCatching { Api.updateConsent(JSONObject().put(key, v)) }
                                        .onFailure {
                                            consent[key] = prev
                                            consentError = "Couldn't save that change — check your connection and try again."
                                        }
                                }
                            })
                        }
                    }
                }
            }
            consentError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
            }
        }
        SectionCard {
            var statsOn by remember { mutableStateOf(Analytics.enabled) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Anonymous usage stats", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Text("Counts only — never your content or account",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                AppSwitch(checked = statsOn, onCheckedChange = { v -> statsOn = v; Analytics.enabled = v })
            }
            var lockOn by remember { mutableStateOf(Session.prefGet("journal_locked") == "true") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (lockOn) "Unlock journal" else "Lock journal",
                        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Text(if (lockOn) "Turn off PIN/password protection" else "Require phone PIN, password, pattern, or biometrics",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                // Gate the change behind a screen-lock confirmation both ways —
                // only persist once the user actually authenticates.
                AppSwitch(checked = lockOn, onCheckedChange = { v ->
                    requestScreenLock(activity) { ok ->
                        if (ok) {
                            lockOn = v
                            Session.prefPut("journal_locked", v.toString())
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun PremiumScreen(onBack: () -> Unit) = SubPage("CereBro Premium", "Go deeper", onBack) {
    // First-party paywall funnel count (anonymous, opt-out; mirrors iOS).
    LaunchedEffect(Unit) { Analytics.track("paywall_view") }
    Text("Unlock the full library, longer programs, and unlimited voice — same private-by-design promise.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    PlanCard("Annual", "₹2,999 / year", "Save 37% · 7-day free trial", featured = true)
    PlanCard("Monthly", "₹399 / month", "Cancel anytime", featured = false)
    PrimaryButton(text = "Start free trial", enabled = false, modifier = Modifier.fillMaxWidth()) {}
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
    Text("CereBro is a companion, not a clinician. When you want a real person, these connect you with one.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    // Real, tappable pathways (REDESIGN §2.2) — no promises, just doors.
    SupportLinkRow("Tele-MANAS — call 14416", "Free government mental-health line · 24/7", "14416")
    SupportLinkRow("Tele-MANAS on WhatsApp", "The same counsellors, by chat", "https://wa.me/9114416")
    SupportLinkRow("iCall — talk to a counsellor", "Trained counsellors by phone · 9152987821", "9152987821")
    SupportLinkRow("Find a therapist", "Directories of professional help near you", "https://www.findahelpline.com/in")
    InfoCard("Talk to a coach", "A vetted coach directory for India is on our roadmap. Until it's real, the lines above reach real people today.")
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
                    Text("Once a day around 9am — gentle, no pressure.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                AppSwitch(checked = on, onCheckedChange = {
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

    // Credibility disclosure (REDESIGN §2.4) — honest even where the honest
    // answer is "on our roadmap".
    Text("HOW CEREBRO IS BUILT", style = MaterialTheme.typography.labelSmall, color = Periwinkle,
        modifier = Modifier.padding(top = 8.dp))
    InfoCard("Evidence, labeled", "Tools are labeled with why they work. Where something is comfort rather than therapy, we say so.")
    InfoCard("What CereBro is not", "A companion alongside care, never a replacement. It doesn't diagnose or treat.")
    InfoCard("Professional involvement", "Built with published clinical research; a formal clinical advisory process is on our roadmap.")

    Text("Full policy at cerebrozen.in/privacy.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
fun DataExportScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var exportOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubPage("Your data, your copy", "Export my data", onBack) {
        Text("Download everything CereBro has stored for you — mood, journal, sleep and chat.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        PrimaryButton(
            text = if (busy) "Preparing…" else "Export my data",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            busy = true
            scope.launch {
                runCatching { Api.exportData() }
                    .onSuccess { exportOk = true; status = "Exported ${it.length} characters of your data." }
                    .onFailure { exportOk = false; status = it.message ?: "Export failed." }
                busy = false
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (exportOk) Ok else Danger) }
    }
}

@Composable
fun AccountDeletionScreen(onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SubPage("This can't be undone", "Delete account", onBack) {
        Text("Permanently erase your account and everything in it. There's no recovery.",
            style = MaterialTheme.typography.bodyMedium, color = Danger)
        if (!confirm) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete my account", color = Danger)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton(text = if (busy) "Deleting…" else "Delete forever", enabled = !busy) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { Api.deleteAccount() }
                            .onSuccess { Session.signOut() }
                            .onFailure {
                                busy = false
                                error = it.message ?: "Couldn't delete your account. Please try again."
                            }
                    }
                }
                TextButton(onClick = { confirm = false }, enabled = !busy) { Text("Cancel", color = TextMuted) }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger) }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
