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
            assertEquals(
                "pace never changes the guidance",
                listOf(R.string.breathe_phase_in, R.string.breathe_phase_hold,
                    R.string.breathe_phase_out, R.string.breathe_phase_hold),
                phases.map { it.labelRes },
            )
            assertEquals(listOf(true, true, false, false), phases.map { it.expanded })
            assertEquals("Color shares Box pacing at every pace",
                phases, breathePhases(BreathePreset.Color, pace))
        }
    }

    @Test
    fun pace_scales_the_reset_rhythm_and_it_still_has_no_holds() {
        val phases = breathePhases(BreathePreset.Reset, 8)
        assertEquals(listOf(8, 8), phases.map { it.seconds })
        assertEquals(listOf(R.string.breathe_phase_in, R.string.breathe_phase_out), phases.map { it.labelRes })
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
        // Reset is an open-ended in/out cycle. Reset at 4s a phase is 8s a
        // breath, so two minutes is fifteen of them.
        assertEquals(8, breatheElapsedSeconds(BreathePreset.Reset, 4, 1))
        assertEquals(120, breatheElapsedSeconds(BreathePreset.Reset, 4, 15))
        assertFalse(twoMinutesReached(BreathePreset.Reset, 4, 14))
        assertTrue(twoMinutesReached(BreathePreset.Reset, 4, 15))
    }

    @Test
    fun aSlowerPaceReachesTwoMinutesInFewerBreaths() {
        // W27 lets the user pick 6s or 8s a phase; the mark must follow the
        // clock, not a fixed breath count.
        assertTrue(twoMinutesReached(BreathePreset.Reset, 8, 8))    // 16s x 8 = 128
        assertFalse(twoMinutesReached(BreathePreset.Reset, 8, 7))   // 112
    }

    @Test
    fun onlyTheResetPresetClaimsTwoMinutes() {
        // Box and Color are open-ended by design and promise no duration
        // anywhere, so they must not start announcing one.
        assertFalse(twoMinutesReached(BreathePreset.Box, 4, 100))
        assertFalse(twoMinutesReached(BreathePreset.Color, 4, 100))
    }
}
