package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * W27 §4: the one breathing engine gains user-selectable pacing (Gentle 6s /
 * Classic 4s / Slow 8s per phase). The pace scales every phase's seconds
 * equally and changes nothing else — labels, hold structure and orb phases
 * stay exactly as the presets define them. The default stays the
 * long-standing 4s (ScreenLogicTest pins that contract).
 */
class BreathePacingTest {

    @Test
    fun pace_scales_every_phase_equally_for_box_and_color() {
        listOf(4, 6, 8).forEach { pace ->
            val phases = breathePhases(BreathePreset.Box, pace)
            assertEquals(List(4) { pace }, phases.map { it.seconds })
            assertEquals("pace never changes the guidance",
                listOf(BreathKind.IN, BreathKind.HOLD, BreathKind.OUT, BreathKind.HOLD),
                phases.map { it.kind })
            assertEquals(listOf(true, true, false, false), phases.map { it.expanded })
            assertEquals("Color shares Box pacing at every pace",
                phases, breathePhases(BreathePreset.Color, pace))
        }
    }

    @Test
    fun pace_scales_the_reset_rhythm_and_it_still_has_no_holds() {
        val phases = breathePhases(BreathePreset.Reset, 8)
        // The exhale stays RESET_EXHALE_EXTRA seconds longer than the inhale at
        // every pace — that asymmetry IS the exercise (and iOS's `.reset`), not
        // a rounding of it. Pinned so a future pace tweak can't quietly flatten
        // it back to a symmetric rhythm, which is what Android shipped until
        // 2026-07-29 (and what the other branch of this merge still asserted).
        assertEquals(listOf(8, 8 + RESET_EXHALE_EXTRA), phases.map { it.seconds })
        assertEquals(listOf(BreathKind.IN, BreathKind.OUT), phases.map { it.kind })
    }

    @Test
    fun reset_matches_the_ios_preset_at_the_default_pace() {
        // iOS: BreathingPacer.Preset.reset — ("Breathe in", 4), ("Breathe out", 6).
        assertEquals(listOf(4, 6), breathePhases(BreathePreset.Reset).map { it.seconds })
    }

    @Test
    fun the_default_pace_is_the_classic_four() {
        assertEquals(breathePhases(BreathePreset.Box, 4), breathePhases(BreathePreset.Box))
        assertEquals(breathePhases(BreathePreset.Reset, 4), breathePhases(BreathePreset.Reset))
    }

    // ── The two minutes the app promises on five surfaces ──
    @Test
    fun theTwoMinuteMarkIsRealAndArrivesWhenItShould() {
        // "Two-minute reset" / "Try a 2-minute reset" / "Fast anxiety-stress
        // reset — 2 minutes" all point at this preset, and nothing measured it:
        // Reset is an open-ended in/out cycle.
        //
        // A Reset breath is NOT two equal phases: the exhale runs
        // RESET_EXHALE_EXTRA seconds longer than the inhale (in 4, out 6 — the
        // asymmetry is the exercise). So at the classic pace a breath is 10s and
        // two minutes is twelve of them, not fifteen.
        assertEquals(4 + 4 + RESET_EXHALE_EXTRA, breatheElapsedSeconds(BreathePreset.Reset, 4, 1))
        assertEquals(120, breatheElapsedSeconds(BreathePreset.Reset, 4, 12))
        assertFalse(twoMinutesReached(BreathePreset.Reset, 4, 11))
        assertTrue(twoMinutesReached(BreathePreset.Reset, 4, 12))
    }

    @Test
    fun aSlowerPaceReachesTwoMinutesInFewerBreaths() {
        // W27 lets the user pick 6s or 8s a phase; the mark must follow the
        // clock, not a fixed breath count. At 8s a phase a breath is 8 + 10 = 18s.
        assertTrue(twoMinutesReached(BreathePreset.Reset, 8, 7))    // 18s x 7 = 126
        assertFalse(twoMinutesReached(BreathePreset.Reset, 8, 6))   // 108
    }

    @Test
    fun onlyTheResetPresetClaimsTwoMinutes() {
        // Box and Color are open-ended by design and promise no duration
        // anywhere, so they must not start announcing one.
        assertFalse(twoMinutesReached(BreathePreset.Box, 4, 100))
        assertFalse(twoMinutesReached(BreathePreset.Color, 4, 100))
    }
}
