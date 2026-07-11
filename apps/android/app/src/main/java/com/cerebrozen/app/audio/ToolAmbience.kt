package com.cerebrozen.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A soft, fading ambient bed for the calming tool & game screens — the Android
 * mirror of the iOS `ToolAmbience`. One quiet looping track fades in when a tool
 * appears and out when it leaves, with a mute toggle the user controls. Kept low
 * (a bed, not a soundtrack) and Compose-observable. Player creation is guarded so
 * a headless test environment never crashes on it.
 */
object ToolAmbience {
    var playing by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set

    private const val BED_VOLUME = 0.22f
    private var player: ExoPlayer? = null
    private var currentRes = 0
    private val handler = Handler(Looper.getMainLooper())

    /** Begin (or switch to) a soft looping bed from a raw resource, fading in. */
    fun start(context: Context, rawRes: Int) {
        if (player != null && currentRes == rawRes) return   // already on this bed
        release()
        currentRes = rawRes
        player = runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$rawRes"))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
        playing = player != null
        fadeTo(if (muted) 0f else BED_VOLUME)
    }

    /** Fade out and tear down — call when the tool screen leaves. */
    fun stop() {
        val p = player ?: run { playing = false; return }
        rampVolume(from = p.volume, to = 0f) { release() }
    }

    fun toggleMute() {
        muted = !muted
        fadeTo(if (muted) 0f else BED_VOLUME)
    }

    private fun fadeTo(target: Float) {
        val p = player ?: return
        rampVolume(from = p.volume, to = target, onDone = null)
    }

    private fun rampVolume(from: Float, to: Float, onDone: (() -> Unit)?) {
        handler.removeCallbacksAndMessages(null)
        val steps = 20
        val stepMs = 40L
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val v = from + (to - from) * (step.toFloat() / steps)
                player?.volume = v.coerceIn(0f, 1f)
                if (step < steps) handler.postDelayed(this, stepMs) else onDone?.invoke()
            }
        }
        handler.postDelayed(runnable, stepMs)
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        currentRes = 0
        playing = false
    }
}
