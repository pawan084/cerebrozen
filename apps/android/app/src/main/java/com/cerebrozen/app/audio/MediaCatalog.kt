package com.cerebrozen.app.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * The server media catalogue, resolved by key.
 *
 * Every sound and video the app can play has a stable dotted key ([Keys]) that the
 * backend also knows (`app/seed.py` `_MEDIA` — a hand-duplicated cross-stack
 * contract, see docs/ARCHITECTURE.md). GET /media/catalog returns one row per key
 * with a URL that may be empty.
 *
 * **An empty URL is the normal, expected state, not a failure.** It means "the
 * server has no bytes for this key yet" and the caller plays its bundled loop or
 * synthesized tone instead — the same contract `content_items.audio_url` already
 * uses. That is what lets an admin upload a real recording to `game.bloom` and have
 * every user hear it on next launch, with no app release, while the app stays fully
 * audible before that ever happens.
 *
 * Plain object with no Android/Compose types, so it is JVM unit-testable.
 */
object MediaCatalog {
    /** The key contract. Mirrored in backend `app/seed.py` `_MEDIA`. */
    object Keys {
        const val AMBIENCE_RAIN = "ambience.rain"
        const val AMBIENCE_OCEAN = "ambience.ocean"
        const val AMBIENCE_WIND = "ambience.wind"
        const val AMBIENCE_DRONE = "ambience.drone"
        const val AMBIENCE_BED = "ambience.bed"

        const val BREATHE_INHALE = "breathe.inhale"
        const val BREATHE_HOLD = "breathe.hold"
        const val BREATHE_EXHALE = "breathe.exhale"

        /** Pattern Glow's four pads, indexed 0–3. */
        fun gamePad(index: Int) = "game.pad.$index"
        const val GAME_PATTERN_SUCCESS = "game.pattern.success"
        const val GAME_PATTERN_RESET = "game.pattern.reset"
        const val GAME_RIPPLE = "game.ripple"
        const val GAME_BLOOM = "game.bloom"

        const val CHIME_TIMER_BELL = "chime.timer_bell"

        const val SCENE_NIGHT_LAKE = "scene.night_lake"
        const val SCENE_DAWN = "scene.dawn"
    }

    // Immutable snapshots swapped atomically, never mutated in place. The catalogue
    // is written once from a coroutine at launch and read from every audio thread
    // there is (the two foreground services, SoundPool callbacks, Compose) — a
    // shared mutable map would race, and a read during a clear() could throw.
    @Volatile private var urls: Map<String, String> = emptyMap()
    @Volatile private var looping: Set<String> = emptySet()

    /**
     * True once a catalogue response has been parsed (even an empty one), so the UI
     * can tell "not fetched yet" from "fetched, and the server has nothing".
     *
     * Compose-observable on purpose: the catalogue lands asynchronously, *after* the
     * first screens have already composed. A plain field would leave a scene video
     * (or any catalogue-dependent UI) missing until the user happened to navigate
     * and force a recomposition. Read it from a composable and the screen refreshes
     * the moment the catalogue arrives.
     */
    var loaded by mutableStateOf(false)
        private set

    /**
     * Parse a /media/catalog response. [base] is the API base URL used to resolve
     * relative "/media/..." paths; absolute URLs pass through untouched.
     *
     * Reuses [MediaUrls.resolve] — one place decides what a relative media path
     * means, so narration and catalogue assets can never disagree about it.
     *
     * Replaces the previous catalogue wholesale rather than merging: a key an admin
     * unpublished must stop resolving, not linger pointing at bytes that may already
     * be gone from disk.
     */
    fun load(json: JSONArray, base: String) {
        val nextUrls = mutableMapOf<String, String>()
        val nextLooping = mutableSetOf<String>()
        for (i in 0 until json.length()) {
            val row = json.optJSONObject(i) ?: continue
            val key = row.optString("key").takeIf { it.isNotBlank() } ?: continue
            val url = MediaUrls.resolve(row.optString("url"), base)
            if (url.isNotBlank()) nextUrls[key] = url
            if (row.optBoolean("loop")) nextLooping.add(key)
        }
        urls = nextUrls
        looping = nextLooping
        loaded = true
    }

    /** "" when the server has no asset for this key — play the bundled fallback. */
    fun urlFor(key: String): String = urls[key] ?: ""

    /** Whether the server says this asset loops (beds, scenes) or fires once. */
    fun isLooping(key: String): Boolean = key in looping

    /** True when the server has real bytes for this key. */
    fun has(key: String): Boolean = urls[key]?.isNotBlank() == true

    fun clear() {
        urls = emptyMap()
        looping = emptySet()
        loaded = false
    }
}
