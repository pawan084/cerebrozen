package com.cerebrozen.app.net

import java.security.MessageDigest

/**
 * Structured error tracking for the Android client (WC-17).
 *
 * The port of `backend/app/services/errors.py` and `apps/app/lib/errors.ts`,
 * deliberately the same contract rather than a third one — a fingerprint
 * computed differently per client is three incident counts that cannot be
 * added together.
 *
 * Before this there was no uncaught-exception handler at all: a crash was a
 * logcat trace on one device, plus whatever Play collects — which includes the
 * exception's full message, and on this product a message can quote what
 * somebody wrote about their own mind.
 *
 * ## The policy is an allow-list, and it is the whole design
 *
 * Nothing is scrubbed OUT of a rich context; a fixed set of fields is copied
 * IN. A deny-list fails open on the field nobody thought of. [Session] already
 * keeps a `SENSITIVE_KEYS` set for masking DEBUG response logs, and that is
 * exactly the pattern this avoids depending on: it can only mask the keys it
 * knows, whereas a crash inside a journal parser puts the entry itself in the
 * message under no key at all.
 *
 * So a report carries the throwable's **class name** and never its message,
 * stack frames as **positions only** (`File.kt:120 in method`), the **route**
 * the user was on rather than any of its content, and no user, no preference,
 * no request body.
 *
 * ## Why no vendor
 *
 * Same as the other two clients: where an error sink lands is a DPDP transfer
 * and retention question before it is a pricing one, and that is the owner's
 * call. The seam is [addSink]; the default writes one structured line to
 * logcat, which is what a tester's bug report actually carries today.
 */
object ErrorTracking {

    /** Everything allowed to leave the device about one failure. */
    data class Event(
        /** The throwable's simple class name. Never its message. */
        val kind: String,
        /** Stable across occurrences of the same fault, so they can be counted. */
        val fingerprint: String,
        /** `screen:<route>` — where it happened, with no content from it. */
        val where: String,
        /** How it arrived: the crash handler, or a caught-and-reported failure. */
        val via: String,
        /** `File.kt:line in method`, innermost last. No locals, no arguments. */
        val frames: List<String>,
    ) {
        /** The single structured line a sink writes. Built by hand rather than
         *  reflected from the data class, because `toString()` on a data class
         *  would happily print any field a future edit adds. */
        fun render(): String = buildString {
            append("error_event kind=").append(kind)
            append(" fingerprint=").append(fingerprint)
            append(" where=").append(where)
            append(" via=").append(via)
            append(" frames=").append(frames.joinToString("|"))
        }
    }

    fun interface Sink {
        fun send(event: Event)
    }

    /** The always-on sink. Replaced wholesale in tests via [resetSinks]. */
    private val logSink = Sink { event -> logLine(event.render()) }

    /** Seam so a unit test can read what logcat would have shown. */
    internal var logLine: (String) -> Unit = { line -> android.util.Log.e("CereBroError", line) }

    private val sinks = mutableListOf<Sink>(logSink)

    fun addSink(sink: Sink) {
        synchronized(sinks) { sinks.add(sink) }
    }

    fun resetSinks() {
        synchronized(sinks) {
            sinks.clear()
            sinks.add(logSink)
        }
    }

    /**
     * The route the user is on, set by the nav host as it changes.
     *
     * A route name and nothing else: "journal" is where a crash happened,
     * whereas the entry open on that screen is the thing that must not travel.
     * Defaults to `unknown` so a crash before the first navigation still
     * reports rather than throwing inside the crash handler.
     */
    @Volatile
    var currentRoute: String = "unknown"

    /**
     * Install the process-wide crash handler. Idempotent.
     *
     * Chains to whatever handler was already there — Android's default, which
     * is what actually terminates the process and files the Play report. A
     * tracker that swallowed the crash would turn a visible failure into a
     * frozen screen, which is strictly worse for the person holding the phone.
     */
    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { capture(throwable, via = "crash") }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @Volatile
    private var installed = false

    /** For tests: forget that install() ran. */
    internal fun resetForTest() {
        installed = false
        currentRoute = "unknown"
        resetSinks()
    }

    /**
     * Scrub, fingerprint and dispatch one failure. Never throws.
     *
     * A crash inside the crash reporter must not become the crash: every sink
     * is called defensively, because the one moment this runs is the moment
     * the process is already having a bad time.
     */
    fun capture(throwable: Throwable, via: String = "caught", route: String = currentRoute): Event {
        val kind = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
        val frames = framesOf(throwable)
        val where = "screen:$route"
        val event = Event(
            kind = kind,
            fingerprint = fingerprintOf(kind, where, frames.lastOrNull().orEmpty()),
            where = where,
            via = via,
            frames = frames,
        )
        val snapshot = synchronized(sinks) { sinks.toList() }
        for (sink in snapshot) {
            runCatching { sink.send(event) }
        }
        return event
    }

    /**
     * `File.kt:line in method`, innermost last, capped.
     *
     * Only the frame's POSITION. A helpful reporter that also captured
     * arguments or locals would capture the journal entry that crashed the
     * parser — the same reason the backend reads `extract_tb` rather than
     * frame objects.
     *
     * Frames from this file are dropped: the reporter blaming itself is noise
     * at the innermost position, which is exactly the position the fingerprint
     * is built from.
     */
    internal fun framesOf(throwable: Throwable, limit: Int = 12): List<String> =
        throwable.stackTrace
            .filterNot { it.className.startsWith("com.cerebrozen.app.net.ErrorTracking") }
            .take(limit)
            .map { frame ->
                val file = frame.fileName ?: frame.className.substringAfterLast('.')
                "$file:${frame.lineNumber} in ${frame.methodName}"
            }
            .asReversed()

    /**
     * Group occurrences of the same fault.
     *
     * Built from the type, the route and the innermost frame — never the
     * message, which usually contains the offending value and would therefore
     * split one recurring bug into a thousand singletons AND carry that value
     * into the grouping key. Same three inputs as the backend and the web
     * client, so the three counts describe the same shape of thing.
     */
    internal fun fingerprintOf(kind: String, where: String, innermost: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind|$where|$innermost".toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
