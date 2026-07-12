package com.cerebrozen.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.audio.Player
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W27 §6: the breathing dot that replaced the fake-reactive EqBars beside the
 * now-playing title. The dot must be present in every state — playing, paused,
 * and under Reduce Motion it holds a static mid-size (static, never blank).
 * Rendered off-device via Robolectric; the animated branch is deliberately
 * exercised only under Reduce Motion (an infinite transition never idles a
 * compose test clock), which is exactly the branch the design contract gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingDotTest {

    @get:Rule val compose = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun silence() {
        Player.setState(null, false)
    }

    @Test
    fun playing_under_reduce_motion_renders_a_static_dot() {
        setAnimatorScale(0f)
        compose.setContent { BreathingDot(playing = true) }
        compose.onNodeWithTag("now-playing-dot").assertExists()
    }

    @Test
    fun paused_renders_a_static_dot_never_blank() {
        setAnimatorScale(1f)
        compose.setContent { BreathingDot(playing = false) }
        compose.onNodeWithTag("now-playing-dot").assertExists()
    }

    @Test
    fun nowPlayingBar_carries_the_dot_beside_the_title() {
        setAnimatorScale(0f)
        Player.setState("A calmer night", true)
        compose.setContent { NowPlayingBar() }
        // Unmerged: the bar's tappable title row merges its descendants' semantics.
        compose.onNodeWithTag("now-playing-dot", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("A calmer night").assertExists()
    }
}
