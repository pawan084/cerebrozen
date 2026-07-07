package com.cerebrozen.app.net

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cerebrozen.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal first-party API client — deliberately zero SDKs (HttpURLConnection +
 * org.json + coroutines). Token model mirrors apps/app/lib/api.ts: the ACCESS
 * token lives in memory only, the REFRESH token in SharedPreferences, and a
 * 401/403 triggers one rotation retry before signing out.
 * (Hardening note: move the refresh token to EncryptedSharedPreferences when
 * the security-crypto dependency lands — parity with web's localStorage today.)
 */
object Session {
    private const val PREFS = "cerebro"
    private const val REFRESH_KEY = "refresh_token"

    /** Compose-observable auth state; gates the whole UI in CereBroApp. */
    var signedIn by mutableStateOf(false)
        private set

    private var access: String? = null
    private var storage: Store = MemoryStore()

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
        storage = SharedPrefsStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        signedIn = storage.getString(REFRESH_KEY) != null
    }

    /** Point Session at fakes for unit tests (no Android, no network). */
    internal fun resetForTest(
        store: Store,
        exec: suspend (String, String, String?, String?, String?) -> Pair<Int, String>,
    ) {
        storage = store
        http = exec
        sseExec = ::realSse
        access = null
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
    ): String {
        val (code, text) = http(BuildConfig.API_BASE_URL + path, method, body, contentType, if (authed) access else null)
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("detail") }
                .getOrNull().takeUnless { it.isNullOrBlank() } ?: "Request failed ($code)"
            throw ApiException(code, detail)
        }
        return text
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
            conn.requestMethod = method
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
            code to (if (code in 200..299) conn.inputStream
                     else conn.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0)))
        }

    /** POST an SSE endpoint and invoke [onEvent] for every `data:` JSON frame.
     * Same auth semantics as [api]: fresh-launch refresh + one rotation retry. */
    suspend fun sse(path: String, json: JSONObject, onEvent: (JSONObject) -> Unit) {
        if (access == null && !refresh()) throw ApiException(401, "Signed out")
        var detail = ""
        suspend fun attempt(): Int {
            val (code, stream) = sseExec(BuildConfig.API_BASE_URL + path, json.toString(), access)
            withContext(Dispatchers.IO) {
                stream.bufferedReader().use { reader ->
                    if (code in 200..299) {
                        reader.forEachLine { line -> parseSseLine(line)?.let(onEvent) }
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
        val token = storage.getString(REFRESH_KEY) ?: return false
        return try {
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

    /** Authenticated call with the fresh-launch refresh + one rotation retry.
     * GET responses are cached; a network failure falls back to the last copy so
     * read screens work offline (the store is cleared on sign-out for privacy). */
    suspend fun api(path: String, method: String = "GET", json: JSONObject? = null): String {
        val isGet = method == "GET"
        return try {
            if (access == null && !refresh()) throw ApiException(401, "Signed out")
            val result = try {
                raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true)
            } catch (e: ApiException) {
                if ((e.code == 401 || e.code == 403) && refresh()) {
                    raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true)
                } else {
                    throw e
                }
            }
            if (isGet) cachePut(path, result)
            result
        } catch (e: Exception) {
            if (isGet) cacheGet(path)?.let { return it }
            throw e
        }
    }

    private fun cacheKey(path: String) = "cache:$path"
    private fun cachePut(path: String, body: String) {
        runCatching { storage.putString(cacheKey(path), body) }
    }
    private fun cacheGet(path: String): String? = storage.getString(cacheKey(path))
    private fun clearCache() {
        storage.keys().filter { it.startsWith("cache:") }.forEach { storage.remove(it) }
    }

    fun signOut() {
        access = null
        storage.remove(REFRESH_KEY)
        clearCache()
        signedIn = false
    }
}

/** Typed-ish endpoint helpers over the raw session. */
object Api {
    suspend fun me(): JSONObject = JSONObject(Session.api("/auth/me"))
    suspend fun streak(): JSONObject = JSONObject(Session.api("/users/me/streak"))
    suspend fun moods(): JSONArray = JSONArray(Session.api("/moods"))
    suspend fun checkIn(mood: String, note: String, symbol: String, intensity: Int): JSONObject =
        JSONObject(
            Session.api(
                "/moods", "POST",
                JSONObject().put("mood", mood).put("note", note)
                    .put("symbol", symbol).put("intensity", intensity),
            ),
        )

    suspend fun journal(): JSONArray = JSONArray(Session.api("/journal"))
    suspend fun createJournal(title: String, body: String): JSONObject =
        JSONObject(
            Session.api(
                "/journal", "POST",
                JSONObject().put("title", title).put("body", body)
                    .put("tags", JSONArray()).put("symbol", "book"),
            ),
        )

    suspend fun sleepLogs(): JSONArray = JSONArray(Session.api("/sleep"))
    suspend fun sleepSummary(): JSONObject = JSONObject(Session.api("/sleep/summary"))
    suspend fun logSleep(date: String, bedtime: String, wakeTime: String, quality: Int): JSONObject =
        JSONObject(
            Session.api(
                "/sleep", "POST",
                JSONObject().put("date", date).put("bedtime", bedtime)
                    .put("wake_time", wakeTime).put("quality", quality).put("awakenings", 0),
            ),
        )

    suspend fun chat(): JSONArray = JSONArray(Session.api("/chat"))
    suspend fun sendChat(text: String): JSONObject =
        JSONObject(Session.api("/chat/messages", "POST", JSONObject().put("text", text)))

    /** Tappable conversation starters grounded in the user's saved
     * self-reflection (LLM or curated fallback — always non-empty). */
    suspend fun starters(): JSONObject =
        JSONObject(Session.api("/assessment/topics", "POST", JSONObject()))

    // ── Library / insights / account (parity with iOS + web) ──────────────
    suspend fun content(kind: String): JSONArray =
        JSONArray(Session.api("/content?kind=" + URLEncoder.encode(kind, "UTF-8")))

    suspend fun insightsWeekly(): JSONObject = JSONObject(Session.api("/insights/weekly"))

    /** The user's active daily plan, or null if none. */
    suspend fun activePlan(): JSONObject? =
        runCatching { JSONObject(Session.api("/plans/active")) }.getOrNull()

    /** Patch profile fields (companion, region, language, name…) via PATCH /users/me. */
    suspend fun updateProfile(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me", "PATCH", patch))

    suspend fun consent(): JSONObject = JSONObject(Session.api("/users/me/consent"))
    suspend fun updateConsent(patch: JSONObject): JSONObject =
        JSONObject(Session.api("/users/me/consent", "PATCH", patch))

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
}

/** One SSE line → its JSON payload (`data: {…}`); null for blanks/comments. */
internal fun parseSseLine(line: String): org.json.JSONObject? {
    val t = line.trim()
    if (!t.startsWith("data:")) return null
    return runCatching { org.json.JSONObject(t.substring(5).trim()) }.getOrNull()
}
