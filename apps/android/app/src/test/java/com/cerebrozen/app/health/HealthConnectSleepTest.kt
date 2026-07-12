package com.cerebrozen.app.health

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Health Connect prefill must DEGRADE CLEANLY — that is the whole contract
 * (mirrors iOS HealthKit: no provider, no permission, no data → the user just
 * types times by hand). Robolectric has no Health Connect provider, which makes
 * it exactly the environment the degrade paths are written for; installing a
 * fake provider package then exercises the available()/getOrCreate branch and
 * the runCatching guards around the real permission/read calls. The record →
 * minute-of-day math cannot run hermetically (readRecords needs the provider
 * IPC), so it stays uncovered — same reasoning as the excluded services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HealthConnectSleepTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun declares_exactly_the_sleep_read_permission() {
        assertEquals(1, HealthConnectSleep.permissions.size)
        assertTrue(HealthConnectSleep.permissions.first().contains("READ_SLEEP"))
    }

    @Test
    fun everything_degrades_when_health_connect_is_absent() = runBlocking {
        assertFalse("no provider installed → unavailable", HealthConnectSleep.available(context))
        assertFalse(HealthConnectSleep.hasPermission(context))
        assertNull("no provider → prefill silently yields nothing", HealthConnectSleep.readLastNight(context))
    }

    @Test
    fun a_present_provider_whose_ipc_fails_still_degrades() = runBlocking {
        // Install a fake Health Connect provider that satisfies getSdkStatus's
        // three checks (package enabled, versionCode >= the client minimum, a
        // service answering the bind action) — so available() flips true and
        // getOrCreate constructs a real client. The IPC behind it can never
        // connect under Robolectric, which is the point: the runCatching guards
        // must swallow the failure, never crash or phantom-grant.
        val provider = "com.google.android.apps.healthdata"
        val pi = PackageInfo().apply {
            packageName = provider
            @Suppress("DEPRECATION")
            versionCode = 2_000_000
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = provider
                enabled = true
            }
        }
        shadowOf(context.packageManager).installPackage(pi)
        val bind = android.content.Intent("androidx.health.ACTION_BIND_HEALTH_DATA_SERVICE")
            .setPackage(provider)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            bind,
            android.content.pm.ResolveInfo().apply {
                serviceInfo = android.content.pm.ServiceInfo().apply {
                    packageName = provider
                    name = "$provider.HealthDataService"
                    applicationInfo = pi.applicationInfo
                }
            },
        )
        assertTrue("the fake provider must satisfy the SDK availability probe",
            HealthConnectSleep.available(context))

        // The permission/read calls bind to an IPC that never connects: they must
        // degrade to false/null (the withTimeout guards the test, not the app —
        // a cancelled await surfaces as a caught exception inside runCatching).
        val has = withTimeoutOrNull(5_000) { HealthConnectSleep.hasPermission(context) }
        assertTrue("no crash and no phantom grant", has == null || has == false)
        val night = withTimeoutOrNull(5_000) { HealthConnectSleep.readLastNight(context) }
        assertNull(night)
    }
}
