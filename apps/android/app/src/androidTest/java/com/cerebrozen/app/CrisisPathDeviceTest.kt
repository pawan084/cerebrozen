package com.cerebrozen.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.crisisLinesFor
import com.cerebrozen.app.ui.screens.deviceCrisisCountry
import com.cerebrozen.app.ui.screens.effectiveRegion
import com.cerebrozen.app.ui.screens.primaryCrisisLine
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The crisis path, walked on hardware by something other than a person.
 *
 * `DeviceSmokeTest` proves the APK starts; it never opens a screen. This does,
 * and it opens the one screen where a defect is measured in human harm — the
 * flow `WORLD_CLASS` §0.5 names as never having been walked on hardware. Both
 * assertions here are deliberately things the existing gates *cannot* make:
 *
 *  - `CrisisDirectoryTest` and `scripts/check-crisis-lines.mjs` read the
 *    directory. They can prove the data is right and say nothing about what a
 *    phone renders from it — which is exactly how a UK helpline was once
 *    offered to Indian users on a device whose data was correct all along.
 *  - The ordering contract ("the mental-health line leads every crisis
 *    surface", REDESIGN §2.3) is asserted across three stacks by a script that
 *    reads the *directory*. `UrgentSupportScreen`'s own comment records that
 *    the gate could not see this screen, "because it reads the directory, not
 *    a layout". A rendered screen has coordinates, so here it can be seen.
 *
 * No test in this file dials anything. The last step of this path rings a
 * helpline staffed for people in crisis; the number is asserted as *shown*,
 * and connecting it stays a human decision.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CrisisPathDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var restoreGuest: Boolean? = null

    @Before
    fun theDeviceMustBeUnlocked() {
        // Same precondition as DeviceSmokeTest, and for the same reason: a
        // keyguard blocks every activity launch, and without this the suite
        // hangs instead of saying so.
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            "the device is locked - unlock it and re-run",
            keyguard?.isKeyguardLocked == true,
        )
        reduceMotionOverrideForTests = true

        // The deeplink is consumed by the signed-in NavHost, so the shell has
        // to exist before `cerebro://crisis` can land anywhere. A session that
        // is already signed in (a developer's handset) is left exactly as it
        // is; only a device with no session at all is nudged into guest mode,
        // and that flag is put back afterwards. Nothing here signs anyone out.
        Session.init(context)
        if (!Session.signedIn && !Session.guestMode) {
            restoreGuest = false
            Session.continueAsGuest(context)
        }
    }

    @After
    fun restore() {
        reduceMotionOverrideForTests = null
        // Same file and key `Session.continueAsGuest` writes ("cerebro" /
        // "guest_mode"); there is no exit-guest path to call, and leaving a
        // developer's handset flagged as a guest is the test changing the
        // device rather than reading it.
        restoreGuest?.let { previous ->
            context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
                .edit().putBoolean("guest_mode", previous).apply()
            Session.guestMode = previous
        }
    }

    private fun launchCrisis(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("cerebro://crisis")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )

    /** The region this handset would actually resolve, by the app's own rules. */
    private fun region(): String = effectiveRegion(
        runCatching { Session.prefGet("crisis_region") }.getOrNull(),
        deviceCrisisCountry(context),
    )

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun the_crisis_deeplink_shows_the_line_this_hardware_resolves_to() {
        val expected = primaryCrisisLine(region())
        val name = context.getString(expected.nameRes)
        launchCrisis().use {
            awaitText(name)
            // The number, not just the name: the name is copy, the number is
            // what someone dials, and it is the half that was wrong before.
            awaitText(expected.target)
        }
    }

    @Test
    fun the_mental_health_line_leads_the_action_cards_that_render_it() {
        val regional = crisisLinesFor(region())
        val mental = primaryCrisisLine(region())
        val emergency = regional.firstOrNull {
            it.target in setOf("112", "911", "999", "000", "111")
        } ?: regional.first()
        // A region whose emergency number *is* its crisis line has nothing to
        // order; the assertion would be vacuous rather than wrong.
        if (mental.target == emergency.target) return

        // Compared by card TITLE, not by the numbers. Written against the
        // numbers first, this failed at emergency=245px — which turned out to
        // be the immediate-danger banner, above every card *on purpose* ("the
        // immediate-danger case is not demoted: the banner above answers it
        // before any card, and is now dialable itself"). The contract is about
        // which line leads the actions someone chooses between, so the banner
        // is not a counter-example and must not be read as one.
        val mentalCard = context.getString(R.string.crisis_call_line, context.getString(mental.nameRes))
        val emergencyCard = context.getString(R.string.crisis_call_emergency)

        launchCrisis().use {
            awaitText(mentalCard)
            awaitText(emergencyCard)
            val mentalTop = compose.onAllNodesWithText(mentalCard, substring = true)
                .fetchSemanticsNodes().first().boundsInRoot.top
            val emergencyTop = compose.onAllNodesWithText(emergencyCard, substring = true)
                .fetchSemanticsNodes().first().boundsInRoot.top
            assertTrue(
                "the emergency card sits above the mental-health line on the rendered " +
                    "screen (mental at ${mentalTop}px, emergency at ${emergencyTop}px) - " +
                    "REDESIGN 2.3 puts the mental-health line first on every crisis surface",
                mentalTop < emergencyTop,
            )
        }
    }
}
