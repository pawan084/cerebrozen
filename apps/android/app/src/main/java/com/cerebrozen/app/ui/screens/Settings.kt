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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Analytics
import com.cerebrozen.app.data.Helplines
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

// i18n: pending — the companion names double as the server-side `companion`
// profile value (cross-stack contract), so this list needs a label/value split
// before its display strings can be localized.
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
    PremiumSubPage(stringResource(R.string.companion_eyebrow), stringResource(R.string.companion_title), onBack) {
        Text(stringResource(R.string.companion_intro),
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

/** You → Appearance. CereBroZen is DARK-ONLY (owner decision 2026-07-15): one calm
 * indigo theme on every screen, so nothing is half-light and half-dark. The Dawn/System
 * palettes still exist in the theme layer (and their contrast is still tested), but they
 * are no longer selectable — a light option can only reintroduce the very inconsistency
 * we removed, because several premium/sleep surfaces paint a fixed night background. */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    PremiumSubPage(stringResource(R.string.appearance_eyebrow), stringResource(R.string.appearance_title), onBack) {
        Text(stringResource(R.string.appearance_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        // The one theme, shown selected — informational, not a choice.
        SelectableRow(
            stringResource(R.string.theme_night_title),
            stringResource(R.string.theme_night_hint),
            selected = true,
        ) { AppTheme.mode = ThemeMode.Night; Session.prefPut("theme_mode", ThemeMode.Night.prefValue()) }
    }
}

@Composable
fun CrisisRegionScreen(onBack: () -> Unit) {
    // Region codes are the cross-stack contract; only the labels are localized.
    val regions = listOf(
        "" to stringResource(R.string.region_auto), "IN" to stringResource(R.string.region_in),
        "US" to stringResource(R.string.region_us), "GB" to stringResource(R.string.region_gb),
        "CA" to stringResource(R.string.region_ca), "AU" to stringResource(R.string.region_au),
        "NZ" to stringResource(R.string.region_nz), "EU" to stringResource(R.string.region_eu),
    )
    var region by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { region = Api.me().optString("region") } }
    PremiumSubPage(stringResource(R.string.crisisregion_eyebrow), stringResource(R.string.crisisregion_title), onBack) {
        Text(stringResource(R.string.crisisregion_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        regions.forEach { (code, label) ->
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
    PremiumSubPage(stringResource(R.string.privacy_control_line), notice.title, onBack) {
        Text(notice.caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        ChipWrap(NOTICE_CODES.map { noticeFor(it).nativeName }, notice.nativeName) { picked ->
            noticeLang = NOTICE_CODES.first { noticeFor(it).nativeName == picked }
        }
        if (!loaded) {
            Text(stringResource(R.string.privacy_loading), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            val consentSaveError = stringResource(R.string.privacy_consent_error)
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
                                            consentError = consentSaveError
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
                    Text(stringResource(R.string.privacy_stats_title), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Text(stringResource(R.string.privacy_stats_hint),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                AppSwitch(checked = statsOn, onCheckedChange = { v -> statsOn = v; Analytics.enabled = v })
            }
            var lockOn by remember { mutableStateOf(Session.prefGet("journal_locked") == "true") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (lockOn) stringResource(R.string.privacy_unlock_journal) else stringResource(R.string.privacy_lock_journal),
                        style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                    )
                    Text(
                        if (lockOn) stringResource(R.string.privacy_unlock_hint) else stringResource(R.string.privacy_lock_hint),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted,
                    )
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
fun PremiumScreen(onBack: () -> Unit) = PremiumSubPage(stringResource(R.string.premium_eyebrow), stringResource(R.string.premium_title), onBack) {
    // First-party paywall funnel count (anonymous, opt-out; mirrors iOS).
    LaunchedEffect(Unit) { Analytics.track("paywall_view") }
    Text(stringResource(R.string.premium_intro),
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    PlanCard(stringResource(R.string.premium_annual), stringResource(R.string.premium_annual_price),
        stringResource(R.string.premium_annual_note), featured = true)
    PlanCard(stringResource(R.string.premium_monthly), stringResource(R.string.premium_monthly_price),
        stringResource(R.string.premium_monthly_note), featured = false)
    PrimaryButton(text = stringResource(R.string.premium_cta), enabled = false, modifier = Modifier.fillMaxWidth()) {}
    Text(stringResource(R.string.premium_billing_note),
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
fun HumanSupportScreen(onBack: () -> Unit) = PremiumSubPage(stringResource(R.string.humansupport_eyebrow), stringResource(R.string.humansupport_title), onBack) {
    Text(stringResource(R.string.humansupport_intro),
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    // Real, tappable pathways (REDESIGN §2.2) — no promises, just doors. The doors are
    // this person's REGION's, served by the engine: these rows used to be India's numbers
    // as literals (Tele-MANAS, iCall, and a findahelpline.com/in deep-link that pinned the
    // international finder to India too). Renders from NEUTRAL on the first frame and
    // stays there if the network never answers — see data/Helplines.kt.
    var lines by remember { mutableStateOf(Helplines.NEUTRAL) }
    LaunchedEffect(Unit) {
        val region = runCatching { Api.me().optString("crisis_region") }.getOrDefault("")
        lines = Helplines.load(region)
    }
    lines.forEach { line -> SupportLinkRow(line.name, line.detail, line.target) }
    InfoCard(stringResource(R.string.humansupport_coach_title), stringResource(R.string.humansupport_coach_body))
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

    PremiumSubPage(stringResource(R.string.reminders_eyebrow), stringResource(R.string.reminders_title), onBack) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.reminders_toggle_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.reminders_toggle_hint),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                AppSwitch(checked = on, onCheckedChange = {
                    if (it) enable() else { com.cerebrozen.app.notify.Reminders.cancel(context); persist(false) }
                })
            }
        }
        TextButton(onClick = { com.cerebrozen.app.notify.Reminders.show(context) }) {
            Text(stringResource(R.string.reminders_test), color = Periwinkle)
        }
        Text(stringResource(R.string.reminders_delivery_note),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) = PremiumSubPage(stringResource(R.string.privacypolicy_eyebrow), stringResource(R.string.privacypolicy_title), onBack) {
    InfoCard(stringResource(R.string.privacypolicy_private_title), stringResource(R.string.privacypolicy_private_body))
    InfoCard(stringResource(R.string.privacypolicy_controls_title), stringResource(R.string.privacypolicy_controls_body))
    InfoCard(stringResource(R.string.privacypolicy_noselling_title), stringResource(R.string.privacypolicy_noselling_body))

    // Credibility disclosure (REDESIGN §2.4) — honest even where the honest
    // answer is "on our roadmap".
    Text(stringResource(R.string.privacypolicy_built_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle,
        modifier = Modifier.padding(top = 8.dp))
    InfoCard(stringResource(R.string.privacypolicy_evidence_title), stringResource(R.string.privacypolicy_evidence_body))
    InfoCard(stringResource(R.string.privacypolicy_not_title), stringResource(R.string.privacypolicy_not_body))
    InfoCard(stringResource(R.string.privacypolicy_professional_title), stringResource(R.string.privacypolicy_professional_body))

    Text(stringResource(R.string.privacypolicy_full_policy), style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
fun DataExportScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var exportOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val successTemplate = stringResource(R.string.export_success)
    val exportFailed = stringResource(R.string.export_failed)
    PremiumSubPage(stringResource(R.string.export_eyebrow), stringResource(R.string.export_title), onBack) {
        Text(stringResource(R.string.export_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        PrimaryButton(
            text = if (busy) stringResource(R.string.export_preparing) else stringResource(R.string.export_title),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            busy = true
            scope.launch {
                runCatching { Api.exportData() }
                    .onSuccess { exportOk = true; status = successTemplate.format(it.length) }
                    .onFailure { exportOk = false; status = it.message ?: exportFailed }
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
    val deleteFailed = stringResource(R.string.delete_error_fallback)
    PremiumSubPage(stringResource(R.string.delete_eyebrow), stringResource(R.string.delete_title), onBack) {
        Text(stringResource(R.string.delete_warning),
            style = MaterialTheme.typography.bodyMedium, color = Danger)
        if (!confirm) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_cta), color = Danger)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton(
                    text = if (busy) stringResource(R.string.delete_busy) else stringResource(R.string.delete_forever),
                    enabled = !busy,
                ) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { Api.deleteAccount() }
                            .onSuccess { Session.signOut() }
                            .onFailure {
                                busy = false
                                error = it.message ?: deleteFailed
                            }
                    }
                }
                TextButton(onClick = { confirm = false }, enabled = !busy) { Text(stringResource(R.string.common_cancel), color = TextMuted) }
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
