package com.cerebrozen.app.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W27 §2: the aurora's "what is audible" signal. The kind of the loaded title
 * is declared by the screen that started it (the only honest source), survives
 * a same-title resume, clears with the session, and `audibleKind()` prefers the
 * bed's declared kind, falls back to "soundscape" for an undeclared bed or the
 * running mixer, and is null in silence — never anything waveform-derived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingKindTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun silence() {
        Player.setState(null, false)
        SoundscapeMixer.publishPlaying(false)
    }

    @Test
    fun play_records_the_kind_the_screen_declared() {
        Player.play(context, "A calmer night", "sleep")
        Player.setState("A calmer night", true)   // service confirms
        assertEquals("sleep", Player.nowPlayingKind)
        assertEquals("sleep", Player.audibleKind())
    }

    @Test
    fun resume_of_the_same_title_keeps_the_original_kind() {
        Player.play(context, "Body scan", "meditation")
        Player.setState("Body scan", true)
        Player.setState("Body scan", false)        // paused
        Player.play(context, "Body scan")          // NowPlayingBar resume — no kind
        Player.setState("Body scan", true)
        assertEquals("meditation", Player.nowPlayingKind)
        assertEquals("meditation", Player.audibleKind())
    }

    @Test
    fun a_new_title_without_a_kind_is_undeclared_and_reads_as_soundscape() {
        Player.play(context, "Slow tide", "sleep")
        Player.setState("Slow tide", true)
        Player.play(context, "Something else")     // different title, kind unknown
        Player.setState("Something else", true)
        assertNull("no screen declared this kind", Player.nowPlayingKind)
        assertEquals("undeclared bed falls back to the generic accent", "soundscape", Player.audibleKind())
    }

    @Test
    fun stop_and_a_null_service_state_both_clear_the_kind() {
        Player.play(context, "A calmer night", "sleep")
        Player.setState("A calmer night", true)
        Player.stop(context)
        assertNull(Player.nowPlayingKind)
        assertNull("silence → no tint signal", Player.audibleKind())

        Player.play(context, "A calmer night", "sleep")
        Player.setState("A calmer night", true)
        Player.setState(null, false)               // service-side teardown
        assertNull(Player.nowPlayingKind)
        assertNull(Player.audibleKind())
    }

    @Test
    fun paused_audio_is_not_audible() {
        Player.play(context, "A calmer night", "sleep")
        Player.setState("A calmer night", false)   // loaded but silent
        assertNull("paused → no tint signal", Player.audibleKind())
    }

    @Test
    fun the_running_mixer_reads_as_a_soundscape() {
        SoundscapeMixer.publishPlaying(true)
        assertEquals("soundscape", Player.audibleKind())
        SoundscapeMixer.publishPlaying(false)
        assertNull(Player.audibleKind())
    }

    @Test
    fun the_playing_bed_takes_precedence_over_the_mixer_flag() {
        Player.play(context, "Body scan", "meditation")
        Player.setState("Body scan", true)
        SoundscapeMixer.publishPlaying(true)       // can't co-occur by contract; precedence is defined anyway
        assertEquals("meditation", Player.audibleKind())
        SoundscapeMixer.publishPlaying(false)
    }
}
