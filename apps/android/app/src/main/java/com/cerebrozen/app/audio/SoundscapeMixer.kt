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
    /** One blendable ambient layer: display name, bundled loop, symbol, and the
     * catalogue key whose server asset supersedes the bundled loop when uploaded. */
    data class Layer(val name: String, val rawRes: Int, val symbol: String, val key: String)

    val layers = listOf(
        Layer("Rain", R.raw.rain, "rain", MediaCatalog.Keys.AMBIENCE_RAIN),
        Layer("Ocean", R.raw.ocean, "ocean", MediaCatalog.Keys.AMBIENCE_OCEAN),
        Layer("Wind", R.raw.wind, "wind", MediaCatalog.Keys.AMBIENCE_WIND),
        Layer("Drone", R.raw.drone, "drone", MediaCatalog.Keys.AMBIENCE_DRONE),
    )

    /** W27 §3 (Calm study): a named one-tap volume vector over the four layers.
     * The [key] is a stable id the UI maps to a localized label; the vector is
     * parallel to [layers] (rain, ocean, wind, drone). Sliders stay the power
     * path — a preset is just a starting blend. */
    data class Preset(val key: String, val volumes: List<Float>)

    val presets = listOf(
        Preset("monsoon_night", listOf(0.8f, 0f, 0.35f, 0.2f)),
        Preset("shoreline", listOf(0f, 0.8f, 0.3f, 0f)),
        Preset("still_air", listOf(0f, 0f, 0.25f, 0.5f)),
        // More named blends over the SAME four real loops — distinct experiences, no new
        // audio. Volumes are [rain, ocean, wind, drone], parallel to `layers`.
        Preset("rainforest", listOf(0.65f, 0.15f, 0.4f, 0f)),
        Preset("deep_current", listOf(0f, 0.6f, 0f, 0.5f)),
        Preset("thunderhead", listOf(0.85f, 0f, 0.55f, 0.35f)),
    )

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
        if (isPlaying) {
            context.startService(
                intent(context, SoundscapeService.ACTION_LAYER)
                    .putExtra(SoundscapeService.EXTRA_INDEX, index)
                    .putExtra(SoundscapeService.EXTRA_VOLUME, volumes[index]),
            )
        }
    }

    /** Tap a layer on/off (0 ↔ 0.7), mirroring iOS `toggleLayer`. */
    fun toggleLayer(context: Context, index: Int) {
        if (index !in volumes.indices) return
        setLayerVolume(context, index, if (volumes[index] > 0.02f) 0f else 0.7f)
    }

    fun setMasterVolume(context: Context, v: Float) {
        master = v.coerceIn(0f, 1f)
        if (isPlaying) {
            context.startService(
                intent(context, SoundscapeService.ACTION_MASTER)
                    .putExtra(SoundscapeService.EXTRA_VOLUME, master),
            )
        }
    }

    /** Off → 15 → 30 → 45 → 60 → off (same steps as the sleep player). */
    fun cycleTimer(context: Context) {
        val next = when (timerMinutes) { 0 -> 15; 15 -> 30; 30 -> 45; 45 -> 60; else -> 0 }
        context.startService(
            intent(context, SoundscapeService.ACTION_TIMER)
                .putExtra(SoundscapeService.EXTRA_MINUTES, next),
        )
        timerMinutes = next   // optimistic; the service confirms via publishTimer
    }

    /** m:ss label for the live countdown, or null when disarmed. */
    fun remainingText(): String? = remaining?.let { "%d:%02d".format(it / 60, it % 60) }

    private fun intent(context: Context, action: String): Intent =
        Intent(context, SoundscapeService::class.java).setAction(action)
}
