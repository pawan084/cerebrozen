package com.cerebrozen.app.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cerebrozen.app.MainActivity
import java.util.Calendar

/**
 * A real, keyless daily reminder — a local AlarmManager alarm posts a gentle
 * notification once a day. No FCM/server needed (that's for remote nudges). On
 * API 33+ the caller requests POST_NOTIFICATIONS first.
 */
object Reminders {
    private const val CHANNEL_ID = "daily_reminder"
    private const val REQ = 4271
    private const val NOTIF_ID = 42
    private const val PREFS = "cerebro"
    private const val HOUR_KEY = "reminder_hour"
    const val DEFAULT_HOUR = 9

    // ── V3-e: the proactive rules, enforced in code ──────────────────────
    // The companion-first design promises three things about nudges, and a
    // promise the code doesn't keep is the kind of thing this product cannot
    // afford: (1) at most ONE a day, (2) never inside quiet hours, (3) every
    // one lands in the inbox. (3) already held (NotificationLog); these keys
    // and the pure helpers below make (1) and (2) true.
    private const val LAST_POSTED_KEY = "nudge_last_posted"   // ISO local date
    private const val QUIET_START_KEY = "quiet_hours_start"
    private const val QUIET_END_KEY = "quiet_hours_end"
    const val DEFAULT_QUIET_START = 22
    const val DEFAULT_QUIET_END = 7

    fun quietHours(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(QUIET_START_KEY, DEFAULT_QUIET_START).coerceIn(0, 23) to
            p.getInt(QUIET_END_KEY, DEFAULT_QUIET_END).coerceIn(0, 23)
    }

    fun setQuietHours(context: Context, startHour: Int, endHour: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(QUIET_START_KEY, startHour.coerceIn(0, 23))
            .putInt(QUIET_END_KEY, endHour.coerceIn(0, 23))
            .apply()
    }

    /** The hour the user chose (onboarding chip or the Reminders screen). */
    fun storedHour(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(HOUR_KEY, DEFAULT_HOUR).coerceIn(0, 23)

    /** Persist the chosen hour without touching the alarm (used while the
     * reminder is switched off, so the choice survives until it's back on). */
    fun rememberHour(context: Context, hour: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(HOUR_KEY, hour.coerceIn(0, 23)).apply()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.cerebrozen.app.R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(com.cerebrozen.app.R.string.reminder_channel_desc)
                },
            )
        }
    }

    private fun alarmPending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Schedule a gentle daily reminder (inexact — needs no exact-alarm perm).
     *
     * Pass [hour] to change the time (it is remembered); omit it to re-arm at
     * the user's stored choice. The default used to be a literal 9, so the
     * Settings toggle and every reboot silently moved an "evening" user's
     * reminder to the morning (audit A2/A3). */
    fun schedule(context: Context, hour: Int? = null) {
        hour?.let { rememberHour(context, it) }
        val at = storedHour(context)
        ensureChannel(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, at); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        context.getSystemService(AlarmManager::class.java)
            .setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, alarmPending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmPending(context))
    }

    /** Post the reminder now (fired by the alarm; also used for a test tap).
     *
     * [force] bypasses the once-a-day cap and quiet hours — that is the
     * Settings "send a test" tap, which the user asked for explicitly and must
     * always be answered, or the button looks broken. */
    fun show(context: Context, force: Boolean = false) {
        if (!force) {
            val (qs, qe) = quietHours(context)
            val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val today = java.time.LocalDate.now().toString()
            val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LAST_POSTED_KEY, null)
            if (!shouldPost(lastPostedDate = last, today = today, hour = nowHour, quietStart = qs, quietEnd = qe)) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(LAST_POSTED_KEY, today).apply()
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // V3-e: "Check in" opens the quick-log popup, not the app — one tap
        // logs a mood over whatever is on screen (QuickLogActivity). The
        // notification face itself stays discreet: "A moment for you", never
        // the why (design rule §9).
        val quickLog = PendingIntent.getActivity(
            context, 1, Intent(context, QuickLogActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(com.cerebrozen.app.R.string.reminder_title))
            .setContentText(context.getString(com.cerebrozen.app.R.string.reminder_body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(com.cerebrozen.app.R.string.reminder_action_checkin),
                    quickLog,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(com.cerebrozen.app.R.string.reminder_action_open),
                    open,
                ).build(),
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
        // The daily alarm is the one nudge that works with no account and no
        // Firebase, so it is the one the inbox can always show honestly.
        NotificationLog.record(
            title = context.getString(com.cerebrozen.app.R.string.reminder_title),
            body = context.getString(com.cerebrozen.app.R.string.reminder_body),
            at = java.time.OffsetDateTime.now().toString(),
            kind = "checkin",
            route = NotificationLog.routeFor("checkin"),
        )
    }
}

/**
 * Whether a nudge may be posted right now. Pure + unit-tested.
 *
 * Two rules, both promises the design makes out loud:
 *  - **one a day** — [lastPostedDate] equal to [today] means the day already
 *    had its nudge (a re-armed alarm, a reboot, a second dispatcher must not
 *    stack a second one).
 *  - **quiet hours** — the window wraps midnight (22 → 7 by default), and a
 *    start equal to end means "quiet all day", which is how a user switches
 *    nudges off without hunting for a toggle. The nudge is DROPPED, never
 *    queued to fire later: a check-in nudge that arrives at 07:00 for
 *    yesterday is noise, and the inbox already keeps what was missed.
 */
internal fun shouldPost(
    lastPostedDate: String?,
    today: String,
    hour: Int,
    quietStart: Int,
    quietEnd: Int,
): Boolean {
    if (lastPostedDate == today) return false
    val quiet = when {
        quietStart == quietEnd -> true                        // quiet all day
        quietStart < quietEnd -> hour in quietStart until quietEnd
        else -> hour >= quietStart || hour < quietEnd         // wraps midnight
    }
    return !quiet
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(context)
    }
}

/**
 * AlarmManager alarms don't survive a reboot (or an app update / force-stop), so
 * re-arm the daily reminder on BOOT_COMPLETED and MY_PACKAGE_REPLACED whenever the
 * user has it switched on. Without this the "gentle daily check-in" silently stops
 * firing after the first restart while the Settings toggle still reads "on".
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val on = context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
            .getBoolean("reminder_on", false)
        if (on) Reminders.schedule(context)
    }
}
