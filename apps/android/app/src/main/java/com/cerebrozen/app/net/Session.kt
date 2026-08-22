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
    private const val GUEST_KEY = "guest_mode"
    private const val ENTITLEMENT_TIER = "entitlement_tier"
    private const val ENTITLEMENT_SPONSORED = "entitlement_sponsored"
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

    /** Local-first access selected at ONB-09. Guest mode never fabricates an
     * auth token; it only opens the offline-capable product shell.
     * Setter is internal-for-tests only (GuestGateScreensTest composes screens
     * in guest mode); production writes stay inside this file. */
    var guestMode by mutableStateOf(false)
        internal set

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

    /** HTTP transport seam — (url, method, body, contentType, authToken, extra
     * headers) → (status, body); throws on a network failure. Swappable in unit
     * tests. The header map carries `Idempotency-Key` for offline-queue replays
     * (see [Outbox]) and is empty for every other call. */
    internal var http: suspend (String, String, String?, String?, String?, Map<String, String>) -> Pair<Int, String> = ::realHttp

    // For user-facing failure strings (B84). Null in unit tests, where the
    // English fallbacks below keep the message pins meaningful.
    private var appContext: Context? = null

    private fun netString(resId: Int, fallback: String, vararg args: Any?): String =
        runCatching { appContext?.getString(resId, *args) }.getOrNull() ?: fallback

    fun init(context: Context) {
        appContext = context.applicationContext
        storage = buildStore(context)
        signedIn = storage.getString(REFRESH_KEY) != null
        guestMode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(GUEST_KEY, false)
    }

    fun continueAsGuest(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(GUEST_KEY, true).apply()
        guestMode = true
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
        exec: suspend (String, String, String?, String?, String?, Map<String, String>) -> Pair<Int, String>,
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

    /**
     * The free-tier daily cap, specifically.
     *
     * A subclass rather than a flag because the IP rate limiter ALSO returns
     * 429 and means something entirely different — offering an upgrade to
     * someone who merely typed too fast would be wrong and manipulative. The
     * server marks this one with `detail.code`, and only that is trusted.
     *
     * [resetsAtUtc] is an ISO timestamp, not the word "midnight": the window is
     * UTC, so in India it clears at 05:30 local.
     */
    class FreeLimitException(
        message: String,
        val limit: Int,
        val used: Int,
        val resetsAtUtc: String,
    ) : Exception(message)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun raw(
        path: String,
        method: String,
        body: String?,
        contentType: String?,
        authed: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val (code, text) = http(
            BuildConfig.API_BASE_URL + path, method, body, contentType,
            if (authed) access else null, headers,
        )
        logApiResponse(method, path, code, text)
        if (code !in 200..299) {
            // `detail` may be an OBJECT (the free-tier cap) or a string. Reading
            // it blindly with optString would print raw JSON at the user.
            val body = runCatching { JSONObject(text) }.getOrNull()
            val obj = body?.optJSONObject("detail")
            if (code == 429 && obj?.optString("code") == "free_daily_limit") {
                throw FreeLimitException(
                    obj.optString("message"),
                    obj.optInt("limit"),
                    obj.optInt("used"),
                    obj.optString("resets_at"),
                )
            }
            // Pydantic validation errors (422) put `detail` in a THIRD shape —
            // an array of {loc, msg, type} — and the human-written reason sits
            // in `msg` behind a "Value error, " prefix. Without this branch the
            // server's own sentence ("That doesn't look like an email
            // address.") collapsed to "Request failed (422)" on every screen.
            val validationMsg = body?.optJSONArray("detail")
                ?.optJSONObject(0)?.optString("msg")
                ?.removePrefix("Value error, ")
            // `error` is slowapi's key for the IP rate limiter — without it a
            // throttled user just sees "Request failed (429)", which teaches
            // them nothing about what to do. `opt` + a type check rather than
            // `optString`: org.json's optString SERIALIZES a non-string value,
            // which is exactly the print-raw-JSON-at-the-user failure the
            // comment above describes.
            val detail = listOfNotNull(
                obj?.optString("message"),
                body?.opt("detail") as? String,
                validationMsg,
                body?.opt("error") as? String,
            ).firstOrNull { it.isNotBlank() } ?: netString(com.cerebrozen.app.R.string.net_request_failed, "Request failed ($code)", code)
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
        headers: Map<String, String> = emptyMap(),
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
            headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
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
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(GUEST_KEY, false)?.apply()
        guestMode = false
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
    internal fun prefRemove(key: String) { storage.remove(key) }

    // ── Entitlement, as last reported by the server ──────────────────────
    // The server resolves what an account may use (a purchase, or an
    // organisation sponsoring the seat) and says so on /auth/me. It is
    // remembered here for one reason: without it, a member whose employer pays
    // is shown a price list every time the profile read fails. Defaulting to
    // "free" while offline is wrong far more often than the last answer is.
    //
    // Never an authorization decision — the server enforces the quota and the
    // media gate regardless of what this device believes. It decides only what
    // to say on a screen.

    /** Remember what the server just reported. */
    internal fun rememberEntitlement(tier: String, sponsored: Boolean) {
        storage.putString(ENTITLEMENT_TIER, tier)
        storage.putString(ENTITLEMENT_SPONSORED, sponsored.toString())
    }

    /** The last tier the server reported; "free" until one has been. */
    internal fun cachedTier(): String = storage.getString(ENTITLEMENT_TIER) ?: "free"

    /** Whether that tier came from an organisation rather than a purchase. */
    internal fun cachedSponsored(): Boolean = storage.getString(ENTITLEMENT_SPONSORED) == "true"

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
                .getOrNull().takeUnless { it.isNullOrBlank() } ?: netString(com.cerebrozen.app.R.string.net_request_failed, "Request failed ($code)", code)
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
    suspend fun sse(path: String, json: JSONObject, onEvent: (JSONObject) -> Unit) {
        ensureAccess()
        var detail = ""
        suspend fun attempt(): Int {
            val (code, stream) = sseExec(BuildConfig.API_BASE_URL + path, json.toString(), access)
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
        if (code !in 200..299) throw ApiException(code, detail.ifBlank { netString(com.cerebrozen.app.R.string.net_request_failed, "Request failed ($code)", code) })
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
        if (signedIn) {
            throw ApiException(
                503,
                netString(com.cerebrozen.app.R.string.net_unreachable, "Couldn't reach the server — check your connection."),
            )
        }
        // A guest never signed in, so this 401 is not a failure — it is the
        // shell working as documented (guestMode "only opens the
        // offline-capable product shell"). Said here rather than in each
        // screen: every server-backed screen renders its own network-failure
        // copy from ApiException.message via Throwable.userMessage, so a guest
        // was told "Couldn't load patterns. Please try again." about a request
        // that was never going to succeed and never should have.
        if (guestMode) {
            throw ApiException(
                401,
                netString(
                    com.cerebrozen.app.R.string.net_guest_mode,
                    "Sign in to keep this. You're looking around as a guest, so nothing is saved yet.",
                ),
            )
        }
        throw ApiException(401, netString(com.cerebrozen.app.R.string.net_signed_out, "Signed out"))
    }

    /** Authenticated call with the fresh-launch refresh + one rotation retry.
     * GET responses are cached; a network failure falls back to the last copy so
     * read screens work offline (the store is cleared on sign-out for privacy). */
    suspend fun api(
        path: String,
        method: String = "GET",
        json: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val isGet = method == "GET"
        return try {
            ensureAccess()
            val result = try {
                raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true, headers = headers)
            } catch (e: ApiException) {
                if ((e.code == 401 || e.code == 403) && refresh()) {
                    raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true, headers = headers)
                } else {
                    throw e
                }
            }
            if (isGet && cacheablePath(path)) {
                cachePut(path, result)
                servedStale = false   // a live network read — we're online again
            }
            result
        } catch (e: Exception) {
            // Serve the last cached copy only for connectivity failures or server
            // (5xx) errors — a 4xx is a real, actionable response (bad request,
            // not-found, forbidden) and must not be masked behind stale data.
            val serveStale = e !is ApiException || e.code >= 500
            if (isGet && cacheablePath(path) && serveStale) {
                cacheGet(path)?.let {
                    servedStale = true   // honest signal: this is the last copy
                    return it
                }
            }
            throw e
        }
    }

    /** Whether a GET may enter the pref-backed response cache. The full
     * personal-data export must never persist at rest here — on keystore-
     * fallback devices this cache is plaintext, and the payload would sit
     * until sign-out (audit B83). Everything else keeps the offline-first
     * behavior screens like the safety plan depend on. */
    internal fun cacheablePath(path: String): Boolean =
        !path.startsWith("/users/me/export")

    private fun cacheKey(path: String) = "cache:$path"

    /** The response cache keeps at most this many entries — it accumulated
     * every distinct GET path until sign-out, unbounded (B85). Eviction is
     * oldest-written-first via a monotonic sequence. */
    internal const val MAX_CACHE_ENTRIES = 48

    private fun cachePut(path: String, body: String) {
        runCatching {
            val seq = (storage.getString("cache_seq")?.toLongOrNull() ?: 0L) + 1
            storage.putString("cache_seq", seq.toString())
            storage.putString(cacheKey(path), body)
            storage.putString("cacheseq:$path", seq.toString())
            val cached = storage.keys().filter { it.startsWith("cache:") }
            if (cached.size > MAX_CACHE_ENTRIES) {
                val victim = cached.minByOrNull {
                    storage.getString("cacheseq:" + it.removePrefix("cache:"))?.toLongOrNull() ?: 0L
                }
                if (victim != null) {
                    storage.remove(victim)
                    storage.remove("cacheseq:" + victim.removePrefix("cache:"))
                }
            }
        }
    }
    private fun cacheGet(path: String): String? = storage.getString(cacheKey(path))
    private fun clearCache() {
        storage.keys()
            .filter { it.startsWith("cache:") || it.startsWith("cacheseq:") || it == "cache_seq" }
            .forEach { storage.remove(it) }
    }

    /** Pref keys that hold the LEAVING person's data outside the `cache:`
     * namespace. Found live on the 2026-08-16 device walk: after sign-out, a
     * fresh GUEST session greeted the previous account by name and echoed
     * their last check-in ("Earlier · Good · Clear") — `home_snapshot`
     * hydrates Today's first frame and had outlived the account it described.
     * On a shared device in a family-stigma context, "Earlier · Anxious ·
     * Loud thoughts" shown to the next person is precisely the leak the
     * privacy posture forbids. Pinned by AuthFlowTest. */
    private val PERSONAL_PREF_KEYS = listOf(
        "home_snapshot", "sleep_snapshot", "toolkit_recent",
        "milestone_celebrated", "sleepBannerDismissed", "windDownBannerDismissed",
        "talk_crisis_sticky", "mixer_state", "consent_sync_failed",
    )

    fun signOut() {
        access = null
        storage.remove(REFRESH_KEY)
        // Sign out is also the exit from the local-only guest shell. Leaving
        // this flag persisted kept the root UI inside the app and made the
        // sign-out row appear broken for guests instead of showing onboarding.
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(GUEST_KEY, false)?.apply()
        guestMode = false
        // The entitlement belonged to the account that just left. Devices are
        // shared, and inheriting it would tell the next person their employer
        // pays for a seat that is not theirs.
        storage.remove(ENTITLEMENT_TIER)
        storage.remove(ENTITLEMENT_SPONSORED)
        PERSONAL_PREF_KEYS.forEach { storage.remove(it) }
        clearCache()
        servedStale = false
        signedIn = false
    }
}

/** Typed-ish endpoint helpers over the raw session. */
object Api {
    suspend fun me(): JSONObject = JSONObject(Session.api("/auth/me"))
    suspend fun streak(): JSONObject = JSONObject(Session.api("/users/me/streak"))
    suspend fun moods(): JSONArray = JSONArray(Session.api("/moods"))

    /** Take back a check-in. The tap that logs one is single, so the tap that
     * undoes it has to exist — and a stray mood otherwise sits in the 60-day
     * window that patterns and the weekly read are computed from. */
    suspend fun deleteMood(id: String) { Session.api("/moods/$id", "DELETE") }

    /** Log a check-in. Goes through [Outbox], so a tap with no signal is kept
     * and sent later rather than lost — returns null in that case, and the
     * caller shows the entry anyway, because from the user's side it happened. */
    suspend fun checkIn(mood: String, note: String, symbol: String, intensity: Int): JSONObject? =
        Outbox.send(
            "/moods",
            JSONObject().put("mood", mood).put("note", note)
                .put("symbol", symbol).put("intensity", intensity),
        )

    suspend fun journal(): JSONArray = JSONArray(Session.api("/journal"))

    /** Write an entry. Queued when offline, same contract as [checkIn] — a
     * journal that drops what you wrote on a train is worse than no journal. */
    suspend fun createJournal(title: String, body: String, tags: List<String> = emptyList()): JSONObject? =
        Outbox.send(
            "/journal",
            JSONObject().put("title", title).put("body", body)
                .put("tags", JSONArray().apply { tags.forEach { put(it) } })
                .put("symbol", "book"),
        )

    suspend fun updateJournal(id: String, title: String, body: String, tags: List<String> = emptyList()): JSONObject =
        JSONObject(Session.api("/journal/$id", "PUT", JSONObject()
            .put("title", title).put("body", body)
            .put("tags", JSONArray().apply { tags.forEach { put(it) } })
            .put("symbol", "book")))

    suspend fun deleteJournal(id: String) { Session.api("/journal/$id", "DELETE") }

    suspend fun sleepLogs(limit: Int? = null): JSONArray = JSONArray(
        Session.api(if (limit == null) "/sleep" else "/sleep?limit=${limit.coerceIn(1, 366)}"),
    )
    suspend fun sleepSummary(days: Int? = null): JSONObject = JSONObject(
        Session.api(if (days == null) "/sleep/summary" else "/sleep/summary?days=${days.coerceIn(2, 90)}"),
    )
    suspend fun logSleep(date: String, bedtime: String, wakeTime: String, quality: Int): JSONObject =
        JSONObject(
            Session.api(
                "/sleep", "POST",
                JSONObject().put("date", date).put("bedtime", bedtime)
                    .put("wake_time", wakeTime).put("quality", quality).put("awakenings", 0),
            ),
        )

    suspend fun deleteSleep(date: String) { Session.api("/sleep/$date", "DELETE") }

    suspend fun chat(): JSONArray = JSONArray(Session.api("/chat"))
    // ── Work coaching (organisation-sponsored members; /work) ────────────
    // Stateless by design: the client holds the transcript, the server keeps
    // no chat rows — a work conversation never enters the wellness history.
    suspend fun workChat(history: JSONArray, message: String): JSONObject =
        JSONObject(Session.api("/work/chat", "POST",
            JSONObject().put("message", message).put("history", history)))

    suspend fun workPlan(history: JSONArray): JSONObject =
        JSONObject(Session.api("/work/plan", "POST", JSONObject().put("history", history)))

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

    suspend fun insightsWeekly(): JSONObject = JSONObject(Session.api("/insights/weekly"))

    /** Day-by-day mood + sleep series for the Trends screen.
     *
     * Days with no data are absent rather than zero, and every summary number is
     * gated by `enough_data` — the screen must respect both rather than drawing
     * a flat line at the bottom of a chart, which reads as "I felt terrible". */
    suspend fun trends(days: Int = 30): JSONObject =
        JSONObject(Session.api("/insights/trends?days=$days"))

    // ── Journal search ───────────────────────────────────────────────────
    /** Entries filtered server-side. The client only holds the pages it has
     * fetched, so "I wrote about this months ago" is exactly the search that
     * would miss if it ran on-device. */
    suspend fun searchJournal(query: String = "", tag: String = ""): JSONArray {
        val params = buildList {
            if (query.isNotBlank()) add("q=" + URLEncoder.encode(query, "UTF-8"))
            if (tag.isNotBlank()) add("tag=" + URLEncoder.encode(tag, "UTF-8"))
        }
        val suffix = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return JSONArray(Session.api("/journal$suffix"))
    }

    /** Every tag the user has actually used — offered as chips so the composer
     * doesn't quietly create "work", "Work" and "work " as three tags. */
    suspend fun journalTags(): JSONArray = JSONArray(Session.api("/journal/tags"))

    // ── Push registration ────────────────────────────────────────────────
    suspend fun registerDevice(token: String, platform: String, appVersion: String): JSONObject =
        JSONObject(
            Session.api(
                "/users/me/devices", "POST",
                JSONObject().put("token", token).put("platform", platform)
                    .put("app_version", appVersion),
            ),
        )

    suspend fun unregisterDevice(token: String) {
        Session.api("/users/me/devices?token=" + URLEncoder.encode(token, "UTF-8"), "DELETE")
    }

    /** Whether the server can actually deliver push to this platform. The
     * Settings toggle asks first — a switch that silently does nothing is worse
     * than an honest explanation. */
    suspend fun pushStatus(): JSONObject =
        JSONObject(Session.api("/users/me/devices?platform=android"))

    /** Transparent AI memory: honest learned statements + their data basis. */
    suspend fun patterns(): JSONObject = JSONObject(Session.api("/insights/patterns"))

    /** Wipe the AI's memory (chat history + insights + saved notes + Oracle thread state). */
    suspend fun deleteMemory(): JSONObject = JSONObject(Session.api("/users/me/memory", "DELETE"))

    // ── Per-item memory ──
    // Patterns above are computed and can only be hidden; these are rows the
    // user wrote or approved, so they can be edited and deleted individually.
    suspend fun memories(): JSONArray = JSONArray(Session.api("/users/me/memory"))

    suspend fun addMemory(body: String): JSONObject =
        JSONObject(Session.api("/users/me/memory", "POST", JSONObject().put("body", body)))

    suspend fun editMemory(id: String, body: String): JSONObject =
        JSONObject(Session.api("/users/me/memory/$id", "PATCH", JSONObject().put("body", body)))

    suspend fun deleteOneMemory(id: String) {
        Session.api("/users/me/memory/$id", "DELETE")
    }

    // ── Goals + habits (the things the user defines) ──
    /** Active goals, or every goal including the ones finished and let go.
     *
     * The client only ever asked for active ones, so "Done" and "Let it go" —
     * two one-tap buttons sitting beside "Make today's plan" — made a
     * user-authored goal vanish from the app with no way back, while the server
     * had been keeping it and offering this flag all along. */
    suspend fun goals(includeResolved: Boolean = false): JSONArray =
        JSONArray(Session.api("/goals" + if (includeResolved) "?include_resolved=true" else ""))

    suspend fun addGoal(title: String): JSONObject =
        JSONObject(Session.api("/goals", "POST", JSONObject().put("title", title)))

    suspend fun setGoalStatus(id: String, status: String): JSONObject =
        JSONObject(Session.api("/goals/$id", "PATCH", JSONObject().put("status", status)))

    /** Turn a goal into today's plan — replaces the active plan, since the
     * product has exactly one at a time. */
    suspend fun decomposeGoal(id: String): JSONObject =
        JSONObject(Session.api("/goals/$id/decompose", "POST", JSONObject()))

    suspend fun habits(): JSONArray = JSONArray(Session.api("/habits"))

    suspend fun addHabit(title: String, cue: String): JSONObject =
        JSONObject(Session.api("/habits", "POST",
            JSONObject().put("title", title).put("cue", cue)))

    /** Toggle today. Idempotent server-side and undoable — a mis-tap is never
     * permanent. */
    suspend fun setHabitToday(id: String, done: Boolean): JSONObject {
        val body = Session.api(
            "/habits/$id/complete",
            if (done) "POST" else "DELETE",
            if (done) JSONObject() else null,
        )
        return JSONObject(body)
    }

    // ── Recommendations (derived from the user's own patterns) ──
    /** Pending suggestions. The server seeds from current patterns on read and
     * returns [] for thin data, so this is safe to call unconditionally. */
    suspend fun recommendations(): JSONArray = JSONArray(Session.api("/recommendations/mine"))

    suspend fun resolveRecommendation(id: String, accept: Boolean) {
        Session.api("/recommendations/$id/${if (accept) "accept" else "dismiss"}", "POST", JSONObject())
    }

    // ── Safety plan (user-authored; the model never writes one) ──
    /** The live plan, or null when the user hasn't written one — a normal
     * state, never an error. */
    suspend fun safetyPlan(): JSONObject? {
        val body = Session.api("/safety-plan/me")
        if (body.isBlank() || body == "null") return null
        return JSONObject(body)
    }

    /** Save one or more sections; unset fields carry over server-side. */
    suspend fun saveSafetyPlan(fields: JSONObject): JSONObject =
        JSONObject(Session.api("/safety-plan/me", "PUT", fields))

    /** Stop showing one computed pattern. Keyed by the statement — patterns
     * are derived per request and have no id of their own. */
    suspend fun suppressPattern(statement: String) {
        Session.api(
            "/users/me/memory/suppress-pattern", "POST",
            JSONObject().put("statement", statement),
        )
    }

    // ── Programs (multi-day journeys — ref "DAY X OF Y" card) ──
    suspend fun activeProgram(): JSONObject? =
        JSONObject(Session.api("/programs/active")).optJSONObject("program")

    suspend fun enrollProgram(contentId: String): JSONObject =
        JSONObject(Session.api("/programs/enroll", "POST", JSONObject().put("content_id", contentId)))

    suspend fun leaveProgram() { Session.api("/programs/active", "DELETE") }

    /** Toggle one plan step; returns the refreshed plan (server reconciles). */
    suspend fun togglePlanStep(stepId: String, done: Boolean): JSONObject =
        JSONObject(Session.api("/plans/steps/$stepId", "PATCH", JSONObject().put("done", done)))

    /** Regenerate today's plan from the latest check-ins/diary. */
    suspend fun regeneratePlan(): JSONObject =
        JSONObject(Session.api("/plans/generate", "POST", JSONObject()))

    /** Throws on failure — both callers wrap in their own runCatching. The old
     * internal swallow returned null for EVERY failure, which PlanScreen could
     * not tell apart from "no plan yet": a guest (or any failed load) sat on
     * "Loading your plan…" forever because its error branch was unreachable
     * (audit K, guest-state class). */
    suspend fun activePlan(): JSONObject =
        JSONObject(Session.api("/plans/active"))

    /** Patch profile fields (companion, region, language, name…) via PATCH /users/me. */
    suspend fun updateProfile(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me", "PATCH", patch))

    /**
     * Hand a Play purchase to the server, which decides the tier.
     *
     * The two strings go through EXACTLY as Play produced them: the signature
     * is over those bytes, so re-serialising the JSON — even into an identical
     * object — breaks verification. `backend/tests/test_playstore.py` asserts
     * the same contract from the other side.
     */
    suspend fun verifyPlayPurchase(originalJson: String, signature: String): JSONObject =
        JSONObject(
            Session.api(
                "/users/me/subscription/verify-play",
                "POST",
                JSONObject()
                    .put("purchase_payload", originalJson)
                    .put("purchase_signature", signature),
            ),
        )

    suspend fun consent(): JSONObject = JSONObject(Session.api("/users/me/consent"))
    suspend fun updateConsent(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me/consent", "PATCH", patch))

    /** 18+ attestation captured during onboarding (best-effort; never blocks). */
    suspend fun attest() { Session.api("/users/me/attest", "POST", JSONObject().put("adult", true)) }

    suspend fun trustedContact(): JSONObject? =
        Session.api("/users/me/trusted-contact").let {
            if (it.isBlank() || it.trim() == "null") null else JSONObject(it)
        }
    /** [notifyConsent] is the USER's answer, never assumed. This used to hardcode
     * `true`, so naming a trusted contact silently agreed to messaging them at
     * the worst moment of someone's life — and nothing on Android ever called
     * this, so no one had been asked at all. */
    suspend fun setTrustedContact(
        name: String,
        method: String,
        value: String,
        notifyConsent: Boolean,
    ): JSONObject =
        JSONObject(
            Session.api(
                "/users/me/trusted-contact", "PUT",
                JSONObject().put("name", name).put("method", method)
                    .put("value", value).put("notify_consent", notifyConsent),
            ),
        )

    suspend fun deleteTrustedContact() { Session.api("/users/me/trusted-contact", "DELETE") }

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
