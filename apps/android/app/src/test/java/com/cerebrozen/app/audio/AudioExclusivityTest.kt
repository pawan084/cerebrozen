package com.cerebrozen.app.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REDESIGN §3.4: exactly one audio engine plays at a time. [Player] (ambient
 * bed) and [SoundscapeMixer] (4-layer mix) each stop the other on play, and
 * stop() never counter-calls — so starting one can't ping-pong. Robolectric
 * supplies a real Context (service intents are recorded, never executed), and
 * both objects flip their Compose state synchronously, so the contract is
 * assertable without running either foreground service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioExclusivityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        // Both are process-wide singletons — start every test from silence.
        Player.setState(null, false)
        SoundscapeMixer.publishPlaying(false)
    }

    @Test
    fun starting_the_bed_stops_a_running_mixer() {
        SoundscapeMixer.publishPlaying(true)
        Player.play(context, "A calmer night")
        assertFalse(SoundscapeMixer.isPlaying)
    }

    @Test
    fun starting_the_mixer_stops_a_playing_bed() {
        Player.setState("A calmer night", true)
        SoundscapeMixer.play(context)
        assertTrue(SoundscapeMixer.isPlaying)
        assertFalse(Player.isPlaying)
        assertNull(Player.nowPlaying)
    }

    @Test
    fun starting_the_mixer_leaves_a_paused_bed_alone() {
        Player.setState("A calmer night", false)   // loaded but not playing
        SoundscapeMixer.play(context)
        assertTrue(SoundscapeMixer.isPlaying)
        // The paused session is untouched — only *playing* audio yields.
        assertTrue(Player.nowPlaying == "A calmer night")
    }

    @Test
    fun stop_never_retriggers_the_other_engine() {
        SoundscapeMixer.publishPlaying(true)
        Player.setState("A calmer night", true)
        Player.stop(context)
        assertTrue(SoundscapeMixer.isPlaying)   // untouched by the bed's stop
        assertFalse(Player.isPlaying)
        SoundscapeMixer.stop(context)
        assertFalse(SoundscapeMixer.isPlaying)
        assertFalse(Player.isPlaying)           // still stopped, no ping-pong
    }
}
