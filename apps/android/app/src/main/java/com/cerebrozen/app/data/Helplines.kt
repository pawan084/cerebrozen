package com.cerebrozen.app.data

/* Crisis helplines — served by the engine, never hardcoded here.
 *
 * ARCHITECTURE.md §Cross-stack contracts already required this ("Crisis regions/helplines
 * config | Engine | Android crisis screen | never hardcoded in clients"). The app violated
 * it: one country's numbers were literals in Crisis.kt / Settings.kt / strings.xml, while
 * Settings offered a region picker whose answer nothing read. A user in the UK could pick
 * "GB" and still be shown India's Tele-MANAS. That is the bug this file closes.
 *
 * OFFLINE IS THE HARD PART, and it is why the numbers were inlined in the first place — a
 * crisis screen that needs the network is worse than one with wrong numbers. So there are
 * three layers, and the screen always renders:
 *
 *   1. the engine's answer for the person's region (Session caches every GET);
 *   2. the last cached answer, served automatically by Session on a connectivity failure;
 *   3. NEUTRAL below — the built-in floor for a first run with no network.
 *
 * NEUTRAL is deliberately identical to what the engine returns for an unknown region
 * (app/safety/helplines.py::_INTERNATIONAL): an international finder that routes to the
 * caller's own country. It names NO country, because on this path we do not know one —
 * and a confident guess is exactly the failure being fixed. Keep it that way: any
 * country's number added here is a regression, and HelplinesTest fails on it.
 */

import com.cerebrozen.app.net.Api
import org.json.JSONArray
import org.json.JSONObject

/** One dialable row. [kind] is "tel" or "url" — the engine says which, so this client
 *  never sniffs a target to decide how to act on it. */
data class Helpline(
    val name: String,
    val detail: String,
    val target: String,
    val kind: String,
) {
    val isUrl: Boolean get() = kind == "url"
}

object Helplines {
    /** The floor. Region-neutral by construction — see the file header. */
    val NEUTRAL: List<Helpline> = listOf(
        Helpline(
            name = "Find a helpline in your country",
            detail = "International directory · routes to your region",
            target = "https://findahelpline.com",
            kind = "url",
        ),
    )

    internal fun parse(rows: JSONArray): List<Helpline> =
        (0 until rows.length()).mapNotNull { i ->
            val o: JSONObject = rows.optJSONObject(i) ?: return@mapNotNull null
            val target = o.optString("target")
            val kind = o.optString("kind")
            // Drop anything malformed rather than render a row that does nothing when
            // tapped. If that empties the list, load() falls back to NEUTRAL.
            if (target.isBlank() || (kind != "tel" && kind != "url")) return@mapNotNull null
            Helpline(o.optString("name"), o.optString("detail"), target, kind)
        }

    /**
     * The helplines to show. Never throws and never returns an empty list: on this screen
     * an exception or a blank list is the worst available outcome, so every failure path
     * lands on [NEUTRAL].
     *
     * [region] comes from the platform's resolved `crisis_region` on /users/me (the
     * person's own choice, else their org's default, else "" for unknown).
     */
    suspend fun load(region: String): List<Helpline> {
        val parsed = runCatching {
            val body = JSONObject(Api.helplinesRaw(region))
            parse(body.optJSONArray("helplines") ?: JSONArray())
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { NEUTRAL }
    }
}
