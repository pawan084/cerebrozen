package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Analytics
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
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
import com.cerebrozen.app.ui.theme.PickRowFill
import com.cerebrozen.app.ui.theme.PickRowSelectedFill
import com.cerebrozen.app.ui.theme.PickRowStroke
import com.cerebrozen.app.ui.theme.PickRowChevron
import com.cerebrozen.app.ui.theme.ChipSelectedInk
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
    /** Overrides the icon tint. For rows whose destination is destructive: on
     * You, "Delete account · Permanently erase everything" was pixel-identical
     * to "Privacy policy · How we handle your data", so the single most
     * irreversible action in the app looked exactly like a link to a document. */
    tint: Color? = null,
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
                if (icon != null) Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        .background((tint ?: if (emphasis) Cyan else Periwinkle).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null,
                        tint = tint ?: if (emphasis) Cyan else Periwinkle, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = if (emphasis) Cyan else TextSoft)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
            // AutoMirrored so the affordance points the right way in RTL
            // (the DPDP notice renders Urdu right-to-left — PrivacyScreen).
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted2,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (selected) PickRowSelectedFill else PickRowFill)
                .border(1.dp, PickRowStroke, shape)
                .clickable { Haptics.selection(); onClick() }
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = if (selected) ChipSelectedInk else TextSoft)
                // The title already flips to ChipSelectedInk on select; the
                // subtitle did not, so it kept a page-ink token on the filled
                // accent row and measured 1.26:1 — the selected option was the
                // one you could not read. Softened rather than equal so the
                // title still leads.
                if (subtitle.isNotBlank()) Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) ChipSelectedInk.copy(alpha = 0.82f) else TextMuted,
                )
            }
            if (selected) Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.common_selected_cd),
                tint = if (selected) ChipSelectedInk else PickRowChevron,
                modifier = Modifier.size(20.dp),
            )
        }
}

/** A companion style. [value] is the server-side `companion` profile value —
 * a cross-stack contract with iOS/web, so it is NEVER translated; the title and
 * detail are display copy and localize freely. */
private data class CompanionStyle(
    val value: String,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val detailRes: Int,
)

private val COMPANIONS = listOf(
    CompanionStyle("Calm Guide", R.string.companion_calm_title, R.string.companion_calm_detail),
    CompanionStyle("Warm Friend", R.string.companion_warm_title, R.string.companion_warm_detail),
    CompanionStyle("Straight Talker", R.string.companion_direct_title, R.string.companion_direct_detail),
    CompanionStyle("Quiet Coach", R.string.companion_quiet_title, R.string.companion_quiet_detail),
)

/** Display label for a PERSISTED companion value (You header/rows render
 * server data); unknown values show as stored. */
internal fun companionLabelRes(value: String): Int? =
    COMPANIONS.firstOrNull { it.value == value }?.titleRes

@Composable
fun CompanionStyleScreen(onBack: () -> Unit) {
    // null = not yet known — a failed read used to leave "" (no row selected,
    // nothing said) and any pick then overwrote a server value this screen
    // never actually learned (audit B21).
    var current by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        loadFailed = false
        runCatching { Api.me().optString("companion") }
            .onSuccess { current = it }
            .onFailure { loadFailed = true }
    }
    PremiumSubPage(stringResource(R.string.companion_eyebrow), stringResource(R.string.companion_title), onBack) {
        Text(stringResource(R.string.companion_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        if (loadFailed) {
            Text(stringResource(R.string.crisisregion_load_failed),
                style = MaterialTheme.typography.bodySmall, color = Danger)
            TextButton(onClick = { reloadKey++ }) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
        }
        COMPANIONS.forEach { style ->
            SelectableRow(
                stringResource(style.titleRes), stringResource(style.detailRes),
                selected = current == style.value,
            ) {
                val prev = current
                current = style.value
                scope.launch {
                    runCatching { Api.updateProfile(JSONObject().put("companion", style.value)) }
                        .onFailure { current = prev }   // don't show a choice the server didn't accept
                }
            }
        }
    }
}

/**
 * You → Language: the language CereBro *writes back in*.
 *
 * Onboarding asked this once and nothing could change it afterwards — the You
 * profile row rendered the saved value ("Calm Guide · Hindi") but opened the
 * companion picker, so a wrong tap on the first run was permanent.
 *
 * What it actually controls is the backend's reply directive
 * (`services/language.py`): chat replies, the agentic plan, Oracle and starter
 * topics. It does NOT re-translate the app's own chrome — that follows the
 * device locale (`values-hi/`) — and it does not translate helpline names or
 * numbers, which `services/crisis.py` returns verbatim by design. The intro copy
 * says exactly that rather than implying a full app translation.
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // null = not yet known, the same honesty rule the companion and region
    // pickers follow: a failed read must select nothing rather than render
    // "English" as a confident answer the screen never actually learned.
    var current by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        loadFailed = false
        runCatching { Api.me().optString("language") }
            .onSuccess { current = it }
            .onFailure { loadFailed = true }
    }
    PremiumSubPage(stringResource(R.string.language_eyebrow), stringResource(R.string.language_title), onBack) {
        Text(stringResource(R.string.language_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        if (loadFailed) {
            Text(stringResource(R.string.crisisregion_load_failed),
                style = MaterialTheme.typography.bodySmall, color = Danger)
            TextButton(onClick = { reloadKey++ }) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
        }
        LANGUAGES.forEach { option ->
            SelectableRow(
                stringResource(option.labelRes), "",
                selected = current == option.id,
            ) {
                val prev = current
                current = option.id
                applyOnboardingLanguage(context, option.id)
                scope.launch {
                    // The wire value stays the English string (cross-stack
                    // contract with iOS/web); only the label localizes.
                    runCatching { Api.updateProfile(JSONObject().put("language", option.id)) }
                        .onFailure {
                            current = prev
                            prev?.let { applyOnboardingLanguage(context, it) }
                        }   // never show a choice the server refused
                }
            }
        }
        // The field is free text server-side (services/language.py passes an
        // unknown value straight to the model), so a profile set from another
        // client can hold something this list does not offer. Show it as its own
        // selected row instead of leaving the screen looking unanswered.
        current?.takeIf { it.isNotBlank() && LANGUAGES.none { opt -> opt.id == it } }?.let { custom ->
            SelectableRow(custom, stringResource(R.string.language_custom_hint), selected = true) {}
        }
        Text(stringResource(R.string.language_crisis_note),
            style = MaterialTheme.typography.bodySmall, color = TextMuted2)
    }
}

/** You → Appearance: pick Night, Dawn, or follow the system (REDESIGN §4.1).
 * The choice persists as `theme_mode` and applies to every app screen. */
@Composable
fun AppearanceScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val themeChoices = listOf(
        Triple(ThemeMode.System, stringResource(R.string.theme_system_title), stringResource(R.string.theme_system_hint)),
        Triple(ThemeMode.Night, stringResource(R.string.theme_night_title), stringResource(R.string.theme_night_hint)),
        Triple(ThemeMode.Dawn, stringResource(R.string.theme_dawn_title), stringResource(R.string.theme_dawn_hint)),
    )
    PremiumSubPage(stringResource(R.string.appearance_eyebrow), stringResource(R.string.appearance_title), onBack) {
        Text(stringResource(R.string.appearance_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        themeChoices.forEach { (mode, title, subtitle) ->
            SelectableRow(title, subtitle, selected = AppTheme.mode == mode) {
                AppTheme.mode = mode
                Session.prefPut("theme_mode", mode.prefValue())
            }
        }
        // V2-e part 2: language lives behind the same You row as appearance
        // ("Appearance & language" — REDESIGN_V2 §3.7), so its door is here.
        NavRow(
            stringResource(R.string.you_language_title),
            stringResource(R.string.language_intro_short),
            icon = Icons.Outlined.Translate,
        ) { onOpen("language") }
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
    // null = not yet known. A failed read used to leave "" — which rendered the
    // "Auto-detect" row as confidently SELECTED, a false answer about crisis
    // routing. Unknown now selects nothing and says so.
    var region by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        loadFailed = false
        runCatching { Api.me().optString("region") }
            .onSuccess {
                region = it
                // Mirror for the offline-first crisis surfaces (CrisisDirectory).
                runCatching { Session.prefPut(CRISIS_REGION_PREF, it) }
            }
            .onFailure { loadFailed = true }
    }
    PremiumSubPage(stringResource(R.string.crisisregion_eyebrow), stringResource(R.string.crisisregion_title), onBack) {
        Text(stringResource(R.string.crisisregion_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        if (loadFailed) {
            Text(stringResource(R.string.crisisregion_load_failed),
                style = MaterialTheme.typography.bodySmall, color = Danger)
            TextButton(onClick = { reloadKey++ }) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
        }
        regions.forEach { (code, label) ->
            SelectableRow(label, "", selected = region == code) {
                val prev = region
                region = code
                scope.launch {
                    runCatching { Api.updateProfile(JSONObject().put("region", code)) }
                        .onSuccess { runCatching { Session.prefPut(CRISIS_REGION_PREF, code) } }
                        .onFailure { region = prev }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val consent = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    // DPDP s.5(3): the consent notice is readable in English or an
    // Eighth-Schedule language, picked right on the notice (ConsentNotice.kt).
    var noticeLang by remember { mutableStateOf("en") }
    var loaded by remember { mutableStateOf(false) }
    var consentError by remember { mutableStateOf<String?>(null) }
    // Onboarding's consent write can fail after the funnel is gone; it leaves a
    // flag rather than passing silently. These switches show what the server
    // actually holds, so the honest thing is to say the setup choices were lost
    // and let the user set them again here. Cleared once they've seen it.
    var setupSyncFailed by remember {
        mutableStateOf(runCatching { Session.prefGet(CONSENT_SYNC_FAILED_KEY) }.getOrNull() == "true")
    }
    val notice = noticeFor(noticeLang)
    val activity = LocalContext.current as? FragmentActivity
    // A failed read must not render as "you consented to nothing".
    //
    // This used to be a bare runCatching with no failure branch: if the GET threw
    // and there was no cached copy, the map stayed empty, every switch drew OFF,
    // and the screen said nothing. On a consent surface that is not a blank
    // state, it is a false statement about what the user has agreed to — and the
    // obvious reaction, re-toggling, would write consents they already had.
    var consentLoadFailed by remember { mutableStateOf(false) }
    fun loadConsent() {
        // Guests have no server consent record — the read can never succeed, and
        // rendering its failure as "we could not reach the server" was a false
        // statement (audit K): the server was fine; there is simply no account.
        if (Session.guestMode) { loaded = true; return }
        consentLoadFailed = false
        scope.launch {
            runCatching { Api.consent() }
                .onSuccess { c -> CONSENT_KEY_ORDER.forEach { consent[it] = c.optBoolean(it) } }
                .onFailure { consentLoadFailed = true }
            runCatching { noticeLang = defaultNoticeCode(Api.me().optString("language")) }
            loaded = true
        }
    }
    // Without this the screen drew every consent switch from its initial state —
    // i.e. all off — before the real values arrived (2026-07-31 audit).
    LaunchedEffect(Unit) { loadConsent() }
    PremiumSubPage(stringResource(R.string.privacy_control_line), notice.title, onBack) {
        Text(notice.caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        // The picker was thirteen unlabelled chips filling the first screen, with
        // nothing saying what they change — easily read as the app's language
        // rather than the notice's (DPDP s.5(3): the notice must be readable in
        // English or an Eighth-Schedule language, which is what this is for).
        Text(stringResource(R.string.privacy_notice_language_label),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        ChipWrap(NOTICE_CODES.map { noticeFor(it).nativeName }, notice.nativeName) { picked ->
            noticeLang = NOTICE_CODES.first { noticeFor(it).nativeName == picked }
        }
        if (!loaded) {
            Text(stringResource(R.string.privacy_loading), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else if (Session.guestMode) {
            // No switches for a guest: they would show a consent state no server
            // holds, and every write would fail. The gate says what is true —
            // nothing is collected — and where choices live.
            GuestSignInCard(onOpen = onOpen, subtitle = stringResource(R.string.guest_gate_privacy))
        } else {
            if (setupSyncFailed) {
                SectionCard {
                    Text(stringResource(R.string.privacy_setup_sync_failed_title),
                        style = MaterialTheme.typography.titleMedium, color = Danger)
                    Text(stringResource(R.string.privacy_setup_sync_failed_body),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    TextButton(onClick = {
                        setupSyncFailed = false
                        runCatching { Session.prefPut(CONSENT_SYNC_FAILED_KEY, "false") }
                    }) { Text(stringResource(R.string.common_got_it), color = Periwinkle) }
                }
            }
            if (consentLoadFailed) {
                SectionCard {
                    Text(stringResource(R.string.privacy_consent_load_failed_title),
                        style = MaterialTheme.typography.titleMedium, color = Danger)
                    Text(stringResource(R.string.privacy_consent_load_failed_body),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    TextButton(onClick = { loadConsent() }) {
                        Text(stringResource(R.string.common_try_again), color = Periwinkle)
                    }
                }
            }
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
                            AppSwitch(label = cat.label, checked = consent[key] == true, onCheckedChange = { v ->
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
                AppSwitch(checked = statsOn, onCheckedChange = { v -> statsOn = v; Analytics.enabled = v }, label = stringResource(R.string.privacy_stats_title))
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
                AppSwitch(
                    // Same conditional the visible label uses, so the announced
                    // name matches what is on screen in both states.
                    label = if (lockOn) stringResource(R.string.privacy_unlock_journal) else stringResource(R.string.privacy_lock_journal),
                    checked = lockOn, onCheckedChange = { v ->
                    requestScreenLock(activity) { ok ->
                        if (ok) {
                            lockOn = v
                            Session.prefPut("journal_locked", v.toString())
                        }
                    }
                })
            }
        }

        // V2-e part 2: the legal trio lives WITH the privacy controls it
        // documents — the You list carried these as three more top-level rows
        // (REDESIGN_V2 §3.7: You ≈ 10 rows, legal inside Privacy & data).
        Text(
            stringResource(R.string.you_legal_header),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle,
            modifier = Modifier.padding(top = 8.dp),
        )
        NavRow(stringResource(R.string.privacypolicy_title), stringResource(R.string.privacypolicy_eyebrow),
            icon = Icons.Outlined.Shield) { onOpen("privacypolicy") }
        NavRow(stringResource(R.string.export_title), stringResource(R.string.you_export_subtitle),
            icon = Icons.Outlined.FileDownload) { onOpen("export") }
        // Danger-tinted, exactly as it was on You: the one irreversible action
        // must not dress like a link to a document.
        NavRow(stringResource(R.string.delete_title), stringResource(R.string.you_delete_subtitle),
            icon = Icons.Outlined.DeleteOutline, tint = Danger) { onOpen("delete") }
    }
}

@Composable
fun PremiumScreen(onBack: () -> Unit) {
    // Three states, not two. Premium can be bought, or an organisation can
    // sponsor the seat — and this screen used to show the same price list to
    // everyone, which invites a member whose employer already pays to pay
    // again. The server resolves which it is (services/entitlements.py) and
    // reports it on /auth/me; the client only has to stop assuming.
    //
    // Seeded from the last known answer so a failed profile read does not
    // demote a sponsored member back to a paywall. Nothing is unlocked on the
    // strength of it — the quota and media gates are enforced server-side
    // whatever this device believes.
    var tier by remember { mutableStateOf(Session.cachedTier()) }
    var sponsored by remember { mutableStateOf(Session.cachedSponsored()) }
    var resolved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { Api.me() }.onSuccess { me ->
            tier = me.optString("subscription_tier").ifBlank { "free" }
            sponsored = me.optBoolean("sponsored")
            Session.rememberEntitlement(tier, sponsored)
        }
        resolved = true
    }
    // Only an actual paywall counts as a paywall view (anonymous, opt-out;
    // mirrors iOS). This used to fire for everyone who opened the screen,
    // which put members who could not convert — because they already have
    // premium — into the denominator of the conversion rate. Held until the
    // tier resolves so the cached guess is never what fires it.
    LaunchedEffect(resolved, tier, sponsored) {
        if (resolved && !sponsored && tier == "free") Analytics.track("paywall_view")
    }

    PremiumSubPage(
        eyebrow = if (sponsored) stringResource(R.string.premium_sponsored_eyebrow)
                  else stringResource(R.string.premium_eyebrow),
        title = if (sponsored) stringResource(R.string.premium_sponsored_title)
                else stringResource(R.string.premium_title),
        onBack = onBack,
    ) {
        when {
            sponsored -> {
                // Nothing here is for sale to this member, so the price list
                // would be an invitation to buy what they already have. What
                // takes its place is the question this screen raises the
                // moment an employer is named: who pays, and what do they see.
                Text(stringResource(R.string.premium_sponsored_intro),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                InfoCard(stringResource(R.string.premium_sponsored_seen_title),
                    stringResource(R.string.premium_sponsored_seen_body))
                InfoCard(stringResource(R.string.premium_sponsored_ends_title),
                    stringResource(R.string.premium_sponsored_ends_body))
            }
            tier != "free" -> {
                // Bought, but not on this device — Android can neither sell
                // nor cancel a subscription yet, so the honest thing is to
                // name where it can be changed rather than show prices again.
                Text(stringResource(R.string.premium_active_intro),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                InfoCard(stringResource(R.string.premium_active_manage_title),
                    stringResource(R.string.premium_active_manage_body))
            }
            else -> {
                Text(stringResource(R.string.premium_intro),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                PlanCard(stringResource(R.string.premium_annual), stringResource(R.string.premium_annual_price),
                    stringResource(R.string.premium_annual_note), featured = true)
                PlanCard(stringResource(R.string.premium_monthly), stringResource(R.string.premium_monthly_price),
                    stringResource(R.string.premium_monthly_note), featured = false)
                // No dead CTA: the permanently-disabled "Start free trial"
                // button walked users into a paywall where nothing could be
                // bought (audit H1). Until Play Billing is configured, the
                // honest state is pricing for transparency + the note — a
                // PrimaryButton returns with the Play Console setup (external
                // blocker, ledgered in TODO.md).
                Text(stringResource(R.string.premium_billing_note),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
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
    // Region-aware, same directory as every crisis surface (audit K: this
    // screen was India-hardcoded while Urgent support followed the region —
    // the app's two safety surfaces disagreed about where the user was).
    // Real, tappable pathways (REDESIGN §2.2) — no promises, just doors.
    // The dial/URL targets are contracts and stay literal.
    val region by rememberCrisisRegion()
    if (normalizeRegion(region) == "IN") {
        SupportLinkRow(stringResource(R.string.humansupport_telemanas_title), stringResource(R.string.humansupport_telemanas_detail), "14416")
        // W25: the WhatsApp row was removed — no official national Tele-MANAS
        // WhatsApp exists (wa.me/9114416 was an invalid target). Voice line stays.
        SupportLinkRow(stringResource(R.string.humansupport_icall_title), stringResource(R.string.humansupport_icall_detail), "9152987821")
        SupportLinkRow(stringResource(R.string.humansupport_find_title), stringResource(R.string.humansupport_find_detail), "https://www.findahelpline.com/in")
        // The coach-directory note is an India roadmap statement; it stays an
        // India-only card rather than promising other markets a plan.
        InfoCard(stringResource(R.string.humansupport_coach_title), stringResource(R.string.humansupport_coach_body))
    } else {
        val line = primaryCrisisLine(region)
        SupportLinkRow(stringResource(line.nameRes), stringResource(R.string.humansupport_line_detail), line.target)
        val finderPath = normalizeRegion(region).lowercase(java.util.Locale.ROOT)
        SupportLinkRow(
            stringResource(R.string.humansupport_find_title),
            stringResource(R.string.humansupport_find_detail),
            if (finderPath.isEmpty()) "https://www.findahelpline.com/" else "https://www.findahelpline.com/$finderPath",
        )
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cerebro", Context.MODE_PRIVATE) }
    var on by remember { mutableStateOf(prefs.getBoolean("reminder_on", false)) }
    // The hour finally has a home outside onboarding: the toggle and the boot
    // re-arm now schedule at this stored choice instead of a hardcoded 9
    // (audit A2-A4), and it can be changed here at any time.
    var hour by remember { mutableStateOf(com.cerebrozen.app.notify.Reminders.storedHour(context)) }
    // A permission denial used to be a silent no-op: the switch just didn't
    // move, with no words and no path (audit H5/B93). Denied says denied.
    var denied by remember { mutableStateOf(false) }
    // Re-check on every resume: the user may revoke POST_NOTIFICATIONS in
    // system settings while the toggle still claims reminders are on.
    var permTick by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val notifsBlocked = remember(permTick) {
        Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    fun persist(value: Boolean) { on = value; prefs.edit().putBoolean("reminder_on", value).apply() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            denied = false
            com.cerebrozen.app.notify.Reminders.schedule(context); persist(true)
        } else {
            denied = true
        }
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
    fun openNotificationSettings() {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
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
                AppSwitch(label = stringResource(R.string.reminders_toggle_title), checked = on, onCheckedChange = {
                    if (it) enable() else { com.cerebrozen.app.notify.Reminders.cancel(context); persist(false) }
                })
            }
        }
        // The reminder time, visible and editable. Onboarding's morning/evening
        // chip used to be the only writer, and any toggle lost it.
        SectionCard(onClick = {
            android.app.TimePickerDialog(
                context,
                { _, h, _ ->
                    hour = h
                    if (on) com.cerebrozen.app.notify.Reminders.schedule(context, h)
                    else com.cerebrozen.app.notify.Reminders.rememberHour(context, h)
                },
                hour, 0, android.text.format.DateFormat.is24HourFormat(context),
            ).show()
        }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.reminders_time_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.reminders_time_hint),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                Text(
                    java.time.LocalTime.of(hour, 0).format(
                        java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT),
                    ),
                    style = MaterialTheme.typography.titleMedium, color = Periwinkle,
                )
            }
        }
        if (denied || (on && notifsBlocked)) {
            SectionCard {
                Text(stringResource(R.string.reminders_perm_denied),
                    style = MaterialTheme.typography.bodyMedium, color = Danger)
                TextButton(onClick = { openNotificationSettings() }) {
                    Text(stringResource(R.string.reminders_open_settings), color = Periwinkle)
                }
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

    Text(stringResource(R.string.privacypolicy_full_policy), style = MaterialTheme.typography.bodySmall, color = TextMuted)
}

@Composable
fun DataExportScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var exportOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    // Held in memory only for the share sheet — deliberately NOT cached by
    // Session (see cacheablePath) and gone when the screen goes.
    var payload by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
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
                    .onSuccess { exportOk = true; payload = it; status = successTemplate.format(it.length) }
                    .onFailure { exportOk = false; payload = null; status = it.userMessage(exportFailed) }
                busy = false
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (exportOk) Ok else Danger) }
        // The point of a DPDP export is to LEAVE with your data. The screen
        // used to report a character count and stop — the payload was
        // unreachable (audit H3). The system share sheet reaches files, drive,
        // mail — the user picks where their data goes.
        payload?.let { data ->
            PrimaryButton(
                text = stringResource(R.string.export_share),
                modifier = Modifier.fillMaxWidth(),
            ) {
                runCatching {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, ctx.getString(R.string.export_share_subject))
                        putExtra(android.content.Intent.EXTRA_TEXT, data)
                    }
                    ctx.startActivity(android.content.Intent.createChooser(send, null))
                }
            }
            Text(stringResource(R.string.export_share_note),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
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
                                error = it.userMessage(deleteFailed)
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
