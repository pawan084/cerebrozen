package com.cerebrozen.app.ui.screens

import org.junit.Assert.assertEquals
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
        // 2026-07-29.
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
}
