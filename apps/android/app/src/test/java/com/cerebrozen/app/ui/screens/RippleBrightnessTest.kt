package com.cerebrozen.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Zen Ripples pitches each drop by where you touched the pool — top brighter,
 * bottom lower — so a run of taps plays as a phrase instead of twelve identical
 * plinks. The mapping is inverted (y grows downward in screen space) and clamped,
 * and it must not divide by zero on the first frame, before layout has a height.
 */
class RippleBrightnessTest {

    @Test
    fun the_top_of_the_pool_is_the_brightest_drop() {
        assertEquals(1f, rippleBrightness(y = 0f, height = 400f), 0.001f)
    }

    @Test
    fun the_bottom_of_the_pool_is_the_lowest_drop() {
        assertEquals(0f, rippleBrightness(y = 400f, height = 400f), 0.001f)
    }

    @Test
    fun the_middle_sits_in_the_middle() {
        assertEquals(0.5f, rippleBrightness(y = 200f, height = 400f), 0.001f)
    }

    @Test
    fun taps_outside_the_bounds_clamp_rather_than_running_off_the_scale() {
        assertEquals(1f, rippleBrightness(y = -50f, height = 400f), 0.001f)
        assertEquals(0f, rippleBrightness(y = 900f, height = 400f), 0.001f)
    }

    @Test
    fun a_zero_height_canvas_resolves_to_the_middle_instead_of_dividing_by_zero() {
        assertEquals(0.5f, rippleBrightness(y = 0f, height = 0f), 0.001f)
        assertEquals(0.5f, rippleBrightness(y = 10f, height = -1f), 0.001f)
    }
}
