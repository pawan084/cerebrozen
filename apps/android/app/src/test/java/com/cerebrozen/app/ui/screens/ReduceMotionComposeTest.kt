package com.cerebrozen.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
