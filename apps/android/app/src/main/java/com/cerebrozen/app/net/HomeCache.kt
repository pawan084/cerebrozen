package com.cerebrozen.app.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Home's data ("Today"), warmed ONCE during [Session.warmBoot] so the first real frame
 * arrives already populated instead of assembling piece by piece — name, then the check-in
 * card, then the streak — as three separate, uncoordinated network calls each resolved on
 * its own timeline (the gap this closes: `TodayHome` called `Api.me()`, `CheckInCard` called
 * `Api.moods()` + `Api.streak()`, independently, with nothing racing or caching them).
 *
 * `TodayHome`/`CheckInCard` read this cache FIRST and only fall back to their own network
 * call when it's empty (a warm start that raced ahead of [warm], or a fresh sign-in). All
 * fields are Compose-observable ([mutableStateOf]), so a pull-to-refresh's [warm] call
 * updates every reader automatically — no manual callback wiring between them.
 */
object HomeCache {
    var name by mutableStateOf<String?>(null)
        private set
    var moods by mutableStateOf<JSONArray?>(null)
        private set
    var streak by mutableStateOf<Int?>(null)
        private set
    var activeProgram by mutableStateOf<JSONObject?>(null)
        private set
    /** A coaching session left mid-turn, waiting to be picked back up — surfaced on Home's
     *  FocusCard (HOME_SPEC #25) so "Talk it through" doesn't read as generic when there is
     *  something specific to return to. CoachScreen resolves the actual restore itself via
     *  the same endpoint; this is only the HINT that it exists. */
    var resumable by mutableStateOf(false)
        private set

    /** True only when EVERY call failed — a single flaky endpoint must not blank a screen
     *  that still has two-thirds of its data to show. */
    var failed by mutableStateOf(false)
        private set

    /** Fetch Home's data CONCURRENTLY. Never throws: each call is independently
     *  best-effort, so one failing endpoint doesn't cost the others their result. */
    suspend fun warm() = coroutineScope {
        val meD = async { runCatching { Api.me() } }
        val moodsD = async { runCatching { Api.moods() } }
        val streakD = async { runCatching { Api.streak() } }
        val programD = async { runCatching { Api.activeProgram() } }
        val resumableD = async { runCatching { Api.resumableSession() } }

        val meR = meD.await()
        val moodsR = moodsD.await()
        val streakR = streakD.await()
        val programR = programD.await()

        meR.onSuccess { name = it.optString("name") }
        moodsR.onSuccess { moods = it }
        streakR.onSuccess { streak = it.optInt("current") }
        programR.onSuccess { activeProgram = it }
        resumableD.await().onSuccess { resumable = it.optBoolean("resumable") }

        failed = meR.isFailure && moodsR.isFailure && streakR.isFailure
    }

    /** Cleared on sign-out so a NEW account never briefly renders the PREVIOUS one's
     *  cached name/streak while its own warm() is in flight. */
    fun clear() {
        name = null; moods = null; streak = null; activeProgram = null; resumable = false; failed = false
    }

    /** Called right after a successful check-in so every OTHER reader on Home — the
     *  presence orb's "done" state — updates immediately instead of waiting for the next
     *  [warm]. A synthetic local entry; the next real [warm] overwrites it with the
     *  server's own record, so this is purely a same-session optimistic update. */
    fun markCheckedInToday() {
        val today = JSONObject().put("ts", OffsetDateTime.now().toString())
        val merged = JSONArray()
        moods?.let { existing -> for (i in 0 until existing.length()) merged.put(existing.get(i)) }
        merged.put(today)
        moods = merged
    }
}
