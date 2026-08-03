package com.cerebrozen.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two tools salvaged from PR #2 — the only part of that branch worth keeping.
 *
 * The branch itself predated both the string externalisation and the Dawn theme, so
 * merging it would have put hardcoded English and raw hex back into every screen.
 * These tests pin the two things that made the port worth doing rather than the
 * merge: the copy comes from resources, and each tool states why it works. A future
 * "quick tool" added by copy-pasting the old branch would fail both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalvagedToolsTest {

    @get:Rule val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `both tools carry a why-this-works line like every other tool here`() {
        // CBT and TIPP set the precedent: a tool that asks the user to write
        // something says what the practice is and where it comes from.
        for (id in listOf(R.string.onegood_why, R.string.intention_why)) {
            val why = res.getString(id)
            assertTrue("provenance too thin: $why", why.length > 60)
            assertTrue("provenance cites nothing: $why", why.contains("("))
        }
    }

    @Test
    fun `the compose formats take the field value rather than dropping it`() {
        // compose = { v -> template.format(v[0]) } — a template that forgot its
        // placeholder would silently save the same sentence for every entry.
        for (id in listOf(R.string.onegood_compose_format, R.string.intention_compose_format)) {
            val template = res.getString(id)
            assertTrue("no placeholder in: $template", template.contains("%1\$s"))
            assertFalse("saved text would be empty", template.format("x").trim() == "x")
            assertTrue(template.format("a small win").contains("a small win"))
        }
    }

    @Test
    fun `one good thing renders its prompt from resources`() {
        compose.setContent { OneGoodThingScreen(onBack = {}) }

        compose.onNodeWithText(res.getString(R.string.onegood_title)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.onegood_field)).assertIsDisplayed()
    }

    @Test
    fun `tomorrows intention renders its prompt from resources`() {
        compose.setContent { IntentionScreen(onBack = {}) }

        compose.onNodeWithText(res.getString(R.string.intention_title)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.intention_field)).assertIsDisplayed()
    }
}
