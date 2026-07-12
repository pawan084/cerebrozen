package com.cerebrozen.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.cerebrozen.app.net.Session
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * W27 §4–5 (Calm study): the one soft chime — a runtime-synthesized ~880Hz sine
 * with an exponential decay (~400ms), so no audio asset ships. Used for the
 * optional breathe phase cue (OFF by default — users haven't consented to
 * surprise audio) and the session-end bell when a sleep timer completes
 * (ON by default, toggleable next to the sleep-timer controls).
 *
 * Everything is wrapped in runCatching: a missing audio stack (headless tests,
 * constrained devices) silently no-ops — the chime is comfort, never load-bearing.
 * NOT in the coverage gate: AudioTrack is framework media (see build.gradle.kts).
 */
object Chime {
    private const val SAMPLE_RATE = 22_050
    private const val DURATION_MS = 400
    private const val FREQ_HZ = 880.0
    private const val DECAY_TAU_MS = 110.0   // exponential decay constant
    private const val LEVEL = 0.22f          // low volume — a cue, not a sound effect

    /** Persisted toggles (Session's Store seam — defaults when unset). */
    private const val TIMER_BELL_KEY = "timer_bell"      // default ON
    private const val BREATHE_CHIME_KEY = "breathe_chime" // default OFF
    private const val BREATHE_HAPTICS_KEY = "breathe_haptics" // default ON

    var timerBellEnabled: Boolean
        get() = runCatching { Session.prefGet(TIMER_BELL_KEY) }.getOrNull() != "false"
        set(value) { runCatching { Session.prefPut(TIMER_BELL_KEY, value.toString()) } }

    var breatheChimeEnabled: Boolean
        get() = runCatching { Session.prefGet(BREATHE_CHIME_KEY) }.getOrNull() == "true"
        set(value) { runCatching { Session.prefPut(BREATHE_CHIME_KEY, value.toString()) } }

    var breatheHapticsEnabled: Boolean
        get() = runCatching { Session.prefGet(BREATHE_HAPTICS_KEY) }.getOrNull() != "false"
        set(value) { runCatching { Session.prefPut(BREATHE_HAPTICS_KEY, value.toString()) } }

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Play the soft chime once. Never throws; never blocks the caller. */
    fun play() {
        runCatching {
            val samples = synthesize()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            // Release after the tail has rung out; runCatching again — the app
            // (or the owning service) may already be tearing down by then.
            handler.postDelayed({ runCatching { track.release() } }, DURATION_MS + 200L)
        }
    }

    /** The session-end bell: plays only when the user hasn't switched it off. */
    fun playTimerBell() {
        if (timerBellEnabled) play()
    }

    /** 880Hz sine, exponentially decayed — a single soft "ting". */
    private fun synthesize(): ShortArray {
        val n = SAMPLE_RATE * DURATION_MS / 1000
        return ShortArray(n) { i ->
            val tMs = i * 1000.0 / SAMPLE_RATE
            val envelope = exp(-tMs / DECAY_TAU_MS)
            val s = sin(2.0 * PI * FREQ_HZ * i / SAMPLE_RATE) * envelope * LEVEL
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
