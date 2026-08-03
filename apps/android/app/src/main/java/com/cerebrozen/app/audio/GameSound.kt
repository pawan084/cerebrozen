package com.cerebrozen.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The games' voice.
 *
 * They had none: every game in every category played the same nothing, and the
 * one that did make a sound used `ToneGenerator`'s DTMF tones — the noise a
 * telephone keypad makes, in an app whose whole argument is that it is calm.
 *
 * So this is a small synthesizer rather than a sound-effect pack. Three reasons
 * it is synthesis and not `.wav` files:
 *
 * * the app ships no audio assets today, and adding a dozen for micro-games
 *   would be the largest thing in the APK by some distance;
 * * a tone can be *pitched* per game, which is what makes each game feel like
 *   itself — Focus games answer high and short, Calm games low and long;
 * * correctness and error can be the same instrument a tone apart, which reads
 *   as feedback rather than as reward and punishment.
 *
 * Volume is deliberately low and every call is `runCatching`-guarded: on a
 * device with no audio stack, or in a headless test, this must do nothing at
 * all rather than throw. Sound is comfort here, never load-bearing.
 *
 * NOT in the coverage gate — AudioTrack is framework media, like [Chime].
 */
object GameSound {
    private const val SAMPLE_RATE = 22_050
    private const val LEVEL = 0.16f          // quieter than the chime; these repeat

    /** The per-game voice: a base pitch and a decay, in Hz and milliseconds.
     *
     * Pitch carries the category. Focus/Flexibility sit high and clipped so the
     * feedback lands inside the reaction window; Memory sits mid; Calm sits low
     * and rings on, because in a calm game the sound IS the pacing. */
    data class Voice(val hz: Double, val decayMs: Double, val durationMs: Int)

    private val VOICES = mapOf(
        "color-tap" to Voice(784.0, 70.0, 220),        // G5, bright and quick
        "stroop-flow" to Voice(880.0, 60.0, 200),      // A5, sharper still
        "freeze-switch" to Voice(988.0, 45.0, 160),    // B5, the shortest — a snap
        "pattern-recall" to Voice(587.0, 110.0, 300),  // D5, room to be remembered
        "object-tray" to Voice(659.0, 100.0, 280),     // E5
        "path-memory" to Voice(523.0, 120.0, 320),     // C5
        "thought-sort" to Voice(440.0, 160.0, 420),    // A4, unhurried
        "rule-switch" to Voice(698.0, 80.0, 240),      // F5
        "mirror-tap" to Voice(740.0, 70.0, 220),       // F#5
        "breathing-rhythm" to Voice(330.0, 320.0, 900), // E4, a long soft swell
        "zen-sand" to Voice(294.0, 280.0, 800),        // D4
        "still-point" to Voice(262.0, 400.0, 1100),    // C4, the longest
    )

    private val DEFAULT = Voice(660.0, 90.0, 260)

    /** Cached waveforms — a game replays its own tone every round, and
     * re-deriving 6,000 sin() calls per tap is pure churn. */
    private val cache = mutableMapOf<String, ShortArray>()

    fun voiceFor(gameId: String): Voice = VOICES[gameId] ?: DEFAULT

    /**
     * The interval a wrong answer is played at.
     *
     * A minor third *below* the game's own note, not a buzzer. The difference
     * between "not that one" and "you failed" is mostly the sound it makes, and
     * a harsh error tone in a wellness app teaches the user to stop playing.
     */
    internal fun errorHz(base: Double): Double = base * 0.8409  // 2^(-3/12)

    /** Correct: the game's own note. */
    fun correct(gameId: String) = play(gameId, voiceFor(gameId).hz)

    /** Wrong: the same instrument, a minor third down. */
    fun wrong(gameId: String) = play(gameId, errorHz(voiceFor(gameId).hz))

    /** A neutral tick — a sequence cell lighting, a breath phase turning.
     * An octave up and very short, so it reads as punctuation, not an answer. */
    fun tick(gameId: String) = play(gameId, voiceFor(gameId).hz * 2, durationScale = 0.35f)

    /** Session complete: the game's note and its fifth, rung together. */
    fun complete(gameId: String) {
        val hz = voiceFor(gameId).hz
        play(gameId, hz)
        play(gameId, hz * 1.5, durationScale = 1.4f)
    }

    private fun play(gameId: String, hz: Double, durationScale: Float = 1f) {
        runCatching {
            val voice = voiceFor(gameId)
            val key = "$gameId:${hz.toInt()}:$durationScale"
            val samples = cache.getOrPut(key) {
                synthesize(hz, voice.decayMs, (voice.durationMs * durationScale).toInt())
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) { runCatching { t?.release() } }
                    override fun onPeriodicNotification(t: AudioTrack?) {}
                },
            )
            track.play()
        }
    }

    /**
     * One decaying sine, with a short fade-in.
     *
     * The fade matters: a sine that starts at full amplitude on sample zero
     * clicks, and a click is exactly the sharp, startling sound this palette is
     * built to avoid.
     */
    internal fun synthesize(hz: Double, decayMs: Double, durationMs: Int): ShortArray {
        val count = SAMPLE_RATE * durationMs / 1000
        val attack = (SAMPLE_RATE * 0.006).toInt().coerceAtLeast(1)   // ~6ms
        return ShortArray(count) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            val envelope = exp(-(i * 1000.0 / SAMPLE_RATE) / decayMs)
            val fadeIn = if (i < attack) i / attack.toDouble() else 1.0
            (sin(2 * PI * hz * t) * envelope * fadeIn * LEVEL * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
