package com.cerebrozen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The splash choreography curves are pure, so the brand arrival is testable by value rather
 * than by eye. These pin the contract the animation reads from: start/rest anchors, the
 * spring overshoot, the glow bloom, and the wordmark's back-half fade-up. See ui/Brand.kt.
 */
class SplashCurvesTest {

    @Test
    fun orb_scale_starts_small_settles_at_one_and_springs_past_it() {
        assertEquals("starts at 0.92", 0.92f, splashOrbScale(0f), 0.001f)
        assertEquals("comes to rest at exactly 1.0", 1.0f, splashOrbScale(1f), 0.005f)
        // A spring-like arrival overshoots 1.0 somewhere in the settle...
        val peak = (0..100).map { splashOrbScale(it / 100f) }.max()
        assertTrue("must overshoot past 1.0", peak > 1.0f)
        assertTrue("but stays tasteful (< 1.08)", peak < 1.08f)
    }

    @Test
    fun glow_bloom_rests_at_one_and_swells_in_the_middle() {
        assertEquals(1.0f, splashGlowBloom(0f), 0.001f)
        assertEquals(1.0f, splashGlowBloom(1f), 0.01f)
        assertTrue("blooms past its resting strength mid-settle", splashGlowBloom(0.5f) > 1.5f)
    }

    @Test
    fun wordmark_fades_up_only_in_the_back_half() {
        assertEquals("hidden while the orb is still arriving", 0f, splashWordmarkAppear(0.4f), 0.001f)
        assertEquals("fully in by the end", 1f, splashWordmarkAppear(1f), 0.001f)
        assertTrue("appears after the mid-point", splashWordmarkAppear(0.75f) > 0f)
    }
}
