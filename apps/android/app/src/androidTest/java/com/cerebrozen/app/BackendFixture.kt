package com.cerebrozen.app

import android.content.Context
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue

/**
 * The signed-in fixture for write-flow tests.
 *
 * Everything instrumented up to now ran as a GUEST, which is why it needed no
 * server: a guest's calls 401 by design, and the assertions were about the app
 * not mistaking "no account" for "an error". Writes cannot work that way. A
 * journal entry that is not stored anywhere proves nothing, so these tests need
 * a real account against a real backend.
 *
 * **They SKIP rather than fail when there is no backend.** CI's Android job is
 * an emulator with no `api` service beside it, so a hard requirement here would
 * turn a green pipeline red for an absence that is expected. This mirrors the
 * rule the iOS live-backend tests already follow (CLAUDE.md: they `XCTSkip`
 * when `localhost:8000` is unreachable, which is why macOS runs show skips).
 * The trade is stated plainly: on CI these are skips, and their real value is
 * on a developer's handset or any runner that brings a backend with it.
 *
 * Reachability is decided by an actual authenticated round trip, not by a
 * socket check — a port that accepts a connection but is a different service
 * is exactly the trap this repo has hit before (a stray `python` bound to 8000
 * answered the phone's tunnel with 404s that looked like app bugs).
 */
internal object BackendFixture {

    /** The seeded demo account (`backend/app/seed.py`); dev-only by design. */
    private const val EMAIL = "pawan@cerebro.app"
    private const val PASSWORD = "demo12345"

    /** Cheap enough to call per-test; the sign-in is what proves reachability. */
    fun signInOrSkip(context: Context) {
        Session.init(context)
        val ok = runBlocking {
            withTimeoutOrNull(12_000) {
                runCatching {
                    Session.signIn(EMAIL, PASSWORD)
                    // A token alone is not proof: assert an authenticated read
                    // succeeds, so a stale or half-configured server is caught
                    // here rather than three assertions later.
                    Api.me()
                }.isSuccess
            } ?: false
        }
        assumeTrue(
            "no reachable backend at ${BuildConfig.API_BASE_URL} — write-flow tests need one " +
                "(run `docker compose up -d api` and, on a handset, " +
                "`adb reverse tcp:8000 tcp:8000`)",
            ok,
        )
    }

    /** Run a suspending block, failing the test rather than swallowing. */
    fun <T> onServer(block: suspend () -> T): T = runBlocking { block() }

    /**
     * Sign up a throwaway account and become it.
     *
     * For any test whose state cannot be put back LOSSLESSLY. Leaving and
     * re-enrolling a programme is the example that taught this: the cleanup
     * looked complete — the same programme was active again — but `day` is
     * derived from `started_at`, so the demo account silently went from day 4
     * to day 1 and the next device walk would have screenshotted a different
     * product. "Restored" has to mean the state is the same, not that a row
     * exists again.
     *
     * The account is never cleaned up here on purpose: a test that deletes its
     * own account cannot then assert anything about it, and these rows are tiny
     * and obviously named. `AccountDeletionE2ETest` deletes its own because
     * deleting IS the assertion.
     */
    fun asThrowaway(prefix: String): String {
        val email = "e2e-$prefix-${System.currentTimeMillis()}@cerebro.app"
        runBlocking { Session.signUp(email, "throwaway-12345", "E2E $prefix") }
        return email
    }
}
