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
 * The synthesis engine: renders a [SfxTones.Tone] to PCM and plays it. No audio
 * asset ships for any of these — the whole one-shot palette (breath cues, the
 * session-end bell, every Toolkit activity sound) is generated at runtime, which
 * is why it has zero latency and works before the user has ever reached the network.
 *
 * [Sfx] is the layer above: it prefers a real server asset when the catalogue has
 * one and calls in here otherwise. The tones themselves live in [SfxTones] as pure
 * data (and are unit-tested there); this file only knows how to make them audible.
 *
 * Everything is wrapped in runCatching: a missing audio stack (headless tests,
 * constrained devices) silently no-ops — a chime is comfort, never load-bearing.
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
        playSamples(synthesize(), DURATION_MS)
    }

    /** Render and play any [SfxTones.Tone]. The single entry point [Sfx] uses when
     * the server catalogue has no asset for a key. */
    fun playTone(tone: SfxTones.Tone) {
        playSamples(render(tone), tone.durationMs)
    }

    /** A soft rising cue for inhale and a longer falling cue for exhale. Routed
     * through [Sfx], so an admin can upload real recorded breath cues to
     * `breathe.inhale` / `breathe.exhale` and they replace the synthesized glide. */
    fun playBreathCue(inhale: Boolean) {
        Sfx.play(if (inhale) MediaCatalog.Keys.BREATHE_INHALE else MediaCatalog.Keys.BREATHE_EXHALE)
    }

    /** The hold phase.
     *
     * Behaviour-preserving: the hold has always rung this same soft chime, and it
     * still does. It only changes if an admin uploads a real `breathe.hold` cue,
     * which supersedes it — so there is a route to a better sound without silently
     * altering one users already know. (There is deliberately no *synthesized*
     * hold voice in [SfxTones]: inventing one would be exactly that silent change.) */
    fun playHoldCue() {
        if (Sfx.hasAsset(MediaCatalog.Keys.BREATHE_HOLD)) Sfx.play(MediaCatalog.Keys.BREATHE_HOLD)
        else play()
    }

    private fun playSamples(samples: ShortArray, durationMs: Int) {
        runCatching {
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
            handler.postDelayed({ runCatching { track.release() } }, durationMs + 200L)
        }
    }

    /** The session-end bell: plays only when the user hasn't switched it off.
     * Routed through [Sfx], so an admin-uploaded bell replaces the synthesized one. */
    fun playTimerBell() {
        if (timerBellEnabled) Sfx.play(MediaCatalog.Keys.CHIME_TIMER_BELL)
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

    /** Render a tone to PCM.
     *
     * DECAY is struck and rings out (a bell, a pad). GLIDE sweeps pitch under a
     * rounded swell, with the phase integrated continuously — stepping between
     * fixed frequencies instead would click audibly at every boundary.
     *
     * `harmonic` mixes in a quiet second partial — a bare sine reads as thin and
     * synthetic. It is per-tone rather than a constant so the shipped session-end
     * bell can keep its original pure sine (harmonic 0.0) while new tones get body.
     */
    private fun render(tone: SfxTones.Tone): ShortArray {
        val n = SAMPLE_RATE * tone.durationMs / 1_000
        val durationSeconds = tone.durationMs / 1_000.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val progress = (t / durationSeconds).coerceIn(0.0, 1.0)
            val phase: Double
            val envelope: Double
            when (tone.shape) {
                SfxTones.Shape.DECAY -> {
                    phase = 2.0 * PI * tone.startHz * t
                    envelope = exp(-(t * 1_000.0) / tone.tauMs)
                }
                SfxTones.Shape.GLIDE -> {
                    phase = 2.0 * PI * (
                        tone.startHz * t +
                            0.5 * (tone.endHz - tone.startHz) * t * t / durationSeconds
                    )
                    envelope = sin(PI * progress).coerceAtLeast(0.0)
                }
            }
            val wave = sin(phase) + sin(phase * 2.0) * tone.harmonic
            val sample = wave * envelope * tone.level
            (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
