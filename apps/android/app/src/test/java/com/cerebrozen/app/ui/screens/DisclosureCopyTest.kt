package com.cerebrozen.app.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Not a therapist, diagnosis, or crisis service."
 *
 * `docs/CLAIMS_MAP.md` §2 has named this test as the mechanism behind that claim
 * for some time, and the file did not exist — the row cited a guarantee nobody
 * had written. That is the failure the claims map exists to prevent, so this
 * makes the citation true rather than quietly softening the row.
 *
 * `ScreenLogicTest` already covers *when* the disclosure re-shows (the 3-hour
 * cadence). What was untested is *what it says*: that every AI surface names the
 * three things CereBro is not, and that none of this copy drifts into the
 * medical register the product is not licensed for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisclosureCopyTest {

    private val res get() = ApplicationProvider.getApplicationContext<Context>().resources

    /** The persistent pill on Talk and the full sheet behind it. */
    private val aiSurfaces = listOf(
        R.string.talk_disclosure_pill,
        R.string.talk_disclosure_dialog_body,
        R.string.ob_disclosure_sub,
    )

    @Test
    fun `the talk pill names all three things CereBro is not`() {
        // The pill is the always-visible one, so it carries the whole claim on
        // its own — a user who never opens the sheet still sees it.
        val pill = res.getString(R.string.talk_disclosure_pill).lowercase()
        assertTrue("the pill must say it is AI: $pill", "ai" in pill)
        assertTrue("the pill must disclaim being a therapist: $pill", "therapist" in pill)
        assertTrue("the pill must disclaim being a crisis service: $pill", "crisis" in pill)
    }

    @Test
    fun `every AI surface disclaims medical care`() {
        aiSurfaces.forEach { id ->
            val copy = res.getString(id).lowercase()
            assertTrue(
                "an AI disclosure surface says nothing about not being medical care: $copy",
                listOf("medical", "diagnose", "diagnoses", "therapist", "clinician")
                    .any { it in copy },
            )
        }
    }

    @Test
    fun `no disclosure surface claims to treat or cure`() {
        // The disclosure is the last place that should drift into the register it
        // exists to disclaim. check-claims.mjs bans these repo-wide; this pins
        // them on the specific strings whose whole job is the denial.
        val banned = listOf("treats ", "cures ", "clinically proven", "prescrib", "guaranteed")
        aiSurfaces.forEach { id ->
            val copy = res.getString(id).lowercase()
            banned.forEach { phrase ->
                // "never diagnoses or prescribes" is a denial, not a claim — so
                // only flag the banned verb when it is NOT negated nearby.
                val idx = copy.indexOf(phrase)
                if (idx >= 0) {
                    val before = copy.substring(maxOf(0, idx - 40), idx)
                    assertTrue(
                        "disclosure copy uses '$phrase' as a claim rather than a denial: $copy",
                        listOf("never", "not", "isn't", "cannot", "can't", "doesn't").any { it in before },
                    )
                }
            }
        }
    }

    @Test
    fun `the onboarding disclosure says it cannot replace professional care`() {
        val sub = res.getString(R.string.ob_disclosure_sub).lowercase()
        assertTrue("must disclaim replacing professional care: $sub", "replace" in sub)
        assertFalse("must not promise an outcome: $sub", "will help you" in sub)
    }
}
