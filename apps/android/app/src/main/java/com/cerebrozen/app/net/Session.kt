package com.cerebrozen.app.net

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cerebrozen.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal first-party API client — deliberately zero SDKs (HttpURLConnection +
 * org.json + coroutines). Token model mirrors apps/app/lib/api.ts: the ACCESS
 * token lives in memory only, the REFRESH token in EncryptedSharedPreferences,
 * and a 401/403 triggers one rotation retry before signing out. The GET response
 * cache (which holds mood/journal/chat data) is encrypted at rest too; if the
 * device keystore is unavailable we fall back to private SharedPreferences so
 * auth still works (no worse than a plain-text store).
 */
object Session {
    private const val PREFS = "cerebro"
    private const val REFRESH_KEY = "refresh_token"
    private const val LOG_TAG = "CereBroApi"
    // Keys whose values are masked in DEBUG response logs (never log secrets).
    private val SENSITIVE_KEYS = setOf(
        "access_token",
        "refresh_token",
        "id_token",
        "token",
        "password",
        "authorization",
    )

    /** Compose-observable auth state; gates the whole UI in CereBroApp. */
    var signedIn by mutableStateOf(false)
        private set

    /** Compose-observable offline signal: true while GET reads are being served
     * from the encrypted response cache (a network read failed and the last copy
     * stepped in). Flips false the moment any network GET succeeds, so surfaces
     * like the Home offline banner clear themselves when connectivity returns. */
    var servedStale by mutableStateOf(false)
        private set

    @Volatile private var access: String? = null
    private var storage: Store = MemoryStore()
    // Serialises token refresh so a burst of concurrent 401s (e.g. every Home read
    // firing at once on a cold launch, when access is null) coalesces onto a single
    // /auth/refresh instead of each presenting the same one-time-use refresh token.
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    /** Persistence seam — SharedPreferences in prod, a map in unit tests. */
    internal interface Store {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
        fun remove(key: String)
        fun keys(): Set<String>
    }

    /** HTTP transport seam — (url, method, body, contentType, authToken) →
     * (status, body); throws on a network failure. Swappable in unit tests. */
    internal var http: suspend (String, String, String?, String?, String?) -> Pair<Int, String> = ::realHttp

    fun init(context: Context) {
        storage = buildStore(context)
        signedIn = storage.getString(REFRESH_KEY) != null
    }

    /** Encrypted-at-rest prefs for the refresh token + response cache, with a
     * private-prefs fallback if the keystore can't init (some OEMs/emulators). */
    private fun buildStore(context: Context): Store {
        val ctx = context.applicationContext
        return runCatching {
            val masterKey = androidx.security.crypto.MasterKey.Builder(ctx)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                ctx,
                "cerebro_secure",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            SharedPrefsStore(prefs)
        }.getOrElse {
            SharedPrefsStore(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        }
    }

    /** Point Session at fakes for unit tests (no Android, no network). */
    internal fun resetForTest(
        store: Store,
        exec: suspend (String, String, String?, String?, String?) -> Pair<Int, String>,
    ) {
        storage = store
        http = exec
        sseExec = ::realSse
        binExec = ::realBin
        access = null
        servedStale = false
        signedIn = storage.getString(REFRESH_KEY) != null
    }

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun raw(
        path: String,
        method: String,
        body: String?,
        contentType: String?,
        authed: Boolean,
        base: String = BuildConfig.API_BASE_URL,
    ): String {
        val (code, text) = http(base + path, method, body, contentType, if (authed) access else null)
        logApiResponse(method, path, code, text)
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("detail") }
                .getOrNull().takeUnless { it.isNullOrBlank() } ?: "Request failed ($code)"
            throw ApiException(code, detail)
        }
        return text
    }

    /** DEBUG-only: log the response with sensitive fields masked. No-op in release.
     * Fully guarded — diagnostics must never be able to break the request path
     * (e.g. android.util.Log is unmocked and throws under plain JVM unit tests). */
    private fun logApiResponse(method: String, path: String, code: Int, text: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val body = redactJson(text).ifBlank { "<empty>" }
            Log.d(LOG_TAG, "$method $path -> $code")
            body.chunked(3_500).forEach { Log.d(LOG_TAG, it) }
        }
    }

    private fun redactJson(text: String): String {
        if (text.isBlank()) return text
        return runCatching {
            val trimmed = text.trim()
            when {
                trimmed.startsWith("{") -> redact(JSONObject(trimmed)).toString(2)
                trimmed.startsWith("[") -> redact(JSONArray(trimmed)).toString(2)
                else -> text
            }
            // Non-JSON that isn't a bare primitive: don't echo it raw (it could carry
            // an unredacted credential in a malformed error payload).
        }.getOrDefault("<unparseable body, ${text.length} bytes>")
    }

    private fun redact(obj: JSONObject): JSONObject {
        val copy = JSONObject()
        obj.keys().forEach { key ->
            val value = obj.get(key)
            copy.put(
                key,
                when {
                    key.lowercase() in SENSITIVE_KEYS -> "***"
                    value is JSONObject -> redact(value)
                    value is JSONArray -> redact(value)
                    else -> value
                },
            )
        }
        return copy
    }

    private fun redact(arr: JSONArray): JSONArray {
        val copy = JSONArray()
        for (i in 0 until arr.length()) {
            when (val value = arr.get(i)) {
                is JSONObject -> copy.put(redact(value))
                is JSONArray -> copy.put(redact(value))
                else -> copy.put(value)
            }
        }
        return copy
    }

    private suspend fun realHttp(
        url: String,
        method: String,
        body: String?,
        contentType: String?,
        authToken: String?,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            try {
                conn.requestMethod = method
            } catch (e: java.net.ProtocolException) {
                // Android's HttpURLConnection rejects PATCH via a fixed method
                // allow-list, and setRequestMethod() is final so the OkHttp-backed
                // impl can't override it — it reads the inherited java.net field.
                // Force that field directly (framework class, so R8 never strips it).
                runCatching {
                    HttpURLConnection::class.java.getDeclaredField("method")
                        .apply { isAccessible = true }
                        .set(conn, method)
                }.getOrElse { throw e }
            }
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            authToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private class MemoryStore : Store {
        private val m = mutableMapOf<String, String>()
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    private class SharedPrefsStore(private val p: SharedPreferences) : Store {
        override fun getString(key: String) = p.getString(key, null)
        override fun putString(key: String, value: String) { p.edit().putString(key, value).apply() }
        override fun remove(key: String) { p.edit().remove(key).apply() }
        override fun keys() = p.all.keys.toSet()
    }

    private fun store(tokens: JSONObject) {
        access = tokens.getString("access_token")
        storage.putString(REFRESH_KEY, tokens.getString("refresh_token"))
        signedIn = true
    }

    suspend fun signIn(email: String, password: String) {
        // OAuth2 password form: the field name is `username`.
        val form = "username=${enc(email)}&password=${enc(password)}"
        store(JSONObject(raw("/auth/login", "POST", form, "application/x-www-form-urlencoded", authed = false)))
    }

    suspend fun signUp(email: String, password: String, name: String) {
        val body = JSONObject().put("email", email).put("password", password).put("name", name)
        store(JSONObject(raw("/auth/signup", "POST", body.toString(), "application/json", authed = false)))
    }

    /** Email a 6-digit one-time sign-in code (passwordless). */
    suspend fun otpRequest(email: String) {
        raw("/auth/otp/request", "POST", JSONObject().put("email", email).toString(), "application/json", authed = false)
    }

    /** Exchange the emailed code for a session. */
    suspend fun otpVerify(email: String, code: String) {
        val body = JSONObject().put("email", email).put("code", code)
        store(JSONObject(raw("/auth/otp/verify", "POST", body.toString(), "application/json", authed = false)))
    }

    /** Exchange a Google ID token for a session (code-complete; inert until a
     * web client id is configured — mirrors the iOS "degrades gracefully" state). */
    suspend fun signInWithGoogle(idToken: String, name: String) {
        val body = JSONObject().put("id_token", idToken).put("name", name)
        store(JSONObject(raw("/auth/google", "POST", body.toString(), "application/json", authed = false)))
    }

    /** Email a password-reset link. The server always answers 200 (no account
     * enumeration); the reset itself completes on the web, same as iOS. */
    suspend fun forgotPassword(email: String) {
        raw("/auth/password/forgot", "POST", JSONObject().put("email", email).toString(), "application/json", authed = false)
    }

    /** Anonymous first-party product event (deliberately UNauthenticated so
     * rows can never join accounts — the /events contract; see Analytics). */
    internal suspend fun postEvent(anonId: String, name: String, step: String?) {
        val event = JSONObject().put("name", name)
        step?.let { event.put("step", it) }
        val body = JSONObject()
            .put("anon_id", anonId)
            .put("source", "android")
            .put("events", org.json.JSONArray().put(event))
        raw("/events", "POST", body.toString(), "application/json", authed = false)
    }

    // Small preference accessors on the same Store seam the tokens use —
    // lets Analytics persist its anon id / opt-out without touching Android
    // APIs directly (unit-testable via resetForTest).
    internal fun prefGet(key: String): String? = storage.getString(key)
    internal fun prefPut(key: String, value: String) { storage.putString(key, value) }

    // ── Oracle SSE ──────────────────────────────────────────────────────
    /** SSE transport seam — (url, jsonBody, authToken) → (status, byte stream).
     * Real impl streams over HttpURLConnection; tests feed ByteArrayInputStreams. */
    internal var sseExec: suspend (String, String, String?) -> Pair<Int, java.io.InputStream> = ::realSse

    private suspend fun realSse(url: String, body: String, authToken: String?): Pair<Int, java.io.InputStream> =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000   // LLM streams pause between tokens
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            authToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val raw = if (code in 200..299) conn.inputStream
                      else conn.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0))
            // Disconnect the socket when the reader closes the stream — including the
            // cancellation path (navigating away mid-stream) — so we don't hold the
            // connection open until the server closes it or the 120s timeout elapses.
            val stream = object : java.io.FilterInputStream(raw) {
                override fun close() {
                    try { super.close() } finally { conn.disconnect() }
                }
            }
            code to stream
        }

    // ── Binary transport (voice: multipart STT upload, MP3 TTS download) ──
    /** Bytes-in/bytes-out transport seam — (url, method, body, contentType,
     * authToken) → (status, bytes). Swappable in unit tests. */
    internal var binExec: suspend (String, String, ByteArray?, String?, String?) -> Pair<Int, ByteArray> = ::realBin

    private suspend fun realBin(
        url: String, method: String, body: ByteArray?, contentType: String?, authToken: String?,
    ): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000   // STT/TTS providers can take a few seconds
            authToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.readBytes() ?: ByteArray(0))
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun binRequest(path: String, body: ByteArray, contentType: String): ByteArray {
        ensureAccess()
        suspend fun attempt(): Pair<Int, ByteArray> =
            binExec(BuildConfig.API_BASE_URL + path, "POST", body, contentType, access)
        var (code, bytes) = attempt()
        if ((code == 401 || code == 403) && refresh()) { val r = attempt(); code = r.first; bytes = r.second }
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(String(bytes)).optString("detail") }
                .getOrNull().takeUnless { it.isNullOrBlank() } ?: "Request failed ($code)"
            throw ApiException(code, detail)
        }
        return bytes
    }

    /** Upload one file as multipart/form-data (the /voice/stt contract). */
    suspend fun upload(path: String, field: String, filename: String, mime: String, bytes: ByteArray): String {
        val boundary = "cerebro-${System.nanoTime()}"
        val body = multipartBody(boundary, field, filename, mime, bytes)
        return String(binRequest(path, body, "multipart/form-data; boundary=$boundary"))
    }

    /** JSON POST returning raw bytes (the /voice/tts MP3 contract). */
    suspend fun postForBytes(path: String, json: JSONObject): ByteArray =
        binRequest(path, json.toString().toByteArray(), "application/json")

    /** POST an SSE endpoint and invoke [onEvent] for every `data:` JSON frame.
     * Same auth semantics as [api]: fresh-launch refresh + one rotation retry. */
    suspend fun sse(
        path: String,
        json: JSONObject,
        base: String = BuildConfig.API_BASE_URL,
        onEvent: (JSONObject) -> Unit,
    ) {
        ensureAccess()
        var detail = ""
        suspend fun attempt(): Int {
            val (code, stream) = sseExec(base + path, json.toString(), access)
            withContext(Dispatchers.IO) {
                val ctx = coroutineContext
                stream.bufferedReader().use { reader ->
                    if (code in 200..299) {
                        reader.forEachLine { line ->
                            // Stop promptly (and close the socket via use{}) if the
                            // caller's scope was cancelled — e.g. left the Talk screen.
                            ctx.ensureActive()
                            parseSseLine(line)?.let(onEvent)
                        }
                    } else {
                        detail = runCatching { JSONObject(reader.readText()).optString("detail") }
                            .getOrNull().orEmpty()
                    }
                }
            }
            return code
        }
        var code = attempt()
        if ((code == 401 || code == 403) && refresh()) code = attempt()
        if (code !in 200..299) throw ApiException(code, detail.ifBlank { "Request failed ($code)" })
    }

    /** Rotate the token pair. Returns false on failure, but only a real auth
     * rejection (401/403) ends the session — a network blip must not sign the
     * user out (it would also wipe the offline cache on a cold, offline launch). */
    private suspend fun refresh(): Boolean {
        val seen = access
        return refreshMutex.withLock {
            // Coalesce: if another coroutine already rotated while we waited for the
            // lock, use its fresh token rather than refreshing again — a second call
            // would present an already-consumed rotating refresh token and wrongly
            // sign the (still-valid) session out.
            if (access != null && access != seen) return@withLock true
            val token = storage.getString(REFRESH_KEY) ?: return@withLock false
            try {
                val body = JSONObject().put("refresh_token", token)
                store(JSONObject(raw("/auth/refresh", "POST", body.toString(), "application/json", authed = false)))
                true
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) signOut()
                false
            } catch (_: Exception) {
                false  // network/offline — keep the session so cached reads can serve
            }
        }
    }

    /** Ensure a usable access token, refreshing on a cold start. Distinguishes a
     * real sign-out (refresh rejected → session ended) from a transient network
     * failure (session kept) so writes surface an honest message, not a scary
     * "Signed out" when the server was merely unreachable. */
    private suspend fun ensureAccess() {
        if (access != null || refresh()) return
        if (signedIn) throw ApiException(503, "Couldn't reach the server — check your connection.")
        throw ApiException(401, "Signed out")
    }

    /** Authenticated call with the fresh-launch refresh + one rotation retry.
     * GET responses are cached; a network failure falls back to the last copy so
     * read screens work offline (the store is cleared on sign-out for privacy). */
    suspend fun api(
        path: String,
        method: String = "GET",
        json: JSONObject? = null,
        base: String = BuildConfig.API_BASE_URL,
    ): String {
        val isGet = method == "GET"
        return try {
            ensureAccess()
            val result = try {
                raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true, base = base)
            } catch (e: ApiException) {
                // A 401 is a stale token. A 403 from the ENGINE is a consent refusal —
                // a real answer, not an auth problem — and rotating the token would not
                // change it, so don't burn a refresh on it.
                val staleToken = e.code == 401 || (e.code == 403 && base == BuildConfig.API_BASE_URL)
                if (staleToken && refresh()) {
                    raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true, base = base)
                } else {
                    throw e
                }
            }
            if (isGet) {
                cachePut(base, path, result)
                servedStale = false   // a live network read — we're online again
            }
            result
        } catch (e: Exception) {
            // Serve the last cached copy only for connectivity failures or server
            // (5xx) errors — a 4xx is a real, actionable response (bad request,
            // not-found, forbidden) and must not be masked behind stale data.
            val serveStale = e !is ApiException || e.code >= 500
            if (isGet && serveStale) {
                cacheGet(base, path)?.let {
                    servedStale = true   // honest signal: this is the last copy
                    return it
                }
            }
            throw e
        }
    }

    /** Adopt a token pair the server handed back mid-session (a consent change rotates
     * the session so the new claim takes effect on the very next request — see the
     * platform's PATCH /users/me/consent). Without this the app would keep using a token
     * that still says "yes" to something the person just switched off. */
    internal fun adoptTokens(json: JSONObject) {
        val newAccess = json.optString("access_token")
        val newRefresh = json.optString("refresh_token")
        if (newAccess.isBlank() || newRefresh.isBlank()) return
        access = newAccess
        storage.putString(REFRESH_KEY, newRefresh)
        signedIn = true
    }

    // ── consent that survives a dropped packet ───────────────────────────────
    //
    // Onboarding captures six DPDP answers moments after the account exists, on a network
    // that has just been used for the first time. If that one request fails, the answers
    // are gone — and the app carries on as though it had them. So a failed write is kept
    // and replayed. This is the record of a consent; losing it is not a UI blemish.
    private const val PENDING_CONSENT_KEY = "pending_consent"

    internal fun queueConsent(patch: JSONObject) {
        runCatching { storage.putString(PENDING_CONSENT_KEY, patch.toString()) }
    }

    /** Replay a queued consent. Called on launch; a no-op when there is nothing owed. */
    internal suspend fun flushPendingConsent(): Boolean {
        val raw = storage.getString(PENDING_CONSENT_KEY) ?: return false
        val patch = runCatching { JSONObject(raw) }.getOrNull()
        if (patch == null) {
            storage.remove(PENDING_CONSENT_KEY)   // unreadable: not worth retrying forever
            return false
        }
        return runCatching { Api.updateConsent(patch) }
            .onSuccess { storage.remove(PENDING_CONSENT_KEY) }
            .isSuccess
    }

    // The cache is keyed by BASE + path: the platform and the engine both serve paths of
    // our choosing, and two identical paths on different services must not collide (one
    // service's body would be served as the other's last-known copy).
    private fun cacheKey(base: String, path: String) = "cache:$base$path"
    private fun cachePut(base: String, path: String, body: String) {
        runCatching { storage.putString(cacheKey(base, path), body) }
    }
    private fun cacheGet(base: String, path: String): String? = storage.getString(cacheKey(base, path))
    private fun clearCache() {
        storage.keys().filter { it.startsWith("cache:") }.forEach { storage.remove(it) }
    }

    fun signOut() {
        access = null
        storage.remove(REFRESH_KEY)
        clearCache()
        servedStale = false
        signedIn = false
    }
}

/** Typed-ish endpoint helpers over the raw session. */
object Api {
    // WHICH SERVICE SERVES WHAT (cross-stack contract — docs/ARCHITECTURE.md):
    //
    //   platform (API_BASE_URL)  the account: identity, profile, consent, trusted
    //                            contact, streak. An HR admin's token reaches this
    //                            service, so it holds NO content — that is what makes
    //                            "counts, never content" a property of the schema.
    //   engine (ENGINE_BASE_URL) the content: coaching turns, and the person's own
    //                            journal / sleep log / check-ins. Same bearer token
    //                            (shared HS512 secret); the engine reads the subject and
    //                            the six consent flags from its claims.
    private val ENGINE = BuildConfig.ENGINE_BASE_URL

    suspend fun me(): JSONObject = JSONObject(Session.api("/users/me"))

    /** Crisis helplines for [region], from the ENGINE — safety code lives there and is
     *  never hardcoded in a client (ARCHITECTURE.md §Cross-stack contracts). Returns the
     *  raw body; `data.Helplines` parses it and owns the offline floor. Session caches
     *  every GET, so a later network failure serves the last copy rather than nothing. */
    suspend fun helplinesRaw(region: String): String =
        Session.api("/v1/safety/helplines?region=" + java.net.URLEncoder.encode(region, "UTF-8"), base = ENGINE)
    suspend fun streak(): JSONObject = JSONObject(Session.api("/users/me/streak"))

    suspend fun moods(): JSONArray = JSONArray(Session.api("/v1/wellness/moods", base = ENGINE))
    suspend fun checkIn(mood: String, note: String, symbol: String, intensity: Int): JSONObject =
        JSONObject(
            Session.api(
                "/v1/wellness/moods", "POST",
                JSONObject().put("mood", mood).put("note", note)
                    .put("symbol", symbol).put("intensity", intensity),
                base = ENGINE,
            ),
        )

    suspend fun journal(): JSONArray = JSONArray(Session.api("/v1/wellness/journal", base = ENGINE))
    suspend fun createJournal(title: String, body: String): JSONObject =
        JSONObject(
            Session.api(
                "/v1/wellness/journal", "POST",
                JSONObject().put("title", title).put("body", body)
                    .put("tags", JSONArray()).put("symbol", "book"),
                base = ENGINE,
            ),
        )

    suspend fun sleepLogs(): JSONArray = JSONArray(Session.api("/v1/wellness/sleep", base = ENGINE))
    suspend fun sleepSummary(): JSONObject =
        JSONObject(Session.api("/v1/wellness/sleep/summary", base = ENGINE))
    suspend fun logSleep(date: String, bedtime: String, wakeTime: String, quality: Int): JSONObject =
        JSONObject(
            Session.api(
                "/v1/wellness/sleep", "POST",
                JSONObject().put("date", date).put("bedtime", bedtime)
                    .put("wake_time", wakeTime).put("quality", quality).put("awakenings", 0),
                base = ENGINE,
            ),
        )

    /** The most recent session that has not ended, so a killed app can pick the thread back
     *  up. `{resumable, session_id, title, updated_at}`. Served by the ENGINE — the
     *  conversation is coaching content and lives there, not on the platform. */
    suspend fun resumableSession(): JSONObject =
        JSONObject(Session.api("/v1/sessions/resumable", base = ENGINE))

    /** One session's transcript, oldest first: `{converstation_status, chat_history}`.
     *  (The typo is the engine's field name — it is the wire contract, not ours to fix
     *  quietly.) Used to restore the thread after a process death. */
    suspend fun sessionHistory(sessionId: String): JSONObject =
        JSONObject(Session.api("/v1/sessions/$sessionId/history", base = ENGINE))
    suspend fun sendChat(text: String): JSONObject =
        JSONObject(Session.api("/chat/messages", "POST", JSONObject().put("text", text)))

    /** Tappable conversation starters grounded in the user's saved
     * self-reflection (LLM or curated fallback — always non-empty). */
    suspend fun starters(): JSONObject =
        JSONObject(Session.api("/assessment/topics", "POST", JSONObject()))

    // ── Library / insights / account (parity with iOS + web) ──────────────
    suspend fun content(kind: String): JSONArray =
        JSONArray(Session.api("/content?kind=" + URLEncoder.encode(kind, "UTF-8")))

    /** The keyed sound/video catalogue ([MediaCatalog]). Goes through the cached
     * GET path, so a launch offline replays the last catalogue rather than
     * dropping every sound back to its bundled fallback. */
    suspend fun mediaCatalog(): JSONArray = JSONArray(Session.api("/media/catalog"))

    suspend fun insightsWeekly(): JSONObject =
        JSONObject(Session.api("/v1/wellness/insights/weekly", base = ENGINE))

    /** Transparent AI memory: honest learned statements + their data basis.
     *
     * The ENGINE serves this, not the platform: it is derived from the person's own
     * journal / sleep / check-ins, which live in the engine because the platform is the
     * database an HR admin's token reaches. (These two methods pointed at the reference
     * backend's routes — /insights/patterns and /users/me/memory on the platform — which
     * were never ported, so every call 404'd. Nothing called them, so nobody noticed.) */
    suspend fun patterns(): JSONObject =
        JSONObject(Session.api("/v1/wellness/patterns", base = ENGINE))

    /** Forget what the coach learned — the conversation, the derived state and the graph
     *  checkpoints. Deliberately NOT the journal, sleep log or check-ins: those are the
     *  person's own writing with their own controls. `?confirm=true` is required, and the
     *  engine answers 500 if its re-scan finds anything left. */
    suspend fun deleteMemory(): JSONObject =
        JSONObject(Session.api("/v1/privacy/me/memory?confirm=true", "DELETE", base = ENGINE))

    // ── Programs (multi-day journeys — ref "DAY X OF Y" card) ──
    suspend fun activeProgram(): JSONObject? =
        JSONObject(Session.api("/programs/active")).optJSONObject("program")

    suspend fun enrollProgram(contentId: String): JSONObject =
        JSONObject(Session.api("/programs/enroll", "POST", JSONObject().put("content_id", contentId)))

    /** Result-typed: the caller must decide what a failure means. Returning Unit here let
     * every caller "succeed" by ignoring it — and the enrolled hero disappears on refresh
     * regardless, so a dropped request looked exactly like a successful one. */
    suspend fun leaveProgram(): Result<Unit> =
        runCatching { Session.api("/programs/active", "DELETE"); Unit }

    /** Toggle one plan step; returns the refreshed plan (server reconciles). */
    suspend fun togglePlanStep(stepId: String, done: Boolean): JSONObject =
        JSONObject(Session.api("/plans/steps/$stepId", "PATCH", JSONObject().put("done", done)))

    /** Regenerate today's plan from the latest check-ins/diary. */
    suspend fun regeneratePlan(): JSONObject =
        JSONObject(Session.api("/plans/generate", "POST", JSONObject()))

    suspend fun activePlan(): JSONObject? =
        runCatching { JSONObject(Session.api("/plans/active")) }.getOrNull()

    /** Patch profile fields (companion, region, language, name…) via PATCH /users/me. */
    suspend fun updateProfile(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me", "PATCH", patch))

    suspend fun consent(): JSONObject = JSONObject(Session.api("/users/me/consent"))

    /** Change consent, then ADOPT the token pair the platform hands back.
     *
     * The engine enforces the six flags from the signed claim in our token. Keep the old
     * token and the app would go on being allowed to store what the person just switched
     * off, for as long as it stays valid. Swapping in the rotated pair makes a withdrawal
     * effective on the very next request. */
    suspend fun updateConsent(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me/consent", "PATCH", patch)).also { Session.adoptTokens(it) }

    /** 18+ attestation captured during onboarding (best-effort; never blocks). */
    suspend fun attest() { Session.api("/users/me/attest", "POST", JSONObject().put("adult", true)) }

    suspend fun trustedContact(): JSONObject? =
        Session.api("/users/me/trusted-contact").let {
            if (it.isBlank() || it.trim() == "null") null else JSONObject(it)
        }
    suspend fun setTrustedContact(name: String, method: String, value: String): JSONObject =
        JSONObject(
            Session.api(
                "/users/me/trusted-contact", "PUT",
                JSONObject().put("name", name).put("method", method)
                    .put("value", value).put("notify_consent", true),
            ),
        )

    /** Full personal-data export (privacy screen). Returns the raw JSON text. */
    suspend fun exportData(): String = Session.api("/users/me/export")

    /** Irreversible account + data deletion. */
    suspend fun deleteAccount() { Session.api("/users/me", "DELETE") }

    /** Whether the streaming agentic Oracle is enabled server-side (needs
     * ORACLE_ENABLED + an LLM key); false → clients use deterministic /chat. */
    suspend fun oracleAvailable(): Boolean =
        runCatching { JSONObject(Session.api("/oracle/status")).optBoolean("available") }
            .getOrDefault(false)

    /** Which halves of the cloud voice loop the server has (Deepgram/ElevenLabs).
     * Both false → clients keep the keyless on-device STT/TTS path. */
    suspend fun voiceStatus(): JSONObject = JSONObject(Session.api("/voice/status"))

    /** Cloud STT: recorded clip → transcript (Deepgram via /voice/stt). */
    suspend fun stt(bytes: ByteArray, mime: String = "audio/mp4"): String =
        JSONObject(Session.upload("/voice/stt", "audio", "clip.m4a", mime, bytes))
            .optString("transcript")

    /** Cloud TTS: reply text → MP3 bytes (ElevenLabs via /voice/tts). */
    suspend fun tts(text: String): ByteArray =
        Session.postForBytes("/voice/tts", JSONObject().put("text", text))
}

/** One SSE line → its JSON payload (`data: {…}`); null for blanks/comments. */
internal fun parseSseLine(line: String): org.json.JSONObject? {
    val t = line.trim()
    if (!t.startsWith("data:")) return null
    return runCatching { org.json.JSONObject(t.substring(5).trim()) }.getOrNull()
}

/** RFC 2046 multipart/form-data body for one file field — pure + testable. */
internal fun multipartBody(
    boundary: String, field: String, filename: String, mime: String, bytes: ByteArray,
): ByteArray {
    val head = (
        "--$boundary\r\n" +
        "Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n" +
        "Content-Type: $mime\r\n\r\n"
    ).toByteArray()
    val tail = "\r\n--$boundary--\r\n".toByteArray()
    return head + bytes + tail
}
