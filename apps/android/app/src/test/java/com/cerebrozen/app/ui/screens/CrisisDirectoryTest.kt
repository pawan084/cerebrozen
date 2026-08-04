package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The region-aware crisis directory — Android's mirror of backend
 * `app/services/crisis.py` (cross-stack contract; iOS carries the third copy).
 *
 * These pins exist because the pre-directory client hardcoded India's numbers
 * on every crisis surface, so a user who set United States still saw and
 * dialled 14416. Each region's numbers, the pill target, and the fallback are
 * therefore pinned value-by-value: a drifted number here is not a UI bug, it
 * is a wrong phone number on a crisis screen.
 */
class CrisisDirectoryTest {

    private val mappedRegions = listOf("US", "CA", "GB", "IE", "AU", "NZ", "IN")

    @Test
    fun indiaLeadsWithTeleManasThenEmergencyThenKiran() {
        // Tele-MANAS-first is the REDESIGN §2.3 rule for India, unlike the
        // emergency-first order every other region inherits from the backend.
        val lines = crisisLinesFor("IN")
        assertEquals(listOf("14416", "112", "1800-599-0019"), lines.map { it.target })
        assertTrue(lines.first().isCrisisLine)
        assertEquals(R.string.crisis_line_telemanas, lines.first().nameRes)
    }

    @Test
    fun everyMappedRegionMatchesTheBackendContract() {
        // Backend crisis.py, value for value. Emergency first, crisis line second.
        assertEquals(listOf("911", "988"), crisisLinesFor("US").map { it.target })
        assertEquals(listOf("911", "988"), crisisLinesFor("CA").map { it.target })
        assertEquals(listOf("999", "116 123"), crisisLinesFor("GB").map { it.target })
        assertEquals(listOf("112", "116 123"), crisisLinesFor("IE").map { it.target })
        assertEquals(listOf("000", "13 11 14"), crisisLinesFor("AU").map { it.target })
        assertEquals(listOf("111", "1737"), crisisLinesFor("NZ").map { it.target })
        // US and CA share a number but not a name — the services are distinct.
        assertEquals(R.string.crisis_line_us_lifeline, crisisLinesFor("US")[1].nameRes)
        assertEquals(R.string.crisis_line_ca_lifeline, crisisLinesFor("CA")[1].nameRes)
    }

    @Test
    fun theOneTapPillNeverDialsTheEmergencyNumber() {
        // The pill is the mental-health line: a one-tap 911/112 from a wellness
        // card would be wrong in both directions.
        mappedRegions.forEach { region ->
            val primary = primaryCrisisLine(region)
            assertTrue("$region pill must be its crisis line", primary.isCrisisLine)
            assertFalse(
                "$region pill must not be the emergency line",
                primary.target == crisisLinesFor(region).first { it.nameRes == R.string.crisis_line_emergency }.target,
            )
        }
    }

    @Test
    fun everyMappedRegionHasExactlyOneCrisisLine() {
        mappedRegions.forEach { region ->
            assertEquals(region, 1, crisisLinesFor(region).count { it.isCrisisLine })
        }
    }

    @Test
    fun unknownRegionsFallBackTo112PlusTheHelplineFinder() {
        // "EU" (the picker's own code), blank/auto, and any unmapped country all
        // land on the GSM emergency number + findahelpline — never India's list.
        listOf("EU", "", null, "FR", "BR", "xx").forEach { region ->
            val lines = crisisLinesFor(region)
            assertEquals(listOf("112", "findahelpline.com"), lines.map { it.target })
            // The pill target for an unmapped region is the finder (a URL, so
            // surfaces render Open/name instead of a Call label).
            assertTrue(primaryCrisisLine(region).target == "findahelpline.com")
            assertTrue(isSupportUrl(primaryCrisisLine(region).target))
        }
    }

    @Test
    fun dialTargetsAreDialableAndTheFinderIsALink() {
        mappedRegions.flatMap { crisisLinesFor(it) }.forEach { line ->
            assertFalse("${line.target} should open the dialler", isSupportUrl(line.target))
        }
    }

    @Test
    fun normalizeRegionTrimsUppercasesAndClips() {
        assertEquals("IN", normalizeRegion(" in "))
        assertEquals("US", normalizeRegion("us"))
        assertEquals("GB", normalizeRegion("GBR"))
        assertEquals("", normalizeRegion(null))
        assertEquals("", normalizeRegion("   "))
    }

    @Test
    fun effectiveRegionPrefersTheExplicitOverride() {
        // The You → Crisis region choice wins over the device locale…
        assertEquals("GB", effectiveRegion("GB", "IN"))
        // …and auto/blank falls through to the device country.
        assertEquals("US", effectiveRegion("", "us"))
        assertEquals("IN", effectiveRegion(null, "IN"))
        assertEquals("", effectiveRegion("", null))
    }

    @Test
    fun regionLabelsCoverEveryMappedRegionAndFallBackHonestly() {
        assertEquals(R.string.region_in, regionLabelRes("IN"))
        assertEquals(R.string.region_ie, regionLabelRes("IE"))
        // An unmapped device country says "International", not a wrong country.
        assertEquals(R.string.region_intl, regionLabelRes("FR"))
        assertEquals(R.string.region_intl, regionLabelRes(""))
    }
}
