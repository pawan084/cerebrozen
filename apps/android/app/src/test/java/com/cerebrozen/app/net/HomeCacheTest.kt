package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's shared, warmed-once data (HOME_SPEC #11-#16): [HomeCache.warm] fetches Home's four
 * data points CONCURRENTLY and best-effort, [HomeCache.failed] is true only when every one of
 * the three primary calls failed, and [HomeCache.markCheckedInToday] gives an optimistic local
 * update the moment a check-in succeeds, without waiting for the next real warm().
 */
class HomeCacheTest {

    private val store = object : Session.Store {
        val m = mutableMapOf("refresh_token" to "r1")
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    // access is null right after resetForTest, so the FIRST call any test makes triggers an
    // automatic refresh (Session.api()'s standard 401-then-retry path) before it reaches this
    // fake responder — every fixture below must answer /auth/refresh, exactly like a real
    // token issuer would.
    private val refresh = """{"access_token":"a1","refresh_token":"r1"}"""

    @Before
    fun setUp() {
        HomeCache.clear()
    }

    @After
    fun tearDown() {
        HomeCache.clear()
    }

    @Test
    fun warm_populates_every_field_on_success() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to refresh
                url.endsWith("/users/me") -> 200 to """{"name":"Nova"}"""
                url.endsWith("/v1/wellness/moods") -> 200 to """[{"ts":"2026-07-20T10:00:00+00:00"}]"""
                url.endsWith("/users/me/streak") -> 200 to """{"current":4}"""
                url.endsWith("/programs/active") -> 200 to """{"program":{"day":3,"days":7,"title":"Better Sleep"}}"""
                url.endsWith("/v1/sessions/resumable") -> 200 to """{"resumable":true}"""
                else -> 200 to "{}"
            }
        }
        HomeCache.warm()
        assertEquals("Nova", HomeCache.name)
        assertEquals(1, HomeCache.moods?.length())
        assertEquals(4, HomeCache.streak)
        assertEquals("Better Sleep", HomeCache.activeProgram?.optString("title"))
        assertTrue(HomeCache.resumable)
        assertFalse("a fully successful warm must not report failed", HomeCache.failed)
    }

    @Test
    fun warm_reports_failed_only_when_every_primary_call_fails() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to refresh
                url.endsWith("/programs/active") || url.endsWith("/v1/sessions/resumable") -> 200 to "{}"
                else -> 404 to """{"detail":"nope"}"""
            }
        }
        HomeCache.warm()
        assertTrue("me/moods/streak all failed — this IS the nothing-to-show floor", HomeCache.failed)
        assertNull(HomeCache.name)
    }

    @Test
    fun warm_is_not_failed_when_only_some_calls_fail() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to refresh
                url.endsWith("/users/me") -> 200 to """{"name":"Nova"}"""
                else -> 404 to """{"detail":"nope"}"""
            }
        }
        HomeCache.warm()
        assertFalse("one working endpoint is still something to show", HomeCache.failed)
        assertEquals("Nova", HomeCache.name)
        assertNull(HomeCache.moods)
    }

    @Test
    fun clear_resets_every_field() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to refresh
                url.endsWith("/users/me") -> 200 to """{"name":"Nova"}"""
                url.endsWith("/v1/wellness/moods") -> 200 to """[{"ts":"2026-07-20T10:00:00+00:00"}]"""
                url.endsWith("/users/me/streak") -> 200 to """{"current":4}"""
                else -> 200 to "{}"
            }
        }
        HomeCache.warm()
        HomeCache.clear()
        assertNull(HomeCache.name)
        assertNull(HomeCache.moods)
        assertNull(HomeCache.streak)
        assertNull(HomeCache.activeProgram)
        assertFalse(HomeCache.resumable)
        assertFalse(HomeCache.failed)
    }

    @Test
    fun markCheckedInToday_appends_to_whatever_moods_already_holds() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to refresh
                url.endsWith("/v1/wellness/moods") -> 200 to """[{"ts":"2026-07-19T10:00:00+00:00"}]"""
                else -> 200 to "{}"
            }
        }
        HomeCache.warm()
        assertEquals(1, HomeCache.moods?.length())

        HomeCache.markCheckedInToday()

        assertEquals("the existing entry survives the optimistic append", 2, HomeCache.moods?.length())
    }

    @Test
    fun markCheckedInToday_works_even_when_moods_was_never_warmed() = runTest {
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to refresh else 200 to "{}" // moods stays null
        }
        HomeCache.warm()
        assertNull(HomeCache.moods)

        HomeCache.markCheckedInToday()

        assertEquals(1, HomeCache.moods?.length())
    }
}
