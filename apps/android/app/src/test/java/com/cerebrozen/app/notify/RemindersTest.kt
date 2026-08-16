package com.cerebrozen.app.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** A quiet-hours window that cannot contain the wall-clock hour this test
     * happens to run at — otherwise the suite would fail every evening. */
    private fun quietElsewhere() {
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        Reminders.setQuietHours(context, (now + 1) % 24, (now + 2) % 24)
    }

    @Test
    fun show_posts_the_gentle_notification() {
        // force = true is the Settings "send a test" path: an explicitly
        // requested notification must arrive whatever the hour.
        Reminders.show(context, force = true)
        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(1, posted.size)
        val n = posted[0]
        assertEquals("A moment for you", shadowOf(n).contentTitle)
        assertEquals("Twenty seconds — how are you, really?", shadowOf(n).contentText)
        assertNotNull("tapping must open the app", n.contentIntent)
    }

    @Test
    fun the_notification_carries_the_quick_log_action() {
        // V3-e: "Check in" logs a mood from a small popup — the app never has
        // to open. The second action is the plain Open door.
        Reminders.show(context, force = true)
        val n = shadowOf(notificationManager).allNotifications.single()
        assertEquals(2, n.actions.size)
        assertEquals("Check in", n.actions[0].title)
        assertEquals("Open", n.actions[1].title)
    }

    @Test
    fun the_alarm_receiver_posts_the_notification() {
        quietElsewhere()
        ReminderReceiver().onReceive(context, Intent())
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun the_alarm_posts_at_most_one_nudge_a_day() {
        // The promise the design makes out loud. A re-armed alarm, a reboot or
        // a second dispatcher must not stack a second nudge on the same day.
        quietElsewhere()
        ReminderReceiver().onReceive(context, Intent())
        ReminderReceiver().onReceive(context, Intent())
        ReminderReceiver().onReceive(context, Intent())
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun quiet_hours_drop_the_nudge_entirely() {
        // Quiet means quiet: dropped, never queued to arrive at 07:00 asking
        // about yesterday. The inbox already keeps what was missed.
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        Reminders.setQuietHours(context, now, (now + 1) % 24)
        ReminderReceiver().onReceive(context, Intent())
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun shouldPost_enforces_both_rules_including_the_midnight_wrap() {
        // one a day
        assertFalse(shouldPost("2026-08-16", "2026-08-16", hour = 9, quietStart = 22, quietEnd = 7))
        assertTrue(shouldPost("2026-08-15", "2026-08-16", hour = 9, quietStart = 22, quietEnd = 7))
        assertTrue(shouldPost(null, "2026-08-16", hour = 9, quietStart = 22, quietEnd = 7))
        // the default window wraps midnight: 22, 23, 0…6 quiet; 7 and 21 open
        listOf(22, 23, 0, 3, 6).forEach {
            assertFalse("$it should be quiet", shouldPost(null, "d", it, 22, 7))
        }
        listOf(7, 12, 21).forEach {
            assertTrue("$it should be open", shouldPost(null, "d", it, 22, 7))
        }
        // a same-hour window is "quiet all day" — how someone switches nudges
        // off without hunting for a toggle
        (0..23).forEach { assertFalse(shouldPost(null, "d", it, 9, 9)) }
        // a non-wrapping window behaves like the plain range it looks like
        assertFalse(shouldPost(null, "d", 14, 13, 17))
        assertTrue(shouldPost(null, "d", 18, 13, 17))
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

    @Test
    fun the_chosen_hour_is_remembered_and_survives_a_rearm() {
        // The audit's A2/A3: schedule(context) used to default to a literal 9,
        // so the Settings toggle and every reboot silently moved an "evening"
        // (19:00) onboarding choice back to the morning.
        Reminders.schedule(context, hour = 19)
        assertEquals(19, Reminders.storedHour(context))

        // A no-hour re-arm (Settings toggle, BootReceiver) keeps 19:00.
        Reminders.cancel(context)
        Reminders.schedule(context)
        assertEquals(19, Reminders.storedHour(context))
        val cal = java.util.Calendar.getInstance()
            .apply { timeInMillis = shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs }
        assertEquals(19, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun boot_rearm_uses_the_stored_hour() {
        prefsOn(true)
        Reminders.rememberHour(context, 19)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val cal = java.util.Calendar.getInstance()
            .apply { timeInMillis = shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs }
        assertEquals(19, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun a_corrupt_or_absent_stored_hour_falls_back_to_the_default() {
        assertEquals(Reminders.DEFAULT_HOUR, Reminders.storedHour(context))
        Reminders.rememberHour(context, 99)   // coerced, never a crash or a 99:00 alarm
        assertEquals(23, Reminders.storedHour(context))
    }

    private fun prefsOn(on: Boolean) {
        context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
            .edit().putBoolean("reminder_on", on).commit()
    }
}
