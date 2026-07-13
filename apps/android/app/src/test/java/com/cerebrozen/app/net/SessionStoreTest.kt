package com.cerebrozen.app.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage of Session's Android-facing persistence (init/buildStore
 * with the keystore fallback, the SharedPreferences-backed store) and of the
 * DEBUG response logging with its secret redaction — android.util.Log works
 * under Robolectric, so the full redact path executes instead of bailing at
 * the first Log.d like it does under the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun init_builds_a_persistent_store_and_reads_the_session_flag() {
        Session.init(context)
        assertFalse("no refresh token yet", Session.signedIn)

        // The store persists across re-inits (same prefs file underneath) —
        // exercises SharedPrefsStore.putString/getString via the pref seam.
        Session.prefPut("theme_mode", "dawn")
        assertEquals("dawn", Session.prefGet("theme_mode"))
        Session.prefPut("refresh_token", "r-persisted")
        Session.prefPut("cache:/moods", """[{"mood":"calm"}]""")
        Session.init(context)
        assertTrue("an existing refresh token must restore the session", Session.signedIn)

        // signOut wipes the token and every cache: entry (keys()+remove covered).
        Session.signOut()
        assertFalse(Session.signedIn)
        assertNull(Session.prefGet("refresh_token"))
        assertNull("privacy: sign-out clears the offline cache", Session.prefGet("cache:/moods"))
        assertEquals("non-cache prefs survive sign-out", "dawn", Session.prefGet("theme_mode"))
    }

    @Test
    fun debug_logging_redacts_secrets_in_objects_arrays_and_survives_junk() = runTest {
        val store = object : Session.Store {
            val m = mutableMapOf("refresh_token" to "r1")
            override fun getString(key: String) = m[key]
            override fun putString(key: String, value: String) { m[key] = value }
            override fun remove(key: String) { m.remove(key) }
            override fun keys() = m.keys.toSet()
        }
        var body = ""
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) {
                200 to """{"access_token":"a1","refresh_token":"r1"}"""
            } else {
                200 to body
            }
        }
        // Nested objects + arrays with sensitive keys at every level: the logger
        // must walk all of it without disturbing the returned payload.
        body = """{"password":"p","profile":{"id_token":"x","tags":["a",{"token":"t"},[1,2]]},"n":7}"""
        assertEquals(body, Session.api("/nested"))
        // Top-level array.
        body = """[{"authorization":"h"},"plain",3]"""
        assertEquals(body, Session.api("/array"))
        // Non-JSON, malformed JSON, and blank bodies must never break a request.
        body = "plain text"
        assertEquals(body, Session.api("/text"))
        body = "{not json"
        assertEquals(body, Session.api("/broken"))
        body = ""
        assertEquals("", Session.api("/empty"))
    }

    @Test
    fun the_default_memory_store_supports_the_full_store_contract() {
        // MemoryStore backs Session before init() runs (a pure in-JVM map).
        // It's private — reached the same way the JVM does, via reflection.
        val cls = Class.forName("com.cerebrozen.app.net.Session\$MemoryStore")
        val ctor = cls.getDeclaredConstructor().apply { isAccessible = true }
        val store = ctor.newInstance() as Session.Store
        assertNull(store.getString("k"))
        store.putString("k", "v")
        assertEquals("v", store.getString("k"))
        assertEquals(setOf("k"), store.keys())
        store.remove("k")
        assertNull(store.getString("k"))
        assertTrue(store.keys().isEmpty())
    }
}
