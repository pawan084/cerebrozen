package com.cerebrozen.app.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * W27 §3: the mixer's named one-tap blends. Three presets span the four
 * existing layers with the approved volume vectors, apply through the same
 * per-layer path the sliders use (so a live service hears every change), and
 * the selected chip is derived purely by vector match — nudging any slider
 * honestly deselects it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundscapeMixerPresetsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun shadowApp() = shadowOf(ApplicationProvider.getApplicationContext<Application>())

    @Before
    fun silence() {
        Player.setState(null, false)
        SoundscapeMixer.publishPlaying(false)
    }

    @After
    fun restoreDefaults() {
        SoundscapeMixer.publishPlaying(false)
        // The mixer is a process-wide singleton — put the default blend back.
        listOf(0.7f, 0f, 0f, 0f).forEachIndexed { i, v -> SoundscapeMixer.setLayerVolume(context, i, v) }
    }

    @Test
    fun three_presets_carry_the_approved_blends_over_the_four_layers() {
        assertEquals(listOf("monsoon_night", "shoreline", "still_air"), SoundscapeMixer.presets.map { it.key })
        SoundscapeMixer.presets.forEach { assertEquals(SoundscapeMixer.layers.size, it.volumes.size) }
        assertEquals(listOf(0.8f, 0f, 0.35f, 0.2f), SoundscapeMixer.presets[0].volumes)
        assertEquals(listOf(0f, 0.8f, 0.3f, 0f), SoundscapeMixer.presets[1].volumes)
        assertEquals(listOf(0f, 0f, 0.25f, 0.5f), SoundscapeMixer.presets[2].volumes)
        // Value semantics (the UI compares/copies presets as plain data).
        val copy = SoundscapeMixer.presets[0].copy()
        assertEquals(SoundscapeMixer.presets[0], copy)
        assertEquals(SoundscapeMixer.presets[0].hashCode(), copy.hashCode())
        assertTrue(copy.toString().contains("monsoon_night"))
    }

    @Test
    fun applying_a_preset_sets_the_volumes_and_selects_it_by_vector_match() {
        SoundscapeMixer.applyPreset(context, 0)
        assertEquals(listOf(0.8f, 0f, 0.35f, 0.2f), SoundscapeMixer.volumes.toList())
        assertEquals(0, SoundscapeMixer.matchingPreset())
        assertNull("idle mixer → no service commands", shadowApp().nextStartedService)

        // Nudge one slider — the chip must honestly deselect.
        SoundscapeMixer.setLayerVolume(context, 0, 0.5f)
        assertNull(SoundscapeMixer.matchingPreset())
    }

    @Test
    fun applying_a_preset_signals_a_live_service_layer_by_layer() {
        SoundscapeMixer.publishPlaying(true)
        SoundscapeMixer.applyPreset(context, 1)
        val expected = SoundscapeMixer.presets[1].volumes
        expected.forEachIndexed { i, v ->
            val intent = shadowApp().nextStartedService
            assertEquals(SoundscapeService.ACTION_LAYER, intent!!.action)
            assertEquals(i, intent.getIntExtra(SoundscapeService.EXTRA_INDEX, -1))
            assertEquals(v, intent.getFloatExtra(SoundscapeService.EXTRA_VOLUME, -1f))
        }
        assertEquals(1, SoundscapeMixer.matchingPreset())
    }

    @Test
    fun matching_tolerates_slider_noise_within_epsilon() {
        SoundscapeMixer.applyPreset(context, 2)
        SoundscapeMixer.setLayerVolume(context, 2, 0.25f + 0.005f)   // sub-epsilon wobble
        assertEquals(2, SoundscapeMixer.matchingPreset())
        SoundscapeMixer.setLayerVolume(context, 2, 0.25f + 0.02f)    // beyond epsilon
        assertNull(SoundscapeMixer.matchingPreset())
    }

    @Test
    fun out_of_range_preset_indices_are_a_noop() {
        SoundscapeMixer.applyPreset(context, 0)
        SoundscapeMixer.applyPreset(context, -1)
        SoundscapeMixer.applyPreset(context, 99)
        assertEquals("the blend is untouched", listOf(0.8f, 0f, 0.35f, 0.2f), SoundscapeMixer.volumes.toList())
    }

    @Test
    fun default_startup_blend_matches_no_preset() {
        // Rain 0.7 solo is the classic default, deliberately not a preset.
        listOf(0.7f, 0f, 0f, 0f).forEachIndexed { i, v -> SoundscapeMixer.setLayerVolume(context, i, v) }
        assertNull(SoundscapeMixer.matchingPreset())
    }
}
