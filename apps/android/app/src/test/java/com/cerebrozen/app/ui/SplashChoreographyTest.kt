package com.cerebrozen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The splash settle curves. Pure functions, so the choreography is checkable
 * without a device — but the one that matters is [splashSkyOpen], because its
 * value at t=0 is a *handoff contract*, not a taste call.
 *
 * The platform (Android 12+) splash paints a flat `@color/night` window with the
 * launcher icon on it, then Compose takes over. If the first Compose frame is
 * already showing the app's NightMid→Night gradient, the top of the screen jumps
 * to a colour roughly five times brighter in a single frame — measured on device
 * at 22:54. Starting the sky closed makes that frame identical to the one the
 * platform just handed over, so there is nothing to see.
 */
class SplashChoreographyTest {

    @Test
    fun theSkyStartsClosedSoTheHandoffIsInvisible() {
        assertEquals("first Compose frame must equal the platform splash window",
            0f, splashSkyOpen(0f), 0f)
    }

    @Test
    fun theSkyIsFullyOpenBeforeTheSettleEnds() {
        assertEquals(1f, splashSkyOpen(0.8f), 1e-4f)
        assertEquals(1f, splashSkyOpen(1f), 0f)
        assertTrue("and it opens monotonically", splashSkyOpen(0.4f) > splashSkyOpen(0.2f))
    }

    @Test
    fun theOrbSettlesFrom92PercentToRest() {
        assertEquals(0.92f, splashOrbScale(0f), 1e-4f)
        assertEquals(1f, splashOrbScale(0.65f), 1e-4f)
        assertEquals("and holds still afterwards", 1f, splashOrbScale(1f), 1e-4f)
    }

    @Test
    fun theGlowBloomsAndReturnsToRest() {
        assertEquals("rests at full strength, never dark", 1f, splashGlowBloom(0f), 1e-4f)
        assertEquals(1f, splashGlowBloom(1f), 1e-4f)
        assertTrue("swells past rest mid-settle", splashGlowBloom(0.5f) > 1.5f)
    }

    @Test
    fun theWordmarkWaitsForTheOrbThenArrives() {
        assertEquals("invisible while the orb is still moving", 0f, splashWordmarkAppear(0.4f), 0f)
        assertEquals(1f, splashWordmarkAppear(1f), 1e-4f)
    }

    @Test
    fun everyCurveIsClampedOutsideZeroToOne() {
        // Reduce Motion snaps progress to 1 and an interrupted animation can
        // report slightly out of range; no curve may return a wild value.
        for (t in listOf(-1f, -0.01f, 1.01f, 2f)) {
            assertTrue(splashSkyOpen(t) in 0f..1f)
            assertTrue(splashOrbScale(t) in 0.92f..1f)
            assertTrue(splashWordmarkAppear(t) in 0f..1f)
            assertTrue(splashGlowBloom(t) in 0.9f..1.95f)
        }
    }
}
