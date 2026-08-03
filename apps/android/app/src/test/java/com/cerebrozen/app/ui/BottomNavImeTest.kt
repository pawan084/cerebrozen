package com.cerebrozen.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tab bar must yield its whole slot to the keyboard, not merely sit behind it.
 *
 * The bug this pins: `BottomNavBar` emitted the pill unconditionally, so with the
 * IME up Scaffold still charged the body ~78dp for a bar the keyboard was covering
 * — and since every screen body also carries `imePadding()` (Common.kt `Page`), the
 * two stacked into an empty band above the keyboard. "The tabs are hidden" is the
 * weaker claim; the regression is about *reserved height*, so both tests measure
 * the gap Scaffold leaves under the body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomNavImeTest {

    @get:Rule val compose = createComposeRule()

    private fun scaffoldWithNav(imeVisible: Boolean) {
        compose.setContent {
            Scaffold(
                bottomBar = {
                    BottomNavBar(
                        currentRoute = "home",
                        compact = false,
                        imeVisible = imeVisible,
                        onSelect = {},
                    )
                },
            ) { padding ->
                // Full-bleed body: the gap between its bottom edge and the root's
                // is exactly the slot the bottom bar reserved.
                Box(Modifier.fillMaxSize().padding(padding).testTag("body"))
            }
        }
    }

    /** Distance in dp from the body's bottom edge to the bottom of the window. */
    private fun reservedSlotDp(): Float {
        val body = compose.onNodeWithTag("body").getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot()
        return (root.bottom - body.bottom).value
    }

    @Test
    fun `keyboard up reserves no bottom slot`() {
        scaffoldWithNav(imeVisible = true)

        assertEquals(0f, reservedSlotDp(), 0.5f)
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Talk").assertDoesNotExist()
    }

    @Test
    fun `keyboard down keeps the tab bar and its slot`() {
        scaffoldWithNav(imeVisible = false)

        // The pill is 78dp tall plus the container's 4dp vertical padding; assert
        // the slot is real rather than pinning the exact design value.
        assertTrue("expected a reserved nav slot, got ${reservedSlotDp()}dp", reservedSlotDp() >= 72f)
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Talk").assertIsDisplayed()
    }
}
