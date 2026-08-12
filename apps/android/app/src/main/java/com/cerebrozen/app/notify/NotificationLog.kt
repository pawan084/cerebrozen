package com.cerebrozen.app.notify

import com.cerebrozen.app.net.Session
import org.json.JSONArray
import org.json.JSONObject

/**
 * What CereBro actually put in the notification shade, recorded on this device.
 *
 * The prototype's TOD-06 shows a list of "recent nudges", and Android had no
 * such record at all: a nudge existed for as long as the user left it in the
 * shade, and swiping it away erased the only evidence the app had ever spoken.
 * That is a bad property for a product whose reminders are the main way it
 * reaches someone who is not already looking at it — "did it even fire?" was
 * unanswerable, by the user and by support.
 *
 * Written by the two places that post a notification and by nothing else:
 * [Reminders.show] (the local daily alarm) and [Push.show] (a server nudge).
 * Both call [record] immediately after `notify()`, so the log describes what was
 * *delivered*, never what was merely intended.
 *
 * **Local only, and deliberately so.** These rows are a mirror of the shade, not
 * a second copy of anything the server holds; nothing here is uploaded, and
 * account deletion clears it with the rest of the prefs. The cap keeps the
 * SharedPreferences entry small — this is a recent-history view, not an archive.
 *
 * No Android or Compose types, so it is JVM unit-testable via
 * `Session.resetForTest`.
 */
object NotificationLog {
    private const val KEY = "notification_log"

    /** Newest first is how the screen reads it, so newest first is how it is stored. */
    private const val CAP = 30

    /** One delivered notification. [at] is an ISO-8601 instant; [route] is the
     * in-app destination the tap opens, or null when the nudge just opens the app. */
    data class Entry(
        val title: String,
        val body: String,
        val at: String,
        val kind: String,
        val route: String?,
    )

    /** Append a delivered notification. Blank titles are ignored — a row with no
     * title would render as an empty card and tell the user nothing. */
    fun record(title: String, body: String, at: String, kind: String, route: String? = null) {
        if (title.isBlank()) return
        val next = JSONArray()
        next.put(
            JSONObject()
                .put("title", title)
                .put("body", body)
                .put("at", at)
                .put("kind", kind)
                .put("route", route ?: JSONObject.NULL),
        )
        // Existing rows shift down; the tail past CAP falls off.
        all().take(CAP - 1).forEach { e ->
            next.put(
                JSONObject()
                    .put("title", e.title)
                    .put("body", e.body)
                    .put("at", e.at)
                    .put("kind", e.kind)
                    .put("route", e.route ?: JSONObject.NULL),
            )
        }
        runCatching { Session.prefPut(KEY, next.toString()) }
    }

    /** Newest first. A corrupt or absent entry reads as an empty log rather than
     * throwing — a notification history is never worth crashing a screen for. */
    fun all(): List<Entry> {
        val raw = runCatching { Session.prefGet(KEY) }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Entry(
                title = title,
                body = o.optString("body"),
                at = o.optString("at"),
                kind = o.optString("kind").ifBlank { "nudge" },
                route = o.optString("route").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    /** Used by the inbox's "clear" action and by account deletion. */
    fun clear() {
        runCatching { Session.prefPut(KEY, JSONArray().toString()) }
    }

    /**
     * Drop one row, matched on its instant.
     *
     * [at] rather than a list index because the screen and the store are read at
     * different moments: a nudge arriving between the render and the tap would
     * shift every index down by one and dismiss the wrong row.
     */
    fun remove(at: String): List<Entry> {
        val kept = all().filterNot { it.at == at }
        val arr = JSONArray()
        kept.forEach { e ->
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("body", e.body)
                    .put("at", e.at)
                    .put("kind", e.kind)
                    .put("route", e.route ?: JSONObject.NULL),
            )
        }
        runCatching { Session.prefPut(KEY, arr.toString()) }
        return kept
    }

    /**
     * The in-app route a nudge kind should open.
     *
     * Kept here rather than in the screen so the deeplink a *posted* notification
     * carries and the destination the *logged* row opens cannot disagree — they
     * were two hand-maintained lists in the web client and drifted within a week.
     */
    fun routeFor(kind: String): String? = when (kind) {
        // "home", not "today". The Today TAB kept the route `home` through the
        // five-tab rename precisely so deeplinks and nudges would not break —
        // this map missed that, so tapping Open on a check-in nudge called
        // navigate("today"), which matches no destination and throws
        // IllegalArgumentException. Every value here must be a real route in
        // CereBroApp's graph.
        "checkin" -> "home"
        "winddown", "sleep" -> "sleep"
        "journal" -> "journal"
        "practice" -> "toolkit"
        else -> null
    }
}
