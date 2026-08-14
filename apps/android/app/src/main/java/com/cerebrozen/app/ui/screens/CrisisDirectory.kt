package com.cerebrozen.app.ui.screens

import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import java.util.Locale

/**
 * Region-aware crisis directory — the Android mirror of
 * `backend/app/services/crisis.py` and iOS `CrisisResources.swift`.
 * Cross-stack contract: change all three together (table in ARCHITECTURE.md).
 *
 * Region codes and dial/URL targets are contracts and stay literal; only the
 * display names localize. Order mirrors the backend: emergency first, then the
 * mental-health line — except India, where Tele-MANAS leads every crisis
 * surface (REDESIGN §2.3).
 */
data class CrisisLine(
    @StringRes val nameRes: Int,
    val target: String,
    /** The mental-health line — what one-tap pills dial. Never the emergency number. */
    val isCrisisLine: Boolean = false,
)

/** Pref key mirroring the server-side `region` profile field, so crisis
 * surfaces stay region-correct with no network. */
internal const val CRISIS_REGION_PREF = "crisis_region"

internal fun normalizeRegion(raw: String?): String =
    (raw ?: "").trim().uppercase(Locale.ROOT).take(2)

internal fun crisisLinesFor(regionRaw: String?): List<CrisisLine> =
    when (normalizeRegion(regionRaw)) {
        "US" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "911"),
            CrisisLine(R.string.crisis_line_us_lifeline, "988", isCrisisLine = true),
        )
        "CA" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "911"),
            CrisisLine(R.string.crisis_line_ca_lifeline, "988", isCrisisLine = true),
        )
        "GB" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "999"),
            CrisisLine(R.string.crisis_line_samaritans, "116 123", isCrisisLine = true),
        )
        "IE" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "112"),
            CrisisLine(R.string.crisis_line_samaritans, "116 123", isCrisisLine = true),
        )
        "AU" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "000"),
            CrisisLine(R.string.crisis_line_au_lifeline, "13 11 14", isCrisisLine = true),
        )
        "NZ" -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "111"),
            CrisisLine(R.string.crisis_line_nz_1737, "1737", isCrisisLine = true),
        )
        "IN" -> listOf(
            CrisisLine(R.string.crisis_line_telemanas, "14416", isCrisisLine = true),
            CrisisLine(R.string.crisis_line_emergency, "112"),
            CrisisLine(R.string.crisis_line_kiran, "1800-599-0019"),
        )
        // EU/GSM emergency number + the international helpline finder.
        else -> listOf(
            CrisisLine(R.string.crisis_line_emergency, "112"),
            CrisisLine(R.string.crisis_line_find_helpline, "findahelpline.com", isCrisisLine = true),
        )
    }

/** What a one-tap support pill targets: the region's mental-health line, or
 * the helpline finder where no region-specific line is known. */
internal fun primaryCrisisLine(regionRaw: String?): CrisisLine =
    crisisLinesFor(regionRaw).first { it.isCrisisLine }

/** Effective crisis region: the explicit profile override when set, else the
 * country the device reports. */
internal fun effectiveRegion(stored: String?, deviceCountry: String?): String {
    val explicit = normalizeRegion(stored)
    return if (explicit.isNotEmpty()) explicit else normalizeRegion(deviceCountry)
}

/**
 * Where this phone actually is — **not** what language its UI is set to.
 *
 * This used to read `Locale.getDefault().country`, and that is a question about
 * language, not geography. Found on a real handset (2026-08-14): a OnePlus sold
 * in India ships `persist.sys.locale=en-GB` out of the box, while its SIM,
 * network and timezone all said `IN`. The You screen therefore offered
 * "Samaritans · 116 123" — a UK number that does not answer from India — to a
 * user standing in India, and Tele-MANAS, which the design rule says leads
 * every crisis surface (REDESIGN §2.3), was nowhere on the screen.
 *
 * en-GB is a factory default across OnePlus, Oppo, Xiaomi and Realme handsets
 * sold in India, so this was not an edge case in the primary market.
 *
 * The order is deliberate:
 *  - **network** first — where the handset is registered *right now*, so a
 *    visitor gets the numbers that work where they are standing rather than the
 *    ones from home;
 *  - **SIM** next — right whenever there is no service, which is exactly when
 *    someone may be reaching for an emergency number;
 *  - **locale** last, because it is better than nothing and is all a wifi-only
 *    tablet has.
 *
 * Every lookup is wrapped: `TelephonyManager` is absent on non-telephony
 * devices and some OEM builds throw from these getters. A crisis surface must
 * degrade to a worse answer, never to a crash.
 */
internal fun deviceCrisisCountry(context: Context): String {
    val tm = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
    val network = runCatching { tm?.networkCountryIso }.getOrNull()
    val sim = runCatching { tm?.simCountryIso }.getOrNull()
    return normalizeRegion(network)
        .ifEmpty { normalizeRegion(sim) }
        .ifEmpty { normalizeRegion(Locale.getDefault().country) }
}

@StringRes
internal fun regionLabelRes(regionRaw: String?): Int = when (normalizeRegion(regionRaw)) {
    "IN" -> R.string.region_in
    "US" -> R.string.region_us
    "GB" -> R.string.region_gb
    "CA" -> R.string.region_ca
    "AU" -> R.string.region_au
    "NZ" -> R.string.region_nz
    "IE" -> R.string.region_ie
    else -> R.string.region_intl
}

/**
 * The effective crisis region for this user, offline-first: seeded from the
 * pref mirror + device locale immediately (a crisis surface must never wait on
 * a network), then refreshed from the profile when reachable.
 */
@Composable
internal fun rememberCrisisRegion(): State<String> {
    val context = LocalContext.current
    // Resolved once and reused for both the seed and the refresh: two calls
    // could disagree mid-flight (a SIM registering while the profile loads) and
    // flip the helpline under the user's thumb.
    val device = remember(context) { deviceCrisisCountry(context) }
    val region = remember {
        mutableStateOf(
            effectiveRegion(
                runCatching { Session.prefGet(CRISIS_REGION_PREF) }.getOrNull(),
                device,
            ),
        )
    }
    LaunchedEffect(Unit) {
        runCatching { Api.me().optString("region") }.onSuccess { stored ->
            runCatching { Session.prefPut(CRISIS_REGION_PREF, stored) }
            region.value = effectiveRegion(stored, device)
        }
    }
    return region
}
