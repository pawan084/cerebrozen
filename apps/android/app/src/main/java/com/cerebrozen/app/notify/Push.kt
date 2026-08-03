package com.cerebrozen.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.MainActivity
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session

/**
 * Remote nudges (FCM), as opposed to [Reminders]' local daily alarm.
 *
 * The two exist for different failure modes. A local alarm needs no server and
 * no account, but OEM battery managers kill it and it can only say what the app
 * already knew when it was scheduled. A server nudge survives that and can
 * carry something the server learned since — the wind-down time computed from
 * the user's own diary, for instance.
 *
 * **Everything here is inert without a `google-services.json`.** No Firebase
 * project is configured yet, so [available] is false, registration is skipped,
 * and the app behaves exactly as it did before. That is deliberate: the client
 * ships ready, and turning delivery on later is a config change (a Firebase
 * project plus `FCM_CREDENTIALS_PATH` server-side), not an app release.
 */
object Push {
    const val CHANNEL_ID = "cerebro_nudges"
    private const val TOKEN_KEY = "fcm_token"

    /** Seam: the Firebase token fetch. Replaced in unit tests, and null in
     * production whenever Firebase has no configuration to initialise from. */
    internal var tokenProvider: suspend () -> String? = ::firebaseToken

    /** Seam: "is Firebase configured on this build". Replaced in unit tests so
     * the configured path is testable on a machine with no Firebase project. */
    internal var availabilityProbe: (Context) -> Boolean = ::firebaseConfigured

    /** Restore the production seams. Tests call this in setUp so `available()`
     * exercises the real probe rather than a copy of it that could drift. */
    internal fun resetSeamsForTest() {
        tokenProvider = ::firebaseToken
        availabilityProbe = ::firebaseConfigured
    }

    /** Whether remote push can work on this build at all. */
    fun available(context: Context): Boolean = availabilityProbe(context)

    /** True only when a `google-services.json` produced a real FirebaseApp.
     * Reflective so the check itself cannot throw on a build where the library
     * is present but never initialised. */
    private fun firebaseConfigured(context: Context): Boolean = runCatching {
        val clazz = Class.forName("com.google.firebase.FirebaseApp")
        val apps = clazz.getMethod("getApps", Context::class.java).invoke(null, context)
        (apps as? List<*>)?.isNotEmpty() == true
    }.getOrDefault(false)

    private suspend fun firebaseToken(): String? = runCatching {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
    }.getOrNull()

    /**
     * Register this install with the backend. Called on every signed-in cold
     * start, not once: FCM rotates tokens silently, and a rotated token nobody
     * re-registered is an install that stops getting nudges with nothing in any
     * log to say so.
     *
     * Never throws — a failed registration must not break app start.
     */
    suspend fun register(context: Context) {
        if (!Session.signedIn || !available(context)) return
        val token = runCatching { tokenProvider() }.getOrNull() ?: return
        runCatching {
            Api.registerDevice(token, "android", BuildConfig.VERSION_NAME)
            Session.prefPut(TOKEN_KEY, token)
        }
    }

    /**
     * Drop this install's token — on sign-out, so the next person to use the
     * handset never receives the previous user's nudges.
     */
    suspend fun unregister() {
        val token = Session.prefGet(TOKEN_KEY) ?: return
        runCatching { Api.unregisterDevice(token) }
        Session.prefRemove(TOKEN_KEY)
    }

    /** The channel remote nudges post on — separate from the daily reminder so a
     * user can silence one without losing the other. */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.cerebrozen.app.R.string.push_channel_name),
                    // DEFAULT, never HIGH: a wellness nudge is not an alarm, and
                    // a heads-up banner at 21:00 is the opposite of winding down.
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(com.cerebrozen.app.R.string.push_channel_desc)
                },
            )
        }
    }

    /**
     * Turn one FCM data payload into a posted notification.
     *
     * Sent as *data* rather than a display `notification` block precisely so
     * this runs in every app state — foreground, background and killed alike —
     * and the deeplink survives. A `notification` message would be drawn by the
     * system while the app is backgrounded, with the payload discarded.
     */
    fun show(context: Context, data: Map<String, String>) {
        val title = data["title"].orEmpty().ifBlank { return }
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data["deeplink"]?.takeIf { it.isNotBlank() }?.let { setData(Uri.parse(it)) }
        }
        val pending = PendingIntent.getActivity(
            context,
            // One request code per nudge kind: a check-in nudge replacing a
            // crisis-adjacent one because they shared an id would be a real harm.
            (data["kind"] ?: "nudge").hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(data["body"].orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(data["body"].orEmpty()))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notifyId(data["kind"]), notification)
    }

    /** A stable per-kind notification id, so a second wind-down nudge replaces
     * the first instead of stacking two identical rows in the shade. */
    internal fun notifyId(kind: String?): Int = 4300 + ((kind ?: "nudge").hashCode() and 0x3F)
}

/** Minimal await for the Play-services Task returned by FirebaseMessaging. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error != null) cont.resumeWith(Result.failure(error))
            else cont.resumeWith(Result.success(task.result))
        }
    }
