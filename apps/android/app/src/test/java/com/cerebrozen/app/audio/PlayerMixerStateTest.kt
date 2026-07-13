package com.cerebrozen.app.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * State-machine coverage for the two audio controllers beyond the exclusivity
 * contract (AudioExclusivityTest): volume/timer/duck command gating (nothing
 * playing → remember state, never start an idle service), the shared
 * off→15→30→45→60→off timer ladder, layer volume clamping/toggling, and the
 * countdown label. Robolectric records the service intents without executing
 * either foreground service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerMixerStateTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun silence() {
        Player.setState(null, false)
        Player.setTimerState(0)
        Player.setVolumeState(1f)
        SoundscapeMixer.publishPlaying(false)
        SoundscapeMixer.publishTimer(0, null)
    }

    private fun startedServices() = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

    // ── Player ─────────────────────────────────────────────────────────

    @Test
    fun player_volume_is_remembered_but_no_service_starts_while_idle() {
        Player.setVolume(context, 0.4f)
        assertEquals(0.4f, Player.volume)
        assertNull("idle → no AmbientService command", startedServices().nextStartedService)
    }

    @Test
    fun player_volume_command_reaches_the_service_when_playing() {
        Player.setState("A calmer night", true)
        Player.setVolume(context, 0.6f)
        val intent = startedServices().nextStartedService
        assertEquals(AmbientService.ACTION_VOLUME, intent!!.action)
        assertEquals(0.6f, intent.getFloatExtra(AmbientService.EXTRA_VOLUME, -1f))
    }

    @Test
    fun player_timer_cycles_the_ladder_and_only_arms_with_a_session() {
        // Idle: the minutes advance locally but no service intent fires.
        Player.cycleTimer(context)
        assertEquals(15, Player.timerMinutes)
        assertNull(startedServices().nextStartedService)

        Player.setState("A calmer night", true)
        listOf(30, 45, 60, 0).forEach { expected ->
            Player.cycleTimer(context)
            assertEquals(expected, Player.timerMinutes)
            val intent = startedServices().nextStartedService
            assertEquals(AmbientService.ACTION_TIMER, intent!!.action)
            assertEquals(expected, intent.getIntExtra(AmbientService.EXTRA_MINUTES, -1))
        }
    }

    @Test
    fun player_toggle_pauses_the_playing_title_and_plays_a_new_one() {
        Player.setState("A calmer night", true)
        Player.toggle(context, "A calmer night")
        assertEquals("same title while playing → pause",
            AmbientService.ACTION_PAUSE, startedServices().nextStartedService!!.action)

        Player.setState("A calmer night", false)
        MediaUrls.register("Slow tide", "https://cdn/x.mp3")
        Player.toggle(context, "Slow tide")
        val play = startedServices().nextStartedService
        assertEquals(AmbientService.ACTION_PLAY, play!!.action)
        assertEquals("Slow tide", play.getStringExtra(AmbientService.EXTRA_TITLE))
        assertEquals("https://cdn/x.mp3", play.getStringExtra(AmbientService.EXTRA_URL))
        MediaUrls.clear()
    }

    @Test
    fun player_duck_only_signals_while_a_bed_is_playing() {
        Player.duck(context, true)
        assertNull("nothing playing → nothing to duck", startedServices().nextStartedService)

        Player.setState("A calmer night", true)
        Player.duck(context, true)
        val intent = startedServices().nextStartedService
        assertEquals(AmbientService.ACTION_DUCK, intent!!.action)
        assertTrue(intent.getBooleanExtra(AmbientService.EXTRA_DUCK, false))
    }

    // ── SoundscapeMixer ────────────────────────────────────────────────

    @Test
    fun mixer_ships_four_layers_with_rain_as_the_primary() {
        assertEquals(listOf("Rain", "Ocean", "Wind", "Drone"), SoundscapeMixer.layers.map { it.name })
        assertEquals("rain", SoundscapeMixer.layers[0].symbol)
        assertEquals(4, SoundscapeMixer.volumes.size)
    }

    @Test
    fun mixer_toggle_starts_then_pauses() {
        SoundscapeMixer.toggle(context)
        assertTrue(SoundscapeMixer.isPlaying)
        SoundscapeMixer.toggle(context)
        assertFalse(SoundscapeMixer.isPlaying)
    }

    @Test
    fun mixer_layer_volume_clamps_ignores_bad_indices_and_signals_only_while_playing() {
        SoundscapeMixer.setLayerVolume(context, 1, 1.7f)
        assertEquals("volumes clamp to 0..1", 1f, SoundscapeMixer.volumes[1])
        SoundscapeMixer.setLayerVolume(context, 1, -0.3f)
        assertEquals(0f, SoundscapeMixer.volumes[1])
        SoundscapeMixer.setLayerVolume(context, 99, 0.5f)   // out of range → no-op
        while (startedServices().nextStartedService != null) { /* drain: idle sends nothing */ }

        SoundscapeMixer.publishPlaying(true)
        SoundscapeMixer.setLayerVolume(context, 2, 0.5f)
        val intent = startedServices().nextStartedService
        assertEquals(SoundscapeService.ACTION_LAYER, intent!!.action)
        assertEquals(2, intent.getIntExtra(SoundscapeService.EXTRA_INDEX, -1))
        assertEquals(0.5f, intent.getFloatExtra(SoundscapeService.EXTRA_VOLUME, -1f))
        SoundscapeMixer.setLayerVolume(context, 2, 0f)
    }

    @Test
    fun mixer_toggleLayer_flips_between_silent_and_the_default_level() {
        SoundscapeMixer.setLayerVolume(context, 3, 0f)
        SoundscapeMixer.toggleLayer(context, 3)
        assertEquals(0.7f, SoundscapeMixer.volumes[3])
        SoundscapeMixer.toggleLayer(context, 3)
        assertEquals(0f, SoundscapeMixer.volumes[3])
        SoundscapeMixer.toggleLayer(context, -1)   // out of range → no-op
    }

    @Test
    fun mixer_master_volume_clamps_and_signals_only_while_playing() {
        SoundscapeMixer.setMasterVolume(context, 2f)
        assertEquals(1f, SoundscapeMixer.master)
        SoundscapeMixer.publishPlaying(true)
        SoundscapeMixer.setMasterVolume(context, 0.25f)
        assertEquals(0.25f, SoundscapeMixer.master)
        SoundscapeMixer.publishPlaying(false)
        SoundscapeMixer.setMasterVolume(context, 0.7f)   // restore the default
    }

    @Test
    fun mixer_timer_ladder_and_countdown_label() {
        assertNull("disarmed → no label", SoundscapeMixer.remainingText())
        listOf(15, 30, 45, 60, 0).forEach { expected ->
            SoundscapeMixer.cycleTimer(context)
            assertEquals(expected, SoundscapeMixer.timerMinutes)
        }
        SoundscapeMixer.publishTimer(15, 95)
        assertEquals("1:35", SoundscapeMixer.remainingText())
        SoundscapeMixer.publishTimer(15, 61)
        assertEquals("1:01", SoundscapeMixer.remainingText())
        SoundscapeMixer.publishTimer(0, null)
    }

    @Test
    fun mixer_stop_disarms_everything() {
        SoundscapeMixer.play(context)
        SoundscapeMixer.publishTimer(30, 1799)
        SoundscapeMixer.stop(context)
        assertFalse(SoundscapeMixer.isPlaying)
        assertEquals(0, SoundscapeMixer.timerMinutes)
        assertNull(SoundscapeMixer.remaining)
    }

    // ── MediaUrls edge cases ───────────────────────────────────────────

    @Test
    fun mediaUrls_resolution_and_registry_edges() {
        assertEquals("", MediaUrls.resolve(null, "https://api.x"))
        assertEquals("", MediaUrls.resolve("   ", "https://api.x"))
        assertEquals("https://api.x/media/a.mp3", MediaUrls.resolve("/media/a.mp3", "https://api.x"))
        assertEquals("trailing base slashes must not double up",
            "https://api.x/media/a.mp3", MediaUrls.resolve("/media/a.mp3", "https://api.x///"))
        assertEquals("absolute urls pass through untouched",
            "https://cdn.example/a.mp3", MediaUrls.resolve("https://cdn.example/a.mp3", "https://api.x"))
        assertEquals("whitespace is trimmed before resolving",
            "https://api.x/m.mp3", MediaUrls.resolve("  /m.mp3  ", "https://api.x"))

        MediaUrls.register("Tide", "https://cdn/t.mp3")
        assertEquals("https://cdn/t.mp3", MediaUrls.urlFor("Tide"))
        MediaUrls.register("Tide", "")   // blank re-registration removes the entry
        assertEquals("", MediaUrls.urlFor("Tide"))
        MediaUrls.register("Keep", "https://cdn/k.mp3")
        MediaUrls.clear()
        assertEquals("", MediaUrls.urlFor("Keep"))
    }
}
