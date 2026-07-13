package com.cerebrozen.app.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The keyless daily reminder: channel creation, (in)exact alarm scheduling,
 * cancel, the posted notification itself, and the BOOT_COMPLETED /
 * MY_PACKAGE_REPLACED re-arm honoring the `reminder_on` preference — the fix
 * for "the reminder silently dies after the first reboot".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    // No @Before reset needed: Robolectric gives every test method a fresh
    // application environment (shadow AlarmManager/NotificationManager/prefs).

    @Test
    fun ensureChannel_creates_the_channel_exactly_once() {
        Reminders.ensureChannel(context)
        Reminders.ensureChannel(context)   // idempotent — no duplicate/crash
        val channel = notificationManager.getNotificationChannel("daily_reminder")
        assertNotNull(channel)
        assertEquals("Daily reminder", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun schedule_arms_a_repeating_daily_alarm_and_cancel_disarms_it() {
        Reminders.schedule(context, hour = 9)
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms[0].type)
        assertEquals(AlarmManager.INTERVAL_DAY, alarms[0].interval)
        assertTrue("first fire must be in the future", alarms[0].triggerAtMs > System.currentTimeMillis())

        Reminders.cancel(context)
        assertTrue("cancel must disarm the alarm", shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun schedule_rolls_a_past_hour_to_tomorrow() {
        // Hour 0 (midnight) is in the past for any test run after 00:00:00,
        // exercising the add-a-day branch; the alarm still lands in the future.
        Reminders.schedule(context, hour = 0)
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertTrue(alarm.triggerAtMs > System.currentTimeMillis())
    }

    @Test
    fun show_posts_the_gentle_notification() {
        Reminders.show(context)
        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(1, posted.size)
        val n = posted[0]
        assertEquals("A moment for you", shadowOf(n).contentTitle)
        assertEquals("Twenty seconds — how are you, really?", shadowOf(n).contentText)
        assertNotNull("tapping must open the app", n.contentIntent)
    }

    @Test
    fun the_alarm_receiver_posts_the_notification() {
        ReminderReceiver().onReceive(context, Intent())
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun boot_rearm_only_fires_for_boot_or_update_actions() {
        prefsOn(true)
        BootReceiver().onReceive(context, Intent())                       // no action
        BootReceiver().onReceive(context, Intent("android.intent.action.AIRPLANE_MODE"))
        assertTrue("unrelated intents must not arm anything", shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun boot_rearm_respects_the_reminder_preference() {
        prefsOn(false)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue("toggle off → reboot must not resurrect the reminder",
            shadowOf(alarmManager).scheduledAlarms.isEmpty())

        prefsOn(true)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals("toggle on → reboot re-arms the daily alarm",
            1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun app_update_also_rearms_when_enabled() {
        prefsOn(true)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    private fun prefsOn(on: Boolean) {
        context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
            .edit().putBoolean("reminder_on", on).commit()
    }
}
