package com.cerebrozen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The synthesized voice of the app — what actually plays today, since we ship no
 * one-shot assets. Two properties here are product decisions, not implementation
 * details, and are pinned deliberately:
 *
 *  - the Pattern Glow pads are a pentatonic set, so no tap can sound like a mistake
 *  - the reset tone is *softer and lower* than the pads, so a missed pad never scolds
 */
class SfxTonesTest {

    // ── every one-shot key has a voice ──────────────────────────────────
    @Test
    fun every_one_shot_key_has_a_synthesized_fallback() {
        val keys = listOf(
            MediaCatalog.Keys.GAME_PATTERN_SUCCESS,
            MediaCatalog.Keys.GAME_PATTERN_RESET,
            MediaCatalog.Keys.GAME_RIPPLE,
            MediaCatalog.Keys.GAME_BLOOM,
            MediaCatalog.Keys.CHIME_TIMER_BELL,
            MediaCatalog.Keys.BREATHE_INHALE,
            MediaCatalog.Keys.BREATHE_EXHALE,
        ) + (0..3).map { MediaCatalog.Keys.gamePad(it) }

        keys.forEach { key ->
            assertNotNull("$key has no synthesized fallback — it would be silent", SfxTones.specFor(key))
        }
    }

    // ── behaviour preservation: sounds that already shipped ─────────────
    @Test
    fun the_session_end_bell_is_the_pure_sine_that_shipped() {
        // 880Hz / 400ms / tau 110 / level 0.22, and — the easy one to lose —
        // harmonic 0.0, because the original synthesize() was a bare sine. Adding
        // a partial here would quietly change a sound users already know.
        val bell = SfxTones.specFor(MediaCatalog.Keys.CHIME_TIMER_BELL)!!

        assertEquals(880.0, bell.startHz, 0.001)
        assertEquals(880.0, bell.endHz, 0.001)
        assertEquals(400, bell.durationMs)
        assertEquals(110.0, bell.tauMs, 0.001)
        assertEquals(0.22f, bell.level, 0.0001f)
        assertEquals(0.0, bell.harmonic, 0.0001)
        assertEquals(SfxTones.Shape.DECAY, bell.shape)
    }

    @Test
    fun the_breath_cues_are_the_glide_that_shipped() {
        val inhale = SfxTones.specFor(MediaCatalog.Keys.BREATHE_INHALE)!!
        val exhale = SfxTones.specFor(MediaCatalog.Keys.BREATHE_EXHALE)!!

        // Same pitches, durations, level and harmonic as the old synthesizeBreathCue.
        assertEquals(360.0, inhale.startHz, 0.001)
        assertEquals(560.0, inhale.endHz, 0.001)
        assertEquals(720, inhale.durationMs)
        assertEquals(0.11f, inhale.level, 0.0001f)
        assertEquals(0.12, inhale.harmonic, 0.0001)

        assertEquals(560.0, exhale.startHz, 0.001)
        assertEquals(330.0, exhale.endHz, 0.001)
        assertEquals(920, exhale.durationMs)
        assertEquals(0.11f, exhale.level, 0.0001f)
    }

    @Test
    fun the_hold_phase_has_no_synthesized_voice_so_it_keeps_the_chime_it_shipped_with() {
        // Chime.playHoldCue falls back to the existing chime unless a real
        // `breathe.hold` asset is uploaded. Inventing a synthesized hold tone here
        // would silently change a shipped sound — so there deliberately isn't one.
        assertNull(SfxTones.specFor(MediaCatalog.Keys.BREATHE_HOLD))
    }

    @Test
    fun loops_and_scenes_have_no_synthesized_voice() {
        // A 45-minute ambient bed cannot be faked with a sine wave, and a video
        // certainly can't — those keys fall back to their bundled asset instead.
        assertNull(SfxTones.specFor(MediaCatalog.Keys.AMBIENCE_RAIN))
        assertNull(SfxTones.specFor(MediaCatalog.Keys.AMBIENCE_BED))
        assertNull(SfxTones.specFor(MediaCatalog.Keys.SCENE_NIGHT_LAKE))
        assertNull(SfxTones.specFor("totally.unknown.key"))
    }

    // ── the pentatonic guarantee ────────────────────────────────────────
    @Test
    fun the_four_pads_are_a_c_major_pentatonic_so_no_sequence_can_clash() {
        val hz = (0..3).map { SfxTones.pad(it).startHz }
        // C5, D5, E5, G5 — no semitone pair exists in this set, which is what makes
        // every possible sequence (and every wrong tap) land consonant.
        assertEquals(listOf(523.25, 587.33, 659.25, 783.99), hz)

        // Strictly ascending, so the board reads left-to-right as it sounds.
        assertEquals(hz.sorted(), hz)
    }

    @Test
    fun pad_indices_wrap_rather_than_crash_on_a_future_bigger_board() {
        assertEquals(SfxTones.pad(0), SfxTones.pad(4))
        assertEquals(SfxTones.pad(1), SfxTones.pad(5))
        // Negative indices must not throw either (floorMod, not %).
        assertEquals(SfxTones.pad(3), SfxTones.pad(-1))
    }

    // ── the "never scold" guarantee ─────────────────────────────────────
    @Test
    fun the_reset_tone_is_quieter_and_lower_than_the_pads_it_follows() {
        val reset = SfxTones.specFor(MediaCatalog.Keys.GAME_PATTERN_RESET)!!
        val quietestPad = (0..3).map { SfxTones.pad(it) }.minBy { it.level }
        val lowestPad = (0..3).map { SfxTones.pad(it) }.minBy { it.startHz }

        assertTrue(
            "reset (${reset.level}) must be quieter than the quietest pad (${quietestPad.level})",
            reset.level < quietestPad.level,
        )
        assertTrue(
            "reset (${reset.startHz}Hz) must sit below the lowest pad (${lowestPad.startHz}Hz)",
            reset.startHz < lowestPad.startHz,
        )
        // …and it settles downward, rather than rising like an alert.
        assertTrue("reset must fall, not rise", reset.endHz < reset.startHz)
    }

    @Test
    fun success_rises_and_reset_falls() {
        val success = SfxTones.specFor(MediaCatalog.Keys.GAME_PATTERN_SUCCESS)!!
        val reset = SfxTones.specFor(MediaCatalog.Keys.GAME_PATTERN_RESET)!!

        assertTrue(success.endHz > success.startHz)
        assertTrue(reset.endHz < reset.startHz)
    }

    // ── everything stays quiet ──────────────────────────────────────────
    @Test
    fun no_tone_is_loud_enough_to_startle() {
        val all = (0..3).map { SfxTones.pad(it) } + listOfNotNull(
            SfxTones.specFor(MediaCatalog.Keys.GAME_PATTERN_SUCCESS),
            SfxTones.specFor(MediaCatalog.Keys.GAME_PATTERN_RESET),
            SfxTones.specFor(MediaCatalog.Keys.GAME_RIPPLE),
            SfxTones.specFor(MediaCatalog.Keys.GAME_BLOOM),
            SfxTones.specFor(MediaCatalog.Keys.CHIME_TIMER_BELL),
            SfxTones.specFor(MediaCatalog.Keys.BREATHE_INHALE),
            SfxTones.specFor(MediaCatalog.Keys.BREATHE_EXHALE),
        )

        all.forEach { tone ->
            assertTrue("a cue at level ${tone.level} is a sound effect, not a cue", tone.level <= 0.25f)
            assertTrue("a tone must have a positive duration", tone.durationMs > 0)
        }
    }

    @Test
    fun breath_cues_glide_the_way_a_breath_moves() {
        val inhale = SfxTones.specFor(MediaCatalog.Keys.BREATHE_INHALE)!!
        val exhale = SfxTones.specFor(MediaCatalog.Keys.BREATHE_EXHALE)!!

        assertTrue("inhale rises", inhale.endHz > inhale.startHz)
        assertTrue("exhale falls", exhale.endHz < exhale.startHz)

        // An exhale is longer than an inhale — that's the physiology the cue paces.
        assertTrue(exhale.durationMs > inhale.durationMs)
    }
}
