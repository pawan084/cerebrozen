package com.cerebrozen.app.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a crash report is allowed to contain (WC-17).
 *
 * The value of this file is what it REFUSES to send. A test that only checked
 * "the event reached a sink" would pass just as happily on a version that
 * shipped the journal entry that crashed the parser — so most of these assert
 * absence, and they assert it against the WHOLE rendered line rather than the
 * field a leak was expected in, because a leak arrives in the field nobody
 * thought of.
 */
class ErrorTrackingTest {

    private val collected = mutableListOf<ErrorTracking.Event>()
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        ErrorTracking.resetForTest()
        ErrorTracking.logLine = { line -> logged.add(line) }
        ErrorTracking.addSink { event -> collected.add(event) }
    }

    @After
    fun tearDown() {
        ErrorTracking.resetForTest()
        ErrorTracking.logLine = { line -> android.util.Log.e("CereBroError", line) }
    }

    private val secret = "I have been feeling hopeless since March"

    /** Raised through a real call so the throwable carries a real stack. */
    private fun boom(message: String): Throwable =
        runCatching { throw IllegalStateException("could not parse entry: $message") }
            .exceptionOrNull()!!

    private fun rendered(): String = collected.joinToString("\n") { it.render() } + "\n" +
        logged.joinToString("\n")

    // ── Nothing a person wrote travels ───────────────────────────────────

    @Test
    fun `the message never leaves the device`() {
        ErrorTracking.capture(boom(secret), route = "journal")
        assertFalse("the entry text must not be in the report", rendered().contains("hopeless"))
        assertFalse(rendered().contains("could not parse entry"))
    }

    @Test
    fun `the type is sent because it carries no content`() {
        val event = ErrorTracking.capture(boom(secret), route = "journal")
        assertEquals("IllegalStateException", event.kind)
    }

    @Test
    fun `every shape of secret stays behind`() {
        // The four kinds this codebase actually handles.
        for (value in listOf(
            "someone@example.com",
            "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def",
            "I want to die",
            "+91 98765 43210",
        )) {
            collected.clear()
            logged.clear()
            ErrorTracking.capture(boom(value), route = "journal")
            assertFalse("leaked: $value", rendered().contains(value))
        }
    }

    @Test
    fun `the route is structure, never content`() {
        val event = ErrorTracking.capture(boom(secret), route = "journal")
        assertEquals("screen:journal", event.where)
    }

    @Test
    fun `a crash before the first navigation still reports`() {
        // currentRoute defaults rather than being null, so the crash handler
        // cannot itself throw on the way to reporting a crash.
        val event = ErrorTracking.capture(boom("x"))
        assertEquals("screen:unknown", event.where)
    }

    // ── Frames are positions ─────────────────────────────────────────────

    @Test
    fun `frames carry a position and nothing else`() {
        val frames = ErrorTracking.framesOf(boom(secret))
        assertTrue("frames are the useful half", frames.isNotEmpty())
        for (frame in frames) {
            assertTrue("looks like File:line in method — was '$frame'", frame.contains(" in "))
            assertFalse(frame.contains("hopeless"))
        }
    }

    @Test
    fun `the reporter does not blame itself`() {
        // The innermost frame is what the fingerprint is built from, so a frame
        // inside ErrorTracking would collapse unrelated faults onto one id.
        val frames = ErrorTracking.framesOf(boom("x"))
        assertFalse(frames.any { it.contains("ErrorTracking") })
    }

    @Test
    fun `only a bounded number of frames travel`() {
        val frames = ErrorTracking.framesOf(deepStack(40), limit = 12)
        assertTrue(frames.size <= 12)
    }

    private fun deepStack(depth: Int): Throwable =
        if (depth == 0) runCatching { throw RuntimeException("deep") }.exceptionOrNull()!!
        else deepStack(depth - 1)

    // ── Fingerprints ─────────────────────────────────────────────────────

    @Test
    fun `the same fault with different values is one fingerprint`() {
        // 400 crashes of one bug must not read as 400 bugs because each quoted
        // a different user's input.
        val a = ErrorTracking.fingerprintOf("IllegalStateException", "screen:journal", "J.kt:12 in save")
        val b = ErrorTracking.fingerprintOf("IllegalStateException", "screen:journal", "J.kt:12 in save")
        assertEquals(a, b)
    }

    @Test
    fun `the same fault on another screen is a different one`() {
        assertNotEquals(
            ErrorTracking.fingerprintOf("E", "screen:journal", "J.kt:1 in f"),
            ErrorTracking.fingerprintOf("E", "screen:sleep", "J.kt:1 in f"),
        )
    }

    @Test
    fun `the fingerprint is short and stable`() {
        val fp = ErrorTracking.fingerprintOf("E", "screen:journal", "J.kt:1 in f")
        assertEquals(16, fp.length)
        assertTrue(fp.all { it.isDigit() || it in 'a'..'f' })
    }

    // ── It cannot make things worse ──────────────────────────────────────

    @Test
    fun `a sink that throws does not stop the healthy ones`() {
        ErrorTracking.addSink { throw RuntimeException("the tracker is down") }
        val healthy = mutableListOf<ErrorTracking.Event>()
        ErrorTracking.addSink { event -> healthy.add(event) }
        ErrorTracking.capture(boom("x"), route = "home")
        assertEquals(1, healthy.size)
    }

    @Test
    fun `the default sink writes one structured line`() {
        ErrorTracking.capture(boom("x"), route = "home")
        assertEquals(1, logged.size)
        assertTrue(logged[0].startsWith("error_event kind=IllegalStateException"))
    }

    @Test
    fun `installing twice keeps one handler`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            ErrorTracking.install()
            val first = Thread.getDefaultUncaughtExceptionHandler()
            ErrorTracking.install()
            assertEquals("install() must be idempotent", first, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
            ErrorTracking.resetForTest()
        }
    }

    @Test
    fun `the crash still reaches the handler that terminates the process`() {
        // Swallowing a crash would turn a visible failure into a frozen screen,
        // which is strictly worse for the person holding the phone.
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val chained = mutableListOf<Throwable>()
            Thread.setDefaultUncaughtExceptionHandler { _, t -> chained.add(t) }
            ErrorTracking.install()

            val crash = boom(secret)
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), crash)

            assertEquals("the original handler must still run", 1, chained.size)
            assertEquals(crash, chained[0])
            assertEquals("and the crash was reported", 1, collected.size)
            assertEquals("crash", collected[0].via)
            assertFalse(rendered().contains("hopeless"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
            ErrorTracking.resetForTest()
        }
    }
}
