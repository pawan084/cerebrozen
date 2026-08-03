package com.cerebrozen.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A tiny offline water-drop synthesizer for Zen Ripples. */
class WaterDropSound {
    private val handler = Handler(Looper.getMainLooper())
    private val active = mutableSetOf<AudioTrack>()

    fun play(horizontalPosition: Float) {
        val sampleRate = 22_050
        val durationSeconds = 0.32f
        val frames = (sampleRate * durationSeconds).toInt()
        val pan = horizontalPosition.coerceIn(0f, 1f)
        val leftGain = (1f - pan * 0.62f)
        val rightGain = (0.38f + pan * 0.62f)
        val pcm = ShortArray(frames * 2)
        var phase = 0.0
        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate
            val progress = frame.toDouble() / frames
            // A quick downward pitch glide with two decays reads as a soft drop,
            // without shipping or streaming an audio asset.
            val frequency = 1_180.0 - 570.0 * progress
            phase += 2.0 * PI * frequency / sampleRate
            val envelope = exp(-12.0 * t) * (1.0 - exp(-95.0 * t))
            val body = sin(phase) * envelope + sin(phase * 0.51) * envelope * 0.22
            val sample = (body * 8_500.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[frame * 2] = (sample * leftGain).toInt().toShort()
            pcm[frame * 2 + 1] = (sample * rightGain).toInt().toShort()
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also { it.write(pcm, 0, pcm.size); it.play() }
        }.getOrNull() ?: return
        active += track
        handler.postDelayed({ active.remove(track); runCatching { track.stop() }; track.release() }, 450L)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        active.forEach { track -> runCatching { track.stop() }; track.release() }
        active.clear()
    }
}
