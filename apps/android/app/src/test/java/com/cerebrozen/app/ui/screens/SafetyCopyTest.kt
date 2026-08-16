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
 * Two safety claims that were false on device, pinned as resource facts.
 *
 * Both were found by walking the screens rather than by reading the code, and
 * neither was reachable by `check-claims.mjs`: that gate matches a list of
 * literal banned phrases, and these were wrong in their own words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafetyCopyTest {

    private val res get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `the TIPP self-harm note does not send the user hunting for a tab`() {
        // TIPP is entered at "a 9 or 10 when thinking feels impossible" and is the
        // one screen that names self-harm. It used to end "Urgent support lives in
        // the You tab" — directions, not a door, with no crisis affordance on the
        // screen at all. The house rule is that a risk signal always pairs with a
        // pathway, so the note must not describe navigation.
        val note = res.getString(R.string.tipp_urge_note)
        listOf("You tab", "tab", "Settings", "menu").forEach { nav ->
            assertFalse(
                "tipp_urge_note tells the user where to navigate ('$nav') instead of opening it: $note",
                note.contains(nav, ignoreCase = true),
            )
        }
        // …and the pathway itself has to exist.
        assertTrue(res.getString(R.string.tipp_urge_action).isNotBlank())
    }

    @Test
    fun `the trusted-contact line does not deny an automatic contact that happens`() {
        // `escalation.on_crisis` emails or texts the trusted contact on a
        // crisis-level safety event whenever `notify_consent` is on. The crisis
        // screen said "CereBro never contacts them automatically" — an absolute
        // that the trusted-contact screen contradicts and the backend disproves.
        // It defaults off, so the sentence was true until the moment a user turned
        // the feature on, which is exactly when being wrong matters.
        // V2-d: `urgent_trusted_detail` left this list with the whole dead
        // `urgent_*` block (18 strings duplicating `crisis_*`, zero Kotlin
        // references) when the CrisisScreen twin was deleted — one crisis
        // surface, one string set, one honesty pin.
        listOf(
            R.string.crisis_trusted_detail,
        ).forEach { id ->
            val copy = res.getString(id)
            assertFalse(
                "claims CereBro never contacts a trusted person automatically, but escalation.on_crisis does: $copy",
                copy.contains("never contacts", ignoreCase = true) ||
                    copy.contains("never contact", ignoreCase = true),
            )
            // The honest version has to keep the consent condition attached —
            // dropping it would flip the error to over-claiming instead.
            assertTrue(
                "the corrected copy must still say the contact is conditional on the user's choice: $copy",
                copy.contains("only if", ignoreCase = true) ||
                    copy.contains("switched that on", ignoreCase = true),
            )
        }
    }
}
