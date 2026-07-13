package com.cerebrozen.app.net

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Exercises the REAL HttpURLConnection transports (realHttp / realSse / realBin)
 * against a loopback socket server — hermetic (no external network), but the
 * genuine socket code path, including the PATCH allow-list workaround
 * (Android's HttpURLConnection rejects PATCH; the test JVM's does too, so the
 * same reflective fallback is exactly what makes this test pass — enabled by
 * the --add-opens java.base/java.net flag in build.gradle.kts).
 *
 * realSse/realBin are reached through their seam vars (resetForTest restores
 * the real implementations); realHttp — whose seam is replaced by the fake —
 * is invoked directly via kotlin-reflect. The server is hand-rolled over
 * ServerSocket because unit tests compile against android.jar, which has no
 * com.sun.net.httpserver (and the app philosophy is zero extra SDKs).
 */
class SessionTransportTest {

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    /** Minimal HTTP/1.1 server: one canned (status, body) response per request,
     * `Connection: close`, records everything it saw. */
    private class MiniServer {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val requests = CopyOnWriteArrayList<Request>()
        var respond: (Request) -> Pair<Int, ByteArray> = { _ -> 200 to "{}".toByteArray() }
        val base get() = "http://127.0.0.1:${socket.localPort}"

        private val acceptor = thread(isDaemon = true) {
            runCatching {
                while (true) {
                    val client = socket.accept()
                    thread(isDaemon = true) {
                        client.use {
                            val req = parse(it.getInputStream()) ?: return@use
                            requests.add(req)
                            val (status, body) = respond(req)
                            val head = "HTTP/1.1 $status X\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            it.getOutputStream().apply {
                                write(head.toByteArray()); write(body); flush()
                            }
                        }
                    }
                }
            }
        }

        private fun parse(input: InputStream): Request? {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return null
                head.append(b.toChar())
            }
            val lines = head.toString().split("\r\n").filter { it.isNotBlank() }
            val (method, path, _) = lines[0].split(" ")
            val headers = lines.drop(1).associate {
                it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim()
            }
            val len = headers["content-length"]?.toInt() ?: 0
            val body = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(body, read, len - read)
                if (n < 0) break
                read += n
            }
            return Request(method, path, headers, body)
        }

        fun stop() {
            runCatching { socket.close() }
            runCatching { acceptor.interrupt() }
        }
    }

    private lateinit var server: MiniServer

    private class NullStore : Session.Store {
        override fun getString(key: String): String? = null
        override fun putString(key: String, value: String) {}
        override fun remove(key: String) {}
        override fun keys(): Set<String> = emptySet()
    }

    @Before
    fun startServer() {
        server = MiniServer()
        // Installs a no-op http fake AND restores the real sseExec/binExec.
        Session.resetForTest(NullStore()) { _, _, _, _, _ -> 200 to "{}" }
    }

    @After
    fun stopServer() {
        server.stop()
    }

    /** The private suspend realHttp(url, method, body, contentType, auth). */
    private fun realHttp(
        url: String, method: String, body: String?, contentType: String?, auth: String?,
    ): Pair<Int, String> = runBlocking {
        val fn = Session::class.declaredMemberFunctions.first { it.name == "realHttp" }
        fn.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        fn.callSuspend(Session, url, method, body, contentType, auth) as Pair<Int, String>
    }

    // ── realHttp ───────────────────────────────────────────────────────

    @Test
    fun realHttp_get_carries_the_bearer_token_and_reads_the_body() {
        server.respond = { _ -> 200 to """{"ok":1}""".toByteArray() }
        val (code, text) = realHttp("${server.base}/ok", "GET", null, null, "tok-1")
        assertEquals(200, code)
        assertEquals("""{"ok":1}""", text)
        val req = server.requests.single()
        assertEquals("GET", req.method)
        assertEquals("/ok", req.path)
        assertEquals("Bearer tok-1", req.headers["authorization"])
    }

    @Test
    fun realHttp_post_sends_body_and_content_type_and_no_auth_when_null() {
        server.respond = { _ -> 201 to "created".toByteArray() }
        val (code, text) = realHttp("${server.base}/post", "POST", """{"a":1}""", "application/json", null)
        assertEquals(201, code)
        assertEquals("created", text)
        val req = server.requests.single()
        assertEquals("POST", req.method)
        assertEquals("""{"a":1}""", req.body.decodeToString())
        assertEquals("application/json", req.headers["content-type"])
        assertNull("no token → no Authorization header", req.headers["authorization"])
    }

    @Test
    fun realHttp_reads_the_error_stream_on_4xx() {
        server.respond = { _ -> 404 to """{"detail":"missing"}""".toByteArray() }
        val (code, text) = realHttp("${server.base}/nope", "GET", null, null, null)
        assertEquals(404, code)
        assertTrue(text.contains("missing"))
    }

    @Test
    fun realHttp_patch_survives_the_method_allow_list() {
        server.respond = { _ -> 200 to "{}".toByteArray() }
        val (code, _) = realHttp("${server.base}/patch", "PATCH", """{"done":true}""", "application/json", "t")
        assertEquals(200, code)
        assertEquals("the reflective fallback must put a real PATCH on the wire",
            "PATCH", server.requests.single().method)
    }

    // ── realSse ────────────────────────────────────────────────────────

    @Test
    fun realSse_streams_frames_and_close_disconnects() = runBlocking {
        val frames = "data: {\"type\":\"token\",\"text\":\"hi\"}\n\ndata: {\"type\":\"done\"}\n\n"
        server.respond = { _ -> 200 to frames.toByteArray() }
        val (code, stream) = Session.sseExec("${server.base}/sse", """{"text":"hey"}""", "tok")
        assertEquals(200, code)
        val events = stream.bufferedReader().use { reader ->
            reader.readLines().mapNotNull(::parseSseLine)
        }
        assertEquals(listOf("token", "done"), events.map { it.getString("type") })
        val req = server.requests.single()
        assertEquals("POST", req.method)
        assertEquals("text/event-stream", req.headers["accept"])
        assertEquals("Bearer tok", req.headers["authorization"])
        assertTrue(req.body.decodeToString().contains("hey"))
    }

    @Test
    fun realSse_maps_a_bodyless_error_to_an_empty_stream() = runBlocking {
        server.respond = { _ -> 401 to ByteArray(0) }
        val (code, stream) = Session.sseExec("${server.base}/sse-fail", "{}", null)
        assertEquals(401, code)
        assertEquals("no error body → empty stream, not an NPE",
            "", stream.bufferedReader().use { it.readText() })
    }

    // ── realBin ────────────────────────────────────────────────────────

    @Test
    fun realBin_posts_bytes_and_returns_bytes() = runBlocking {
        server.respond = { req -> 200 to req.body.reversedArray() }
        val payload = byteArrayOf(1, 2, 3, 4)
        val (code, bytes) = Session.binExec("${server.base}/bin", "POST", payload, "application/octet-stream", "tok")
        assertEquals(200, code)
        assertTrue(bytes.contentEquals(byteArrayOf(4, 3, 2, 1)))
        val req = server.requests.single()
        assertEquals("application/octet-stream", req.headers["content-type"])
        assertEquals("Bearer tok", req.headers["authorization"])
    }

    @Test
    fun realBin_handles_bodyless_requests_and_error_responses() = runBlocking {
        server.respond = { _ -> 500 to """{"detail":"boom"}""".toByteArray() }
        val (code, bytes) = Session.binExec("${server.base}/bin-err", "GET", null, null, null)
        assertEquals(500, code)
        assertTrue(String(bytes).contains("boom"))
        assertEquals("no body → no Content-Type on the wire",
            null, server.requests.single().headers["content-type"])
    }
}
