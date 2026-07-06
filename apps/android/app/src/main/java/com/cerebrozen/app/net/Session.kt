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
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        signedIn = prefs?.getString(REFRESH_KEY, null) != null
    }

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun raw(
        path: String,
        method: String,
        body: String?,
        contentType: String?,
        authed: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (authed) access?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }
                    .getOrNull().takeUnless { it.isNullOrBlank() } ?: "Request failed ($code)"
                throw ApiException(code, detail)
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun store(tokens: JSONObject) {
        access = tokens.getString("access_token")
        prefs?.edit()?.putString(REFRESH_KEY, tokens.getString("refresh_token"))?.apply()
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

    /** Rotate the token pair; false means the session is truly over. */
    private suspend fun refresh(): Boolean {
        val token = prefs?.getString(REFRESH_KEY, null) ?: return false
        return try {
            val body = JSONObject().put("refresh_token", token)
            store(JSONObject(raw("/auth/refresh", "POST", body.toString(), "application/json", authed = false)))
            true
        } catch (_: Exception) {
            signOut()
            false
        }
    }

    /** Authenticated call with the fresh-launch refresh + one rotation retry. */
    suspend fun api(path: String, method: String = "GET", json: JSONObject? = null): String {
        if (access == null && !refresh()) throw ApiException(401, "Signed out")
        return try {
            raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true)
        } catch (e: ApiException) {
            if ((e.code == 401 || e.code == 403) && refresh()) {
                raw(path, method, json?.toString(), json?.let { "application/json" }, authed = true)
            } else {
                throw e
            }
        }
    }

    fun signOut() {
        access = null
        prefs?.edit()?.remove(REFRESH_KEY)?.apply()
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

    // ── Library / insights / account (parity with iOS + web) ──────────────
    suspend fun content(kind: String): JSONArray =
        JSONArray(Session.api("/content?kind=" + URLEncoder.encode(kind, "UTF-8")))

    suspend fun insightsWeekly(): JSONObject = JSONObject(Session.api("/insights/weekly"))

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
}
