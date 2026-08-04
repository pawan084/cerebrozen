package com.cerebrozen.app.audio

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cerebrozen.app.R

/**
 * Controller + Compose-observable state for the layered soundscape. The actual
 * playback lives in [SoundscapeService] (a foreground service, so the mix keeps
 * going with the screen locked / overnight); this object sends it intents and
 * mirrors the state the service publishes back. Layer/master volumes update
 * optimistically here so the sliders stay smooth.
 *
 * Exclusivity: starting one engine stops the other (REDESIGN §3.4) — [play]
 * stops a playing [Player] first, and the player's play does the same to this
 * mixer. [stop] never counter-calls the other engine, so the pair can't loop.
 */
object SoundscapeMixer {
    /** One blendable ambient layer. [symbol] is the stable id (icon lookup, and
     * the same key the presets are documented against); [nameRes] is the display
     * label, resolved in composition so it localizes — same treatment the
     * presets one block below already get. [key] is the media-catalogue key whose
     * uploaded server asset supersedes the bundled loop. */
    data class Layer(
        @androidx.annotation.StringRes val nameRes: Int,
        val rawRes: Int,
        val symbol: String,
        val key: String,
    )

    val layers = listOf(
        Layer(R.string.mixer_layer_rain, R.raw.rain, "rain", MediaCatalog.Keys.AMBIENCE_RAIN),
        Layer(R.string.mixer_layer_ocean, R.raw.ocean, "ocean", MediaCatalog.Keys.AMBIENCE_OCEAN),
        Layer(R.string.mixer_layer_wind, R.raw.wind, "wind", MediaCatalog.Keys.AMBIENCE_WIND),
        Layer(R.string.mixer_layer_drone, R.raw.drone, "drone", MediaCatalog.Keys.AMBIENCE_DRONE),
    )

    /** W27 §3 (Calm study): a named one-tap volume vector over the four layers.
     * The [key] is a stable id the UI maps to a localized label; the vector is
     * parallel to [layers] (rain, ocean, wind, drone). Sliders stay the power
     * path — a preset is just a starting blend. */
    data class Preset(val key: String, val volumes: List<Float>)

    val presets = listOf(
        // First and matching the factory blend, so a first visit reads
        // "Just rain" instead of the puzzling "Custom mix".
        Preset("just_rain", listOf(0.7f, 0f, 0f, 0f)),
        Preset("monsoon_night", listOf(0.8f, 0f, 0.35f, 0.2f)),
        Preset("shoreline", listOf(0f, 0.8f, 0.3f, 0f)),
        Preset("still_air", listOf(0f, 0f, 0.25f, 0.5f)),
    )

    /** The loudest audible layer's display name, for naming a non-preset blend
     * ("Mostly rain" beats "Custom mix"). Null when everything is silent. */
    @androidx.annotation.StringRes
    fun dominantLayerRes(): Int? = volumes.withIndex()
        .filter { it.value > 0.02f }
        .maxByOrNull { it.value }
        ?.let { layers[it.index].nameRes }

    /** Apply a preset's blend through the existing per-layer path (so a live
     * service hears each change); out-of-range indices are a no-op. */
    fun applyPreset(context: Context, index: Int) {
        val preset = presets.getOrNull(index) ?: return
        preset.volumes.forEachIndexed { i, v -> setLayerVolume(context, i, v) }
    }

    /** The preset the current volumes match (within a slider-noise epsilon),
     * or null — drives the chips' selected state, so nudging any slider
     * honestly deselects the preset. */
    fun matchingPreset(): Int? = presets.indexOfFirst { preset ->
        preset.volumes.withIndex().all { (i, v) -> kotlin.math.abs(volumes[i] - v) < 0.01f }
    }.takeIf { it >= 0 }

    var isPlaying by mutableStateOf(false)
        private set

    /** Master volume (0–1) scaling every layer. */
    var master by mutableStateOf(0.7f)
        private set

    /** Per-layer volumes (0–1); starts with just rain, like iOS's primary layer. */
    val volumes = mutableStateListOf(0.7f, 0f, 0f, 0f)

    /** The last audible volume per layer, so a toggle restores YOUR level —
     * muting rain at 0.9 and re-enabling used to snap it to a fixed 0.7. */
    private val lastNonZero = floatArrayOf(0.7f, 0.7f, 0.7f, 0.7f)

    // ── Persistence: the mix a user tunes for a week of nights must survive
    // process death. One small JSON in the session store; loaded lazily the
    // first time anything touches the mixer, saved on every change.
    private const val STATE_KEY = "mixer_state"
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val raw = com.cerebrozen.app.net.Session.prefGet(STATE_KEY) ?: return
            val o = org.json.JSONObject(raw)
            master = o.optDouble("master", master.toDouble()).toFloat().coerceIn(0f, 1f)
            o.optJSONArray("volumes")?.let { arr ->
                for (i in volumes.indices) if (i < arr.length()) {
                    volumes[i] = arr.optDouble(i, volumes[i].toDouble()).toFloat().coerceIn(0f, 1f)
                }
            }
            o.optJSONArray("last")?.let { arr ->
                for (i in lastNonZero.indices) if (i < arr.length()) {
                    lastNonZero[i] = arr.optDouble(i, lastNonZero[i].toDouble()).toFloat().coerceIn(0.05f, 1f)
                }
            }
        }
    }

    private fun persist() {
        runCatching {
            com.cerebrozen.app.net.Session.prefPut(
                STATE_KEY,
                org.json.JSONObject()
                    .put("master", master.toDouble())
                    .put("volumes", org.json.JSONArray().apply { volumes.forEach { put(it.toDouble()) } })
                    .put("last", org.json.JSONArray().apply { lastNonZero.forEach { put(it.toDouble()) } })
                    .toString(),
            )
        }
    }

    /** Armed sleep-timer duration in minutes (0 = off). */
    var timerMinutes by mutableStateOf(0)
        private set

    /** Seconds left before the fade-out, or null when disarmed. */
    var remaining by mutableStateOf<Int?>(null)
        private set

    // ── Published by the service ──────────────────────────────────────────────
    fun publishPlaying(playing: Boolean) { isPlaying = playing }
    fun publishTimer(minutes: Int, secondsLeft: Int?) {
        timerMinutes = minutes
        remaining = secondsLeft
    }

    // ── Commands (sent to the foreground service) ─────────────────────────────
    fun toggle(context: Context) { if (isPlaying) pause(context) else play(context) }

    fun play(context: Context) {
        ensureLoaded()
        // Exactly one audio engine at a time (REDESIGN §3.4): a playing ambient
        // bed yields to the mixer. Its stop() has no counter-call — no loop.
        if (Player.isPlaying) Player.stop(context)
        context.startForegroundService(
            intent(context, SoundscapeService.ACTION_PLAY)
                .putExtra(SoundscapeService.EXTRA_VOLUMES, volumes.toFloatArray())
                .putExtra(SoundscapeService.EXTRA_MASTER, master),
        )
        isPlaying = true   // optimistic; the service confirms via publishPlaying
    }

    fun pause(context: Context) {
        context.startService(intent(context, SoundscapeService.ACTION_PAUSE))
        isPlaying = false
    }

    fun stop(context: Context) {
        context.startService(intent(context, SoundscapeService.ACTION_STOP))
        isPlaying = false
        timerMinutes = 0
        remaining = null
    }

    fun setLayerVolume(context: Context, index: Int, v: Float) {
        if (index !in volumes.indices) return
        volumes[index] = v.coerceIn(0f, 1f)
        if (volumes[index] > 0.02f) lastNonZero[index] = volumes[index]
        persist()
        if (isPlaying) {
            context.startService(
                intent(context, SoundscapeService.ACTION_LAYER)
                    .putExtra(SoundscapeService.EXTRA_INDEX, index)
                    .putExtra(SoundscapeService.EXTRA_VOLUME, volumes[index]),
            )
        }
    }

    /** Tap a layer off and back to the level YOU had it at (0 ↔ last audible),
     * not a fixed 0.7. */
    fun toggleLayer(context: Context, index: Int) {
        if (index !in volumes.indices) return
        setLayerVolume(context, index, if (volumes[index] > 0.02f) 0f else lastNonZero[index])
    }

    fun setMasterVolume(context: Context, v: Float) {
        master = v.coerceIn(0f, 1f)
        persist()
        if (isPlaying) {
            context.startService(
                intent(context, SoundscapeService.ACTION_MASTER)
                    .putExtra(SoundscapeService.EXTRA_VOLUME, master),
            )
        }
    }

    /** Duck the mix under the companion's voice (Talk TTS) — [Player] has had
     * this from the start; the mixer kept full volume while CereBro spoke. */
    fun duck(context: Context, ducked: Boolean) {
        if (!isPlaying) return
        context.startService(
            intent(context, SoundscapeService.ACTION_DUCK)
                .putExtra(SoundscapeService.EXTRA_DUCKED, ducked),
        )
    }

    /** Off → 15 → 30 → 45 → 60 → off (same steps as the sleep player). */
    /** The selectable timer stops, in cycle order — the ONE list the pill
     * rail and the cycle step both read (audit B33). */
    val TIMER_CYCLE = listOf(0, 15, 30, 45, 60)

    /** Arm the timer at exactly [minutes] with one service intent — the card
     * used to reach a target by firing up to four blind cycle intents, each
     * resetting the service's fade state. */
    fun setTimer(context: Context, minutes: Int) {
        context.startService(
            intent(context, SoundscapeService.ACTION_TIMER)
                .putExtra(SoundscapeService.EXTRA_MINUTES, minutes),
        )
        timerMinutes = minutes   // optimistic; the service confirms via publishTimer
    }

    fun cycleTimer(context: Context) {
        val at = TIMER_CYCLE.indexOf(timerMinutes).coerceAtLeast(0)
        setTimer(context, TIMER_CYCLE[(at + 1) % TIMER_CYCLE.size])
    }

    /** m:ss label for the live countdown, or null when disarmed. */
    fun remainingText(): String? = remaining?.let { "%d:%02d".format(it / 60, it % 60) }

    private fun intent(context: Context, action: String): Intent =
        Intent(context, SoundscapeService::class.java).setAction(action)
}
