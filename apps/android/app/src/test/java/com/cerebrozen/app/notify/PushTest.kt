package com.cerebrozen.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.net.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Remote nudges.
 *
 * The load-bearing claim in this file is the dormant one: with no Firebase
 * project configured — which is the state this app ships in — nothing here may
 * touch the network, throw, or offer the user a toggle that does nothing.
 */
@RunWith(RobolectricTestRunner::class)
class PushTest {

    private class FakeStore : Session.Store {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys() = map.keys.toSet()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var store: FakeStore
    private val calls = mutableListOf<String>()

    @Before
    fun setUp() {
        store = FakeStore().apply { map["refresh_token"] = "r1" }
        calls.clear()
        Session.resetForTest(store) { url, _, _, _, _, _ ->
            calls += url
            if (url.endsWith("/auth/refresh")) 200 to """{"access_token":"a","refresh_token":"r"}"""
            else 200 to """{"id":"d1","platform":"android","app_version":"0.1.0",""" +
                """"last_seen_at":"2026-08-01T00:00:00Z","created_at":"2026-08-01T00:00:00Z"}"""
        }
        // Real seams first, so `available()` runs the production probe rather
        // than a copy of it in this file that could quietly drift from it.
        Push.resetSeamsForTest()
        Push.tokenProvider = { "fcm-token-1" }
    }

    /** Pretend a google-services.json was dropped in, so the configured path is
     * reachable on a machine with no Firebase project. */
    private fun asConfiguredBuild() {
        Push.availabilityProbe = { true }
    }

    // ── The configured path ──────────────────────────────────────────────
    @Test
    fun a_configured_build_registers_this_install() = runTest {
        asConfiguredBuild()

        Push.register(context)

        assertTrue(calls.any { it.endsWith("/users/me/devices") })
        assertEquals(
            "the token is remembered so sign-out can unregister exactly this install",
            "fcm-token-1", store.map["fcm_token"],
        )
    }

    @Test
    fun a_null_token_registers_nothing() = runTest {
        asConfiguredBuild()
        Push.tokenProvider = { null }

        Push.register(context)

        assertTrue(calls.none { it.endsWith("/users/me/devices") })
        assertNull(store.map["fcm_token"])
    }

    @Test
    fun a_failed_registration_does_not_claim_success() = runTest {
        asConfiguredBuild()
        Session.resetForTest(store) { _, _, _, _, _, _ -> throw java.io.IOException("offline") }

        Push.register(context)   // must not throw

        assertNull(
            "storing the token after a failed register would skip the retry next launch",
            store.map["fcm_token"],
        )
    }

    // ── Dormant without Firebase ─────────────────────────────────────────
    @Test
    fun push_is_unavailable_without_a_firebase_config() {
        assertFalse(
            "the app ships with no google-services.json; claiming push works would be a dead toggle",
            Push.available(context),
        )
    }

    @Test
    fun registration_is_skipped_when_push_cannot_work() = runTest {
        Push.register(context)

        assertTrue("an unconfigured build must not call the backend", calls.isEmpty())
    }

    @Test
    fun registration_is_skipped_when_signed_out() = runTest {
        Session.resetForTest(FakeStore()) { url, _, _, _, _, _ -> calls += url; 200 to "{}" }

        Push.register(context)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun a_token_fetch_that_throws_does_not_break_app_start() = runTest {
        Push.tokenProvider = { throw IllegalStateException("FirebaseApp is not initialized") }

        Push.register(context)   // must not throw

        assertTrue(calls.isEmpty())
    }

    // ── Unregistering ────────────────────────────────────────────────────
    @Test
    fun unregister_with_no_stored_token_is_a_no_op() = runTest {
        Push.unregister()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun sign_out_drops_the_token_so_the_next_user_is_not_nudged() = runTest {
        store.map["fcm_token"] = "fcm-token-1"

        Push.unregister()

        assertTrue("the server must be told", calls.any { it.contains("/users/me/devices") })
        assertNull("and the local copy must go too", store.map["fcm_token"])
    }

    @Test
    fun unregister_forgets_the_token_even_if_the_server_call_fails() = runTest {
        store.map["fcm_token"] = "fcm-token-1"
        Session.resetForTest(store) { _, _, _, _, _, _ -> throw java.io.IOException("offline") }

        Push.unregister()

        assertNull(
            "keeping a token we failed to unregister would re-register it next launch",
            store.map["fcm_token"],
        )
    }

    // ── Rendering a nudge ────────────────────────────────────────────────
    private fun posted(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .allNotifications

    @Test
    fun a_nudge_becomes_a_notification_on_its_own_channel() {
        Push.show(context, mapOf("title" to "Wind down", "body" to "3-minute reset", "kind" to "reminder"))

        val notification = posted().single()
        assertEquals(Push.CHANNEL_ID, notification.channelId)
        assertEquals("Wind down", notification.extras.getString(Notification.EXTRA_TITLE))
        assertNotNull("tapping it must open the app", notification.contentIntent)
    }

    @Test
    fun the_channel_is_default_importance_not_an_alarm() {
        Push.ensureChannel(context)

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(Push.CHANNEL_ID)
        assertEquals(
            "a heads-up banner at 21:00 is the opposite of winding down",
            NotificationManager.IMPORTANCE_DEFAULT, channel.importance,
        )
    }

    @Test
    fun a_payload_with_no_title_posts_nothing() {
        Push.show(context, mapOf("body" to "orphaned body"))

        assertTrue("an empty notification row is worse than none", posted().isEmpty())
    }

    @Test
    fun a_second_nudge_of_the_same_kind_replaces_the_first() {
        assertEquals(Push.notifyId("reminder"), Push.notifyId("reminder"))
    }

    @Test
    fun different_kinds_get_different_slots() {
        assertTrue(
            "a check-in nudge must not silently replace a safety one",
            Push.notifyId("checkin") != Push.notifyId("safety"),
        )
    }

    @Test
    fun a_missing_kind_still_lands_somewhere_stable() {
        assertEquals(Push.notifyId(null), Push.notifyId(null))
    }
}
