package com.cerebrozen.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the Reduce-Motion branch in a real composition (off-device, Robolectric,
 * so it runs in the JVM unit job — no emulator). `rememberReduceMotion()` must
 * reflect the system "Remove animations" setting (ANIMATOR_DURATION_SCALE), and the
 * shared `appear` entrance must compose and show its content when it's on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReduceMotionComposeTest {

    @get:Rule val compose = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Composable
    private fun Probe() {
        Text(if (rememberReduceMotion()) "reduced" else "full", modifier = Modifier.testTag("probe"))
    }

    @Test
    fun rememberReduceMotion_true_when_animations_removed() {
        setAnimatorScale(0f)
        compose.setContent { Probe() }
        compose.onNodeWithTag("probe").assertTextEquals("reduced")
    }

    @Test
    fun rememberReduceMotion_false_at_normal_scale() {
        setAnimatorScale(1f)
        compose.setContent { Probe() }
        compose.onNodeWithTag("probe").assertTextEquals("full")
    }

    @Test
    fun breatheEngine_under_reduce_motion_still_shows_phase_and_count() {
        setAnimatorScale(0f)
        compose.setContent { BreatheEngine(BreathePreset.Reset) }
        // The orb holds still, but the guidance stays: phase label + count render.
        compose.onNodeWithText("Breathe in").assertExists()
        compose.onNodeWithText("4").assertExists()
    }

    @Test
    fun presenceWeekRing_under_reduce_motion_renders_every_dot_immediately() {
        setAnimatorScale(0f)
        val week = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, d -> d to (i % 2 == 0) }
        compose.setContent { PresenceWeekRing(week) }
        // The staggered fill is skipped: all 7 dots are present at rest without
        // advancing any animation clock (static render, never blank).
        (0..6).forEach { compose.onNodeWithTag("presence-dot-$it").assertExists() }
        compose.onNodeWithText("W").assertExists()   // day letters render too
    }

    @Test
    fun infoBanner_renders_statically_and_dismiss_is_labelled() {
        setAnimatorScale(0f)
        var dismissed = false
        compose.setContent {
            InfoBanner(
                icon = Icons.Outlined.CloudOff,
                text = "You're offline — showing your last copy.",
                onDismiss = { dismissed = true },
            )
        }
        compose.onNodeWithText("You're offline — showing your last copy.").assertExists()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertTrue("the labelled dismiss target must invoke the callback", dismissed)
    }

    @Test
    fun nightsChart_under_reduce_motion_renders_full_height_bars_statically() {
        setAnimatorScale(0f)
        // Equal durations → every bar's fraction is 1.0 of the 120dp chart row.
        val nights = listOf(
            Night("2026-07-10", 480, 4),
            Night("2026-07-09", 480, 3),
        )
        compose.setContent { NightsChart(nights) }
        // W10: the grow-in is skipped — bars sit at full height on the first
        // frame, without advancing any animation clock (static, never blank).
        compose.onNodeWithTag("night-bar-0").assertHeightIsEqualTo(120.dp)
        compose.onNodeWithTag("night-bar-1").assertHeightIsEqualTo(120.dp)
    }

    @Test
    fun appear_entrance_shows_content_under_reduce_motion() {
        setAnimatorScale(0f)
        compose.setContent {
            Box(Modifier.appear().size(20.dp)) {
                Text("hi", modifier = Modifier.testTag("entranceText"))
            }
        }
        // Reduce motion snaps the entrance to its resting state: the content is
        // present without advancing any animation clock.
        compose.onNodeWithTag("entranceText").assertTextEquals("hi")
    }
}
