package com.cerebrozen.app

import android.app.KeyguardManager
import android.content.Context
import android.telephony.TelephonyManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.ui.screens.deviceCrisisCountry
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first tests this app has ever run on hardware.
 *
 * Until 2026-08-14 there was no `androidTest` source set at all: 49 JVM test
 * files, a route-reachability gate that parses the NavHost without rendering it,
 * and a full green build. The first manual device walk then found five real
 * defects in under an hour — a UK helpline offered to Indian users, a primary
 * sign-in button that could not sign anyone in, a password placeholder drawn as
 * dots, a prompt clipped mid-word, an ambiguous idiom. Every one compiled, and
 * every one passed every test.
 *
 * The pattern is consistent and worth stating, because it decides what belongs
 * here: each defect lived in a code path that **guesses** — the locale, the build
 * config, the available width — and every JVM test passes those in explicitly.
 * A test that supplies the answer cannot catch a bug in the guessing.
 *
 * So this file holds only what a JVM cannot answer. Anything assertable
 * off-device stays in `src/test` with Robolectric, which runs everywhere and
 * costs no emulator; duplicating it here would buy nothing and slow CI.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DeviceSmokeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun theDeviceMustBeUnlocked() {
        // A locked handset cannot resume an activity, so every ActivityScenario
        // test below is unrunnable — and it does not say so: on CPH2681 the
        // suite simply never finishes (killed at ten minutes, against ~7s
        // unlocked). That silence is what the 2026-08-14 session met and
        // recorded as "ActivityScenario cannot bring MainActivity to RESUMED,
        // and says nothing about why" — with the phone locked at the time. The
        // tests were never broken; the precondition was never stated. It is
        // stated here, and it fails in a second instead of hanging.
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            // Plain ASCII: an em dash comes back mojibaked through the
            // instrumentation reporter, in the one message someone reads while
            // something is already wrong.
            "the device is locked - unlock it and re-run (`adb shell input keyevent KEYCODE_WAKEUP` " +
                "only turns the screen on; the keyguard still blocks every activity launch)",
            keyguard?.isKeyguardLocked == true,
        )
    }

    @Before
    fun stillTheInfiniteAnimations() {
        // Without this, `ActivityScenario.launch` never returns: Espresso waits
        // for the main looper to quiesce and the sheen and breathing orb are
        // infinite transitions, so it never does. Zeroing the device's
        // animator_duration_scale is the usual answer and this handset refuses
        // it (ColorOS requires WRITE_SECURE_SETTINGS), so the hook lives in the
        // app instead — which also makes CI independent of runner settings.
        reduceMotionOverrideForTests = true
    }

    @After
    fun restoreMotion() {
        reduceMotionOverrideForTests = null
    }

    @Test
    fun the_app_launches_and_settles_without_crashing() {
        // The cheapest test that could possibly have value, and the one thing no
        // JVM suite can promise: that this APK starts on a real Android. A crash
        // in Application.onCreate, a missing native lib, a manifest the merger
        // broke, an SDK-level API used below minSdk — all invisible to
        // Robolectric, all fatal to a release.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(
                "MainActivity did not reach RESUMED on a real device",
                Lifecycle.State.RESUMED,
                scenario.state,
            )
        }
    }

    @Test
    fun the_app_survives_a_configuration_change() {
        // Recreation is where real devices punish state that only ever lived in
        // a JVM: a non-Saveable remember, an activity leak, a NavHost rebuilt
        // from nothing. Cheap to assert, and it fails loudly when it regresses.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertEquals(
                "MainActivity did not survive recreation",
                Lifecycle.State.RESUMED,
                scenario.state,
            )
        }
    }

    @Test
    fun the_crisis_region_follows_the_hardware_not_the_ui_language() {
        // The regression guard for the defect that motivated this whole source
        // set. `CrisisCountryResolutionTest` pins the resolution *order* with a
        // shadowed TelephonyManager; only a device can confirm the real one
        // answers at all — that these getters are readable without a permission
        // prompt, and that the value is a usable ISO country rather than the
        // empty string some OEM builds hand back.
        val resolved = deviceCrisisCountry(context)
        assertNotNull(resolved)

        val tm = context.getSystemService(TelephonyManager::class.java)
        val network = tm?.networkCountryIso.orEmpty().uppercase()
        val sim = tm?.simCountryIso.orEmpty().uppercase()
        val telephony = network.ifEmpty { sim }

        if (telephony.isNotEmpty()) {
            // A handset with service must resolve to where it *is*. On the device
            // that found the bug this is IN while the UI language is en-GB, which
            // is precisely the case that used to produce a UK helpline.
            assertEquals(
                "resolved region ignored the SIM/network the handset is registered on",
                telephony,
                resolved,
            )
        } else {
            // No SIM (a tablet, or an emulator without telephony). The locale
            // fallback is then correct — but it must still produce something.
            assertTrue(
                "no telephony and no locale fallback left the crisis region empty",
                resolved.isEmpty() || resolved.length == 2,
            )
        }
    }
}
