package com.cerebrozen.app.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The durable write queue behind "your check-in is saved even with no signal".
 *
 * Until this existed, a mood logged on the metro simply failed: the POST threw,
 * a toast said something went wrong, and the tap was gone. Reads already had an
 * answer (the encrypted response cache), but writes — the half the user
 * actually authored — had none.
 *
 * How it works, and why each part is the way it is:
 *
 * * **Persisted, not in-memory.** The queue lives on the same encrypted
 *   [Session.Store] as the refresh token, so it survives the process being
 *   killed. A queue that only exists in RAM loses exactly the writes it was
 *   built to protect (the app is backgrounded, Android reclaims it, the entry
 *   never happened).
 * * **Every item carries its own idempotency key from the moment it is
 *   queued.** Not generated at send time: a retry after a crash must reuse the
 *   key of the attempt that may already have reached the server, or the replay
 *   creates a second check-in. The server's contract is in
 *   `backend/app/services/idempotency.py`.
 * * **Order is preserved and one failure stops the drain.** Entries are a
 *   journal of what the user did, in the order they did it. Draining past a
 *   failed item to "make progress" would reorder their day.
 * * **A rejected write is dropped, not retried forever.** A 4xx means the
 *   server understood and refused (a malformed body, a revoked account); it
 *   will refuse identically in an hour. Only connectivity/5xx failures stay
 *   queued. A 409 is the *success* case for a replay — the key was already
 *   used, so the write landed.
 */
object Outbox {
    private const val QUEUE_KEY = "outbox"

    /** Beyond this the queue is dropping the oldest rather than growing without
     * bound. A month offline is not a supported state, and an unbounded queue on
     * a device with no signal is a slow way to fill someone's storage. */
    const val MAX_ITEMS = 200

    /** One queued write. [key] is stable across retries — that is the whole point. */
    data class Item(
        val path: String,
        val body: JSONObject,
        val key: String = UUID.randomUUID().toString(),
    ) {
        fun toJson(): JSONObject =
            JSONObject().put("path", path).put("body", body).put("key", key)

        companion object {
            fun fromJson(json: JSONObject): Item? {
                val path = json.optString("path").takeIf { it.isNotBlank() } ?: return null
                val body = json.optJSONObject("body") ?: return null
                val key = json.optString("key").takeIf { it.isNotBlank() } ?: return null
                return Item(path, body, key)
            }
        }
    }

    /** What a drain attempt did, so callers can tell "nothing to do" from "still stuck". */
    data class DrainResult(val sent: Int, val dropped: Int, val remaining: Int)

    // ── Storage ──────────────────────────────────────────────────────────
    fun pending(): List<Item> {
        val raw = Session.prefGet(QUEUE_KEY) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(Item::fromJson)
        }
    }

    fun count(): Int = pending().size

    private fun write(items: List<Item>) {
        val array = JSONArray()
        // Oldest first is the order the user wrote them; trimming takes from the
        // front, because the newest entry is the one they just made and can see.
        items.takeLast(MAX_ITEMS).forEach { array.put(it.toJson()) }
        Session.prefPut(QUEUE_KEY, array.toString())
    }

    /** Called after every enqueue. The real app points this at
     * [OutboxSync.schedule] (MainActivity wires it), so a write queued offline
     * gets a WorkManager drain when connectivity returns even if the app is
     * killed first — the seam is a var, not a direct call, because WorkManager
     * cannot exist in the JVM unit tests that pin this queue's behaviour
     * (same idiom as [Session.http]). */
    internal var scheduleSync: () -> Unit = {}

    fun enqueue(item: Item): Item {
        write(pending() + item)
        scheduleSync()
        return item
    }

    fun clear() {
        Session.prefPut(QUEUE_KEY, JSONArray().toString())
    }

    /**
     * Remove the most recently queued write for [path]. Returns true if one went.
     *
     * This is Undo for something that never reached the server. Without it, a
     * mis-tapped check-in made offline would be un-undoable: the row it should
     * delete does not exist yet, so the delete is a no-op and the queued write
     * syncs the mistake back the moment signal returns.
     */
    fun dropLast(path: String): Boolean {
        val items = pending()
        val index = items.indexOfLast { it.path == path }
        if (index < 0) return false
        write(items.filterIndexed { i, _ -> i != index })
        return true
    }

    // ── Sending ──────────────────────────────────────────────────────────
    /**
     * Send one write now, queueing it if the network refuses.
     *
     * Returns the server's response when it went through, or null when it was
     * queued — the caller shows the entry optimistically either way, because
     * from the user's side the check-in *did* happen.
     *
     * A 4xx is rethrown rather than queued: the request is wrong, and hiding
     * that behind "saved, will sync" would be a lie the user discovers later.
     */
    suspend fun send(path: String, body: JSONObject): JSONObject? {
        val item = Item(path, body)
        return try {
            JSONObject(post(item))
        } catch (e: Session.ApiException) {
            if (retryable(e.code)) {
                enqueue(item)
                null
            } else {
                throw e
            }
        } catch (e: Session.RefusalException) {
            // The server decided, and will decide the same way again. Without
            // this branch it fell through to the catch-all below — the one
            // meaning "no connectivity at all" — and the write was queued, so
            // a refusal was retried on every drain forever while the person was
            // told it was saved and would sync.
            throw e
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Not a network verdict — the coroutine was cancelled (navigation,
            // scope teardown). Swallowing it would break structured concurrency
            // AND queue a write whose first attempt may still land; rethrow and
            // let the caller's retry mint the same decision path again.
            throw e
        } catch (e: Exception) {
            // No connectivity at all (UnknownHost/timeout) — exactly what the
            // queue is for.
            enqueue(item)
            null
        }
    }

    /**
     * Try to flush the queue, oldest first. Safe to call often (app start, a
     * successful network read, the user pulling to refresh) — an empty queue
     * costs one preference read and no network.
     */
    suspend fun drain(): DrainResult = drainLock.withLock { drainLocked() }

    /** Serialises drains. Two callers used to read [pending] at the same moment
     *  and both POST the same entry: the server's idempotency guard answered
     *  the loser 409 ("already in flight"), so nothing was duplicated — but the
     *  race was real and became easy to hit once Today started draining
     *  automatically when the network returned (device walk 2026-08-20: a 201
     *  and a 409 in the same millisecond). The lock costs nothing on the empty
     *  queue that most calls find. */
    private val drainLock = Mutex()

    private suspend fun drainLocked(): DrainResult {
        val items = pending()
        if (items.isEmpty()) return DrainResult(0, 0, 0)

        var sent = 0
        var dropped = 0
        var index = 0
        while (index < items.size) {
            val item = items[index]
            try {
                post(item)
                sent++
            } catch (e: Session.ApiException) {
                when {
                    retryable(e.code) -> break        // still offline / server down
                    // 409 = this key already landed. The write succeeded on an
                    // earlier attempt we never heard the answer to; counting it
                    // as dropped would under-report what the user actually saved.
                    e.code == 409 -> sent++
                    else -> dropped++                 // refused; it will be refused again
                }
                if (retryable(e.code)) break
            } catch (e: Session.RefusalException) {
                // Refused, and it will be refused again — the same verdict the
                // non-retryable ApiException branch above reaches. Counted as
                // dropped and stepped over, NOT a reason to stop draining: the
                // items behind it may be perfectly sendable.
                dropped++
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e                               // cancellation is not "offline"
            } catch (e: Exception) {
                break                                 // no network — stop, keep order
            }
            index++
        }
        val left = items.drop(index)
        write(left)
        return DrainResult(sent, dropped, left.size)
    }

    private suspend fun post(item: Item): String =
        Session.api(item.path, "POST", item.body, mapOf("Idempotency-Key" to item.key))

    /**
     * Whether a failed write should stay queued.
     *
     * 409 is deliberately NOT retryable and NOT an error: the server is saying
     * this exact key already landed, which is the replay working.
     */
    internal fun retryable(code: Int): Boolean = code >= 500 || code == 408 || code == 429
}
