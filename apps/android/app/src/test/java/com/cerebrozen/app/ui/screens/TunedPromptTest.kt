package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state-tuned Journal hero (iOS parity: `JournalPrompts.tuned(toMood:)`).
 *
 * Two decisions are pinned here because both are invisible until they are wrong:
 * the mood match is case-insensitive, and "today" is the reader's local day, not
 * the UTC one the server stamps.
 */
class TunedPromptTest {

    @Test
    fun `the three tuned feelings map to their own copy`() {
        assertEquals(R.string.journal_tuned_anxious_title, tunedPromptFor("Anxious")?.title)
        assertEquals(R.string.journal_tuned_low_title, tunedPromptFor("Low")?.title)
        assertEquals(R.string.journal_tuned_tired_title, tunedPromptFor("Tired")?.title)
    }

    @Test
    fun `a good day and an unknown feeling leave the rotation alone`() {
        // "Good" is a real check-in with no tuned variant — it must not borrow one.
        assertNull(tunedPromptFor("Good"))
        assertNull(tunedPromptFor("Elated"))
        assertNull(tunedPromptFor(""))
        assertNull(tunedPromptFor(null))
    }

    @Test
    fun `casing does not decide whether the journal responds`() {
        // The clients disagree: mobile posts "Anxious", the browser client posts
        // "anxious", and both land in the same column. A case-sensitive match
        // would tune nothing for anyone who checked in from the web.
        val expected = tunedPromptFor("Anxious")
        assertEquals(expected, tunedPromptFor("anxious"))
        assertEquals(expected, tunedPromptFor("ANXIOUS"))
        assertEquals(expected, tunedPromptFor("  Anxious  "))
    }

    @Test
    fun `today is the readers local day, not the servers UTC one`() {
        // 02:00 on the 31st in IST is 20:30 on the 30th in UTC. Comparing the raw
        // UTC date would stop tuning the hero exactly when someone journals late.
        val lateNightIst = "2026-07-30T20:30:00+00:00"
        val localDate = java.time.OffsetDateTime.parse(lateNightIst)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDate()

        assertTrue(isToday(lateNightIst, today = localDate))
        assertFalse(isToday(lateNightIst, today = localDate.plusDays(1)))
    }

    @Test
    fun `a malformed or missing timestamp is simply not today`() {
        // The hero falls back to the rotation rather than throwing inside a
        // LaunchedEffect and leaving the card blank.
        val today = LocalDate.of(2026, 7, 31)
        assertFalse(isToday(null, today))
        assertFalse(isToday("", today))
        assertFalse(isToday("not-a-date", today))
        assertFalse(isToday("2026-07-31", today))   // date only, no offset
    }
}
