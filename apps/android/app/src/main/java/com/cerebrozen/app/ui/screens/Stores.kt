package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.net.Session
import org.json.JSONArray

// Local-first stores on the same Store seam the tokens use (JVM unit-testable
// via Session.resetForTest). These mirror iOS AppState fields that deliberately
// never leave the device.

/** Favourite sleep sounds/stories, keyed by title (mirrors iOS favoriteSleep). */
internal object SleepFavs {
    private const val KEY = "sleep_favorites"
    fun all(): Set<String> = readList(KEY).toSet()
    fun toggle(title: String): Set<String> {
        val s = all().toMutableSet()
        if (!s.add(title)) s.remove(title)
        Session.prefPut(KEY, JSONArray(s.toList()).toString())
        return s
    }
}

/** Gratitude-garden entries (newest last, capped so the pref stays small). */
internal object Gratitude {
    private const val KEY = "gratitude_garden"
    private const val CAP = 50
    fun all(): List<String> = readList(KEY)
    fun add(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return all()
        val next = (all() + t).takeLast(CAP)
        Session.prefPut(KEY, JSONArray(next).toString())
        return next
    }
}

/** The honest "before" measurement (two 1–5 scales; mirrors iOS baseline).
 * The first save wins the date so the starting point stays a starting point. */
internal object BaselineStore {
    fun get(): Triple<Int, Int, String>? {
        val stress = Session.prefGet("baseline_stress")?.toIntOrNull() ?: return null
        val sleep = Session.prefGet("baseline_sleep")?.toIntOrNull() ?: return null
        return Triple(stress, sleep, Session.prefGet("baseline_date").orEmpty())
    }

    fun set(stress: Int, sleep: Int, date: String) {
        if (Session.prefGet("baseline_date") == null) Session.prefPut("baseline_date", date)
        Session.prefPut("baseline_stress", stress.toString())
        Session.prefPut("baseline_sleep", sleep.toString())
    }
}

private fun readList(key: String): List<String> =
    Session.prefGet(key)
        ?.let { runCatching { JSONArray(it) }.getOrNull() }
        ?.let { a -> (0 until a.length()).map(a::getString) }
        ?: emptyList()
