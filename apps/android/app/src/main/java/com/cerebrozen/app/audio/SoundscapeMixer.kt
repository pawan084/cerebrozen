package com.cerebrozen.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cerebrozen.app.R

/**
 * A layered ambient mixer — parity with the iOS AVAudioEngine SoundscapePlayer.
 *
 * Four seamless loops (rain, ocean, wind, drone) each play on their own looping
 * [ExoPlayer] at an independent volume, summed by the system audio mixer, so the
 * user can blend their own calm (e.g. rain + a soft drone). A master volume scales
 * every layer; a sleep timer fades the whole mix out over its last seconds then
 * stops. ExoPlayer's REPEAT_MODE_ONE loops each track gaplessly (unlike
 * MediaPlayer.isLooping, which ticks at the seam).
 *
 * All state is Compose-observable. ExoPlayer must be touched from the main thread;
 * every entry point here is called from composition, which satisfies that.
 */
object SoundscapeMixer {
    /** One blendable ambient layer: display name, bundled loop, and a symbol key. */
    data class Layer(val name: String, val rawRes: Int, val symbol: String)

    val layers = listOf(
        Layer("Rain", R.raw.rain, "rain"),
        Layer("Ocean", R.raw.ocean, "ocean"),
        Layer("Wind", R.raw.wind, "wind"),
        Layer("Drone", R.raw.drone, "drone"),
    )

    var isPlaying by mutableStateOf(false)
        private set

    /** Master volume (0–1) scaling every layer — the iOS `volume` analogue. */
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

    private var players: List<ExoPlayer>? = null
    private val handler = Handler(Looper.getMainLooper())
    // Drops from 1 → 0 during the sleep-timer fade; multiplies through every layer.
    private var fade = 1f

    private fun effective(i: Int): Float = (volumes[i] * master * fade).coerceIn(0f, 1f)

    fun toggle(context: Context) { if (isPlaying) pause() else play(context) }

    fun play(context: Context) {
        val existing = players
        if (existing == null) {
            players = layers.map { layer ->
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${layer.rawRes}"))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                    playWhenReady = true
                }
            }
        } else {
            existing.forEach { it.playWhenReady = true }
        }
        applyVolumes()
        isPlaying = true
    }

    /** Pause but keep the players ready, so resuming is instant and click-free. */
    fun pause() {
        players?.forEach { it.playWhenReady = false }
        isPlaying = false
    }

    fun setLayerVolume(index: Int, v: Float) {
        if (index !in volumes.indices) return
        volumes[index] = v.coerceIn(0f, 1f)
        players?.getOrNull(index)?.volume = effective(index)
    }

    /** Tap a layer on/off (0 ↔ 0.7), mirroring iOS `toggleLayer`. */
    fun toggleLayer(index: Int) {
        if (index !in volumes.indices) return
        setLayerVolume(index, if (volumes[index] > 0.02f) 0f else 0.7f)
    }

    fun setMasterVolume(v: Float) {
        master = v.coerceIn(0f, 1f)
        applyVolumes()
    }

    private fun applyVolumes() {
        players?.forEachIndexed { i, p -> p.volume = effective(i) }
    }

    /** Off → 15 → 30 → 45 → 60 → off (same steps as the sleep player). */
    fun cycleTimer() {
        timerMinutes = when (timerMinutes) { 0 -> 15; 15 -> 30; 30 -> 45; 45 -> 60; else -> 0 }
        handler.removeCallbacksAndMessages(null)
        fade = 1f
        applyVolumes()
        if (timerMinutes > 0) startCountdown(timerMinutes * 60) else remaining = null
    }

    private const val FADE_LEAD = 12   // begin fading this many seconds before the end

    private fun startCountdown(totalSeconds: Int) {
        remaining = totalSeconds
        val tick = object : Runnable {
            override fun run() {
                val left = (remaining ?: return) - 1
                remaining = left.coerceAtLeast(0)
                if (left == FADE_LEAD) startFade(FADE_LEAD)
                if (left <= 0) { stop(); return }
                handler.postDelayed(this, 1_000)
            }
        }
        handler.postDelayed(tick, 1_000)
    }

    private fun startFade(seconds: Int) {
        val steps = 24
        val interval = seconds * 1_000L / steps
        var step = 0
        val fadeStep = object : Runnable {
            override fun run() {
                step++
                fade = ((steps - step).toFloat() / steps).coerceIn(0f, 1f)
                applyVolumes()
                if (step < steps) handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(fadeStep, interval)
    }

    /** Full teardown — releases the players and disarms the timer. */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        remaining = null
        timerMinutes = 0
        fade = 1f
        players?.forEach { it.release() }
        players = null
        isPlaying = false
    }

    /** m:ss label for the live countdown, or null when disarmed. */
    fun remainingText(): String? = remaining?.let { "%d:%02d".format(it / 60, it % 60) }
}
