package com.cerebrozen.app.ui.screens

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which country's helplines a member is shown must follow where the phone *is*,
 * never what language its UI is set to.
 *
 * Found on a real handset (2026-08-14), not by reasoning about the code: a
 * OnePlus sold in India ships `persist.sys.locale=en-GB` from the factory, while
 * its SIM, network and timezone all reported `IN`. `Locale.getDefault().country`
 * returned GB, so the You screen offered "Samaritans · 116 123" — a UK number
 * that does not answer from India — and Tele-MANAS, which is supposed to lead
 * every crisis surface, appeared nowhere. en-GB is a factory default across
 * OnePlus, Oppo, Xiaomi and Realme handsets sold in India, so the primary market
 * was the one getting it wrong.
 *
 * The existing `check-crisis-lines.mjs` gate cannot catch this. It proves the
 * three stacks agree on *what each region's numbers are*; this is about *which
 * region gets picked*. Different bug, different test.
 *
 * The device-locale case is pinned last and deliberately: it is the fallback, so
 * it must still work — but it must never win while telephony has an answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrisisCountryResolutionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun telephony() =
        shadowOf(context.getSystemService(TelephonyManager::class.java))

    @Test
    fun `an Indian handset with a British UI language is still in India`() {
        // The exact configuration found on the device.
        java.util.Locale.setDefault(java.util.Locale.UK)
        telephony().setNetworkCountryIso("in")
        telephony().setSimCountryIso("in")

        assertEquals("IN", deviceCrisisCountry(context))
    }

    @Test
    fun `the network wins over the SIM, so a visitor gets local numbers`() {
        // An Indian SIM roaming in Britain: the numbers that answer are the ones
        // where the person is standing, not the ones from home.
        java.util.Locale.setDefault(java.util.Locale.UK)
        telephony().setNetworkCountryIso("gb")
        telephony().setSimCountryIso("in")

        assertEquals("GB", deviceCrisisCountry(context))
    }

    @Test
    fun `with no network registration the SIM still answers`() {
        // No service is exactly when someone may be reaching for a helpline, so
        // losing the country at that moment would be the worst possible time.
        java.util.Locale.setDefault(java.util.Locale.UK)
        telephony().setNetworkCountryIso("")
        telephony().setSimCountryIso("in")

        assertEquals("IN", deviceCrisisCountry(context))
    }

    @Test
    fun `locale is the last resort, not the first`() {
        // A wifi-only tablet has nothing else — better than nothing, and the
        // only case where the old behaviour was right.
        java.util.Locale.setDefault(java.util.Locale.UK)
        telephony().setNetworkCountryIso("")
        telephony().setSimCountryIso("")

        assertEquals("GB", deviceCrisisCountry(context))
    }

    @Test
    fun `an explicit profile choice still beats every device signal`() {
        // Someone who has told us their region owns the answer — a traveller who
        // wants their home lines, or anyone the detection gets wrong.
        assertEquals("IN", effectiveRegion(stored = "IN", deviceCountry = "GB"))
        assertEquals("GB", effectiveRegion(stored = "gb", deviceCountry = "IN"))
        assertEquals("IN", effectiveRegion(stored = "", deviceCountry = "in"))
        assertEquals("IN", effectiveRegion(stored = null, deviceCountry = "IN"))
    }

    @Test
    fun `the India case resolves to Tele-MANAS leading, which is the point`() {
        // The end-to-end assertion: the whole fix exists so that this member is
        // offered 14416 rather than a number that does not answer.
        java.util.Locale.setDefault(java.util.Locale.UK)
        telephony().setNetworkCountryIso("in")
        telephony().setSimCountryIso("in")

        val region = effectiveRegion(stored = null, deviceCountry = deviceCrisisCountry(context))
        assertEquals("14416", primaryCrisisLine(region).target)
    }
}
