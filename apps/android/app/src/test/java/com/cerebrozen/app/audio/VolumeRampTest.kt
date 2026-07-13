package com.cerebrozen.app.audio

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * W27 §1: the shared crossfade helper both audio services ramp through. The
 * Handler runs on the main looper, so Robolectric's shadow clock drives every
 * step deterministically — the contract (reaches the target exactly, fires
 * onDone once, last-intent-wins replacement, cancel is silent, values always
 * coerced to 0..1) is assertable without any player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeRampTest {

    private fun drain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))

    @Test
    fun ramps_up_to_the_exact_target_and_fires_onDone_once() {
        val ramp = VolumeRamp()
        val seen = mutableListOf<Float>()
        var done = 0
        ramp.ramp(from = 0f, to = 0.8f, onStep = { seen += it }, onDone = { done++ })
        drain()
        assertTrue("steps were applied", seen.isNotEmpty())
        assertEquals("lands exactly on the target", 0.8f, seen.last())
        assertEquals("monotonically rising", seen.sorted(), seen)
        assertEquals(1, done)
    }

    @Test
    fun ramps_down_for_pause_and_stop_tails() {
        val ramp = VolumeRamp()
        val seen = mutableListOf<Float>()
        var done = false
        ramp.ramp(from = 1f, to = 0f, onStep = { seen += it }, onDone = { done = true })
        drain()
        assertEquals("fades to silence", 0f, seen.last())
        assertEquals("monotonically falling", seen.sortedDescending(), seen)
        assertTrue(done)
    }

    @Test
    fun a_new_ramp_replaces_one_in_flight_and_the_old_onDone_never_fires() {
        val ramp = VolumeRamp()
        var oldDone = false
        var newDone = false
        val seen = mutableListOf<Float>()
        ramp.ramp(from = 0f, to = 1f, onStep = { }, onDone = { oldDone = true })
        // Half-way through the old ramp, a pause arrives: last intent wins.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        ramp.ramp(from = 0.5f, to = 0f, onStep = { seen += it }, onDone = { newDone = true })
        drain()
        assertFalse("the replaced ramp must not complete", oldDone)
        assertTrue(newDone)
        assertEquals(0f, seen.last())
    }

    @Test
    fun cancel_stops_stepping_and_never_completes() {
        val ramp = VolumeRamp()
        val seen = mutableListOf<Float>()
        var done = false
        ramp.ramp(from = 0f, to = 1f, onStep = { seen += it }, onDone = { done = true })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        val applied = seen.size
        ramp.cancel()
        drain()
        assertEquals("no steps after cancel", applied, seen.size)
        assertFalse(done)
    }

    @Test
    fun values_are_always_coerced_into_the_valid_volume_range() {
        val ramp = VolumeRamp()
        val seen = mutableListOf<Float>()
        ramp.ramp(from = -0.5f, to = 1.5f, onStep = { seen += it })
        drain()
        assertTrue("every applied value stays 0..1", seen.all { it in 0f..1f })
        assertEquals("an out-of-range target clamps to full", 1f, seen.last())
    }

    @Test
    fun a_custom_duration_still_lands_on_target() {
        val ramp = VolumeRamp()
        val seen = mutableListOf<Float>()
        var done = false
        ramp.ramp(from = 0.2f, to = 0.6f, durationMs = 120L, onStep = { seen += it }, onDone = { done = true })
        drain()
        assertEquals(0.6f, seen.last(), 1e-4f)
        assertTrue(done)
    }
}
