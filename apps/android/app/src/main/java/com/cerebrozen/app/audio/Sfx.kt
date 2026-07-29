package com.cerebrozen.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.cerebrozen.app.net.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * One-shot sounds: taps, breath cues, the session-end bell.
 *
 * Two sources, in order:
 *  1. A server asset, when [MediaCatalog] has a URL for the key. Assets are
 *     downloaded once at launch and played from local disk through [SoundPool] —
 *     *never* streamed. A Pattern Glow pad that answers 200ms after the tap feels
 *     broken, so a sound that isn't already on disk simply isn't used yet.
 *  2. Otherwise the synthesized tone from [SfxTones], rendered by [Chime].
 *
 * (2) is the shipping default and is genuinely the sound of the app — (1) exists so
 * an admin can upload a recorded bell or a real water drop and have every user hear
 * it on their next launch, with no app release.
 *
 * Loops (ambient beds, scene videos) are not handled here — they belong to
 * [SoundscapeService] / [AmbientService] / SceneVideo, which stream by design.
 *
 * Every framework call is wrapped: a device with no audio stack, a failed download,
 * a half-loaded pool — all silently fall through to synthesis or to silence. Sound
 * is comfort here, never load-bearing.
 */
object Sfx {
    /** Persisted toggle for activity sounds (games, taps). Default ON — a silent
     * game reads as broken — but it stays a user choice, like every other sound
     * in the app. Breath cues keep their own separate opt-in ([Chime]). */
    private const val ENABLED_KEY = "activity_sounds"

    var enabled: Boolean
        get() = runCatching { Session.prefGet(ENABLED_KEY) }.getOrNull() != "false"
        set(value) { runCatching { Session.prefPut(ENABLED_KEY, value.toString()) } }

    private const val MAX_STREAMS = 4

    private var pool: SoundPool? = null

    /** key → SoundPool sound id, present only once the asset is on disk AND the
     * pool reports it decoded. Playing an id before that yields silence, so the
     * absence of an entry is what routes the key back to synthesis.
     *
     * Concurrent because the writers and readers are on different threads: `warm`
     * loads from IO, SoundPool's load-complete callback fires on the main thread,
     * and `play` reads from whichever thread a tap or a service lands on. */
    private val ready = ConcurrentHashMap<String, Int>()

    /**
     * Download every one-shot catalogue asset and load it into the pool. Call once
     * per launch, after [MediaCatalog] has parsed a catalogue response.
     *
     * Idempotent and cheap on a warm cache: an asset already on disk is not
     * re-fetched. Safe to fail — anything that doesn't land stays on synthesis.
     */
    suspend fun warm(context: Context) = withContext(Dispatchers.IO) {
        val pool = ensurePool() ?: return@withContext
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }

        // One-shots only. A looping bed streamed by a service must not be pulled
        // into a SoundPool — it is decoded fully into memory, and a 45-minute
        // soundscape would be tens of megabytes of heap.
        // containsKey explicitly: `in` on a ConcurrentHashMap resolves to
        // containsValue, which would compare a key against the sound ids.
        val keys = ONE_SHOT_KEYS.filter { MediaCatalog.has(it) && !ready.containsKey(it) }
        for (key in keys) {
            val url = MediaCatalog.urlFor(key)
            val file = File(dir, key)   // keys are dotted slugs — safe as filenames
            val ok = file.exists() && file.length() > 0 || download(url, file)
            if (!ok) continue
            runCatching {
                val id = pool.load(file.absolutePath, 1)
                if (id != 0) pendingLoads[id] = key
            }
        }
    }

    /**
     * Play [key] — a server asset if one is loaded, else its synthesized tone.
     *
     * Deliberately carries no policy: each sound has its own user toggle (the
     * session-end bell, the breathe cue, activity sounds) and they must stay
     * independent. Muting the games must not also silence the sleep-timer bell.
     * Callers gate; this only plays. Use [playActivity] for game/tap sounds.
     */
    fun play(key: String) {
        val id = ready[key]
        if (id != null) {
            val played = runCatching { pool?.play(id, 1f, 1f, 1, 0, 1f) }.getOrNull()
            // play() returns 0 when the stream couldn't be allocated; fall through
            // to synthesis rather than swallowing the cue.
            if (played != null && played != 0) return
        }
        SfxTones.specFor(key)?.let { Chime.playTone(it) }
    }

    /** A Toolkit activity sound, gated on the user's [enabled] preference. */
    fun playActivity(key: String) {
        if (enabled) play(key)
    }

    /** Whether the server has an asset for this key — for callers whose default is
     * an existing shipped sound rather than a synthesized tone, and who must only
     * deviate from it when a real upload exists (see [Chime.playHoldCue]). */
    fun hasAsset(key: String): Boolean = MediaCatalog.has(key)

    /** Pattern Glow's pads, by index. */
    fun playPad(index: Int) = playActivity(MediaCatalog.Keys.gamePad(index))

    /**
     * A Zen Ripples drop, pitched by [brightness] (0 = low/bottom of the pool,
     * 1 = high/top). Twelve identical plinks would grate; varying the pitch makes
     * a run of taps play as a phrase.
     *
     * A downloaded asset is repitched by SoundPool's rate parameter; the
     * synthesized fallback is re-rendered at the shifted frequency. Both land in
     * the same ±a-fifth range, so the game sounds like itself either way.
     */
    fun playRipple(brightness: Float) {
        if (!enabled) return
        val rate = RIPPLE_RATE_RANGE.lerp(brightness.coerceIn(0f, 1f))
        val id = ready[MediaCatalog.Keys.GAME_RIPPLE]
        if (id != null) {
            val played = runCatching { pool?.play(id, 1f, 1f, 1, 0, rate) }.getOrNull()
            if (played != null && played != 0) return
        }
        SfxTones.specFor(MediaCatalog.Keys.GAME_RIPPLE)?.let { base ->
            Chime.playTone(base.copy(startHz = base.startHz * rate, endHz = base.endHz * rate))
        }
    }

    /** SoundPool clamps playback rate to 0.5–2.0; this stays well inside that, and
     * inside the range where a drop still reads as a drop rather than a chirp. */
    private val RIPPLE_RATE_RANGE = 0.75f..1.5f

    private fun ClosedFloatingPointRange<Float>.lerp(t: Float) =
        start + (endInclusive - start) * t

    /** Drop cached state (sign-out, tests). Files under cacheDir are left for the
     * OS to reclaim — they're public, non-personal ambience. */
    fun release() {
        runCatching { pool?.release() }
        pool = null
        ready.clear()
        pendingLoads.clear()
    }

    // ── internals ─────────────────────────────────────────────────────────────
    /** sound id → key, for loads still decoding. Same cross-thread story as [ready]. */
    private val pendingLoads = ConcurrentHashMap<Int, String>()

    private val ONE_SHOT_KEYS: List<String> = buildList {
        repeat(4) { add(MediaCatalog.Keys.gamePad(it)) }
        add(MediaCatalog.Keys.GAME_PATTERN_SUCCESS)
        add(MediaCatalog.Keys.GAME_PATTERN_RESET)
        add(MediaCatalog.Keys.GAME_RIPPLE)
        add(MediaCatalog.Keys.GAME_BLOOM)
        add(MediaCatalog.Keys.CHIME_TIMER_BELL)
        add(MediaCatalog.Keys.BREATHE_INHALE)
        add(MediaCatalog.Keys.BREATHE_HOLD)
        add(MediaCatalog.Keys.BREATHE_EXHALE)
    }

    private fun ensurePool(): SoundPool? {
        pool?.let { return it }
        return runCatching {
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
                .also { p ->
                    // A key only becomes playable once the pool says it decoded —
                    // that's the gate that keeps `play` from firing a silent id.
                    p.setOnLoadCompleteListener { _, sampleId, status ->
                        val key = pendingLoads.remove(sampleId)
                        if (status == 0 && key != null) ready[key] = sampleId
                    }
                    pool = p
                }
        }.getOrNull()
    }

    private fun download(url: String, into: File): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) return false
            // Write via a temp file and rename, so a download killed mid-flight
            // can't leave a truncated asset that we'd happily hand to SoundPool.
            val tmp = File(into.parentFile, "${into.name}.part")
            conn.inputStream.use { input -> tmp.outputStream().use(input::copyTo) }
            tmp.renameTo(into)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
