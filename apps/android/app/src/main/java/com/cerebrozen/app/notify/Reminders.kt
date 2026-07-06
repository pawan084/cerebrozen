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

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A gentle once-a-day check-in nudge."
                },
            )
        }
    }

    private fun alarmPending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Schedule a gentle daily reminder (inexact — needs no exact-alarm perm). */
    fun schedule(context: Context, hour: Int = 9) {
        ensureChannel(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
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
            .setContentTitle("A moment for you")
            .setContentText("Twenty seconds — how are you, really?")
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
