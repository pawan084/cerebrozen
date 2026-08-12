package com.cerebrozen.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Verified" on the crisis screen is a claim, and this pins what earns it.
 *
 * The badge used to render unconditionally, so a user in any country saw a green
 * "Verified" sitting against numbers nobody here had checked — a US user read it
 * against 911 and 988. That is the same defect the `ref/` audit found on the
 * prototypes (Indian numbers badged Verified for every country), and it is the
 * worst screen in the app to be wrong on: the badge's whole job is to tell
 * someone in trouble that the number below it will actually answer.
 *
 * India is the one region checked against a named source (MoHFW Tele-MANAS and
 * the ERSS 112 listing — the two the web `/safety` page cites). These tests fail
 * if a region is ever added to [VERIFIED_CRISIS_REGIONS] without that work, and
 * they are the test named in the CLAIMS_MAP row for this claim.
 */
class CrisisVerifiedBadgeTest {

    /** Every region `Settings` lets a user pick, plus the international fallback. */
    private val offeredRegions = listOf("IN", "US", "GB", "CA", "AU", "NZ", "IE", "")

    @Test
    fun `india is verified`() {
        assertTrue(crisisRegionIsVerified("IN"))
    }

    @Test
    fun `every other region the picker offers is not badged`() {
        offeredRegions.filterNot { it == "IN" }.forEach { region ->
            assertFalse(
                "region '$region' would show a Verified badge over numbers nobody has checked",
                crisisRegionIsVerified(region),
            )
        }
    }

    @Test
    fun `an unknown region is not verified`() {
        // The badge fails closed: a region we have never heard of cannot be one
        // whose numbers we checked.
        assertFalse(crisisRegionIsVerified("ZZ"))
        assertFalse(crisisRegionIsVerified(""))
        assertFalse(crisisRegionIsVerified("   "))
    }

    @Test
    fun `region matching is case and whitespace insensitive`() {
        // effectiveRegion() can hand this a device locale country, which arrives
        // in whatever shape the platform gives it. A stray space must not silently
        // DEMOTE India to unverified either — both directions of this are wrong.
        assertTrue(crisisRegionIsVerified("in"))
        assertTrue(crisisRegionIsVerified(" IN "))
        assertFalse(crisisRegionIsVerified("us"))
    }

    @Test
    fun `the verified set is deliberately small`() {
        // Not a style rule. If this grows, someone checked numbers against a named
        // source and should say which in VERIFIED_CRISIS_REGIONS' comment and in
        // docs/CLAIMS_MAP.md — this failure is the prompt to do that.
        assertTrue(
            "a region was added to VERIFIED_CRISIS_REGIONS: cite its source in the KDoc and CLAIMS_MAP",
            VERIFIED_CRISIS_REGIONS == setOf("IN"),
        )
    }
}
