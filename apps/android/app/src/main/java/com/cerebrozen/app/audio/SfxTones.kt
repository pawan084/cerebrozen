package com.cerebrozen.app.audio

/**
 * The synthesized fallback voice for every one-shot sound, as pure data.
 *
 * These are what the app plays when the server catalogue has no asset for a key —
 * which is the shipping default, so this table *is* the sound of the app, not a
 * degraded stand-in. [Chime] renders a [Tone]; [Sfx] decides between this and a
 * downloaded asset. Kept free of Android types so the whole table is unit-testable.
 *
 * Two deliberate choices, both because this is a mental-health app:
 *
 * 1. **The four Pattern Glow pads are a C major pentatonic** (C5–D5–E5–G5). A
 *    pentatonic set has no semitone clashes, so *every* sequence the game can
 *    generate — and every wrong tap a user can make — lands consonant. It is
 *    impossible to play something that sounds like a mistake.
 * 2. **Nothing here is a buzzer.** [Key.PATTERN_RESET] is a soft, low, slow settle,
 *    quieter than the pads it follows. Losing a round must never feel like being
 *    told off.
 */
object SfxTones {
    /** How a tone moves over its life. */
    enum class Shape {
        /** Struck and left to ring out — a bell, a pad. */
        DECAY,

        /** Glides between two pitches under a rounded swell — a breath, a bloom. */
        GLIDE,
    }

    /**
     * @param startHz the pitch (for [Shape.DECAY], the only pitch)
     * @param endHz   the pitch glided to; ignored for [Shape.DECAY]
     * @param durationMs how long the sample runs
     * @param level  peak amplitude 0–1. Everything here is quiet on purpose: these
     *   are cues inside a calm app, never sound effects competing for attention.
     * @param tauMs  exponential decay constant for [Shape.DECAY]
     * @param harmonic how much second harmonic to mix in. A bare sine reads as thin
     *   and synthetic, so the new tones carry a little. The session-end bell sets it
     *   to 0.0 deliberately: it is a *shipped* sound, and it was a pure sine — giving
     *   it a harmonic now would be a silent change to a sound users already know.
     */
    data class Tone(
        val startHz: Double,
        val endHz: Double = startHz,
        val durationMs: Int,
        val level: Float,
        val tauMs: Double = 110.0,
        val shape: Shape = Shape.DECAY,
        val harmonic: Double = 0.12,
    )

    // C major pentatonic — see the class note. Any subset sounds consonant.
    private val PAD_HZ = doubleArrayOf(523.25, 587.33, 659.25, 783.99)   // C5 D5 E5 G5

    /** The pad tone for Pattern Glow index 0–3; out-of-range indices wrap, so a
     * future five-pad board still makes a sound rather than falling silent. */
    fun pad(index: Int): Tone = Tone(
        startHz = PAD_HZ[Math.floorMod(index, PAD_HZ.size)],
        durationMs = 520,
        level = 0.16f,
        tauMs = 170.0,
    )

    /**
     * The tone for a catalogue key, or null when the key has no synthesized voice
     * (loops and scene videos — an ambient bed cannot be faked with a sine wave, so
     * those keys fall back to their bundled asset instead, not to synthesis).
     */
    fun specFor(key: String): Tone? = when (key) {
        MediaCatalog.Keys.GAME_PATTERN_SUCCESS -> Tone(
            // The round-complete lift: a rising fifth, warm and brief.
            startHz = 523.25, endHz = 783.99,
            durationMs = 620, level = 0.15f, shape = Shape.GLIDE,
        )
        MediaCatalog.Keys.GAME_PATTERN_RESET -> Tone(
            // Deliberately gentle: low, slow, and quieter than the pads. A missed
            // pad is a reset, not a failure — the sound must say so.
            startHz = 293.66, endHz = 220.0,   // D4 → A3
            durationMs = 760, level = 0.10f, shape = Shape.GLIDE,
        )
        MediaCatalog.Keys.GAME_RIPPLE -> Tone(
            // A water-drop plink: struck high, decays fast.
            startHz = 880.0, durationMs = 340, level = 0.13f, tauMs = 70.0,
        )
        MediaCatalog.Keys.GAME_BLOOM -> Tone(
            // Something opening: a soft rise, longer than a tap.
            startHz = 392.0, endHz = 659.25,   // G4 → E5
            durationMs = 900, level = 0.14f, shape = Shape.GLIDE,
        )
        MediaCatalog.Keys.CHIME_TIMER_BELL -> Tone(
            // The shipped session-end bell, bit-for-bit: 880Hz, 400ms, tau 110,
            // level 0.22 — and harmonic 0.0, because the original was a pure sine.
            startHz = 880.0, durationMs = 400, level = 0.22f, tauMs = 110.0,
            harmonic = 0.0,
        )
        // The shipped breath cues, unchanged — same pitches, durations, level and
        // harmonic as the glide they replaced.
        MediaCatalog.Keys.BREATHE_INHALE -> Tone(
            startHz = 360.0, endHz = 560.0,
            durationMs = 720, level = 0.11f, shape = Shape.GLIDE,
        )
        MediaCatalog.Keys.BREATHE_EXHALE -> Tone(
            startHz = 560.0, endHz = 330.0,
            durationMs = 920, level = 0.11f, shape = Shape.GLIDE,
        )
        else -> when {
            key.startsWith("game.pad.") ->
                key.removePrefix("game.pad.").toIntOrNull()?.let { pad(it) }
            else -> null
        }
    }
}
