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

    /** Post the reminder now (fired by the alarm; also used for a test tap). */
    fun show(context: Context) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(com.cerebrozen.app.R.string.reminder_title))
            .setContentText(context.getString(com.cerebrozen.app.R.string.reminder_body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
    }
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
