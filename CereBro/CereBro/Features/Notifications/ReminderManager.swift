import Foundation
import UserNotifications

/// Schedules the gentle daily check-in reminder — the external "trigger" that
/// pulls a user back into the habit loop. Permission is only ever requested from
/// an explicit user action (the Reminders toggle), never silently, so it can't
/// surprise the user or block UI tests.
enum ReminderManager {
    static let dailyID = "cerebro.daily-checkin"

    /// Ask for notification permission. Returns whether it's granted.
    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied:
            return false
        default:
            return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        }
    }

    static func isAuthorized() async -> Bool {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        return [.authorized, .provisional, .ephemeral].contains(s.authorizationStatus)
    }

    /// Schedule a repeating daily reminder at `hour:minute` (24h).
    static func scheduleDaily(hour: Int, minute: Int = 0) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [dailyID])

        let content = UNMutableNotificationContent()
        content.title = "A quiet moment for you"
        content.body = "Take two minutes to check in with CereBro."
        content.sound = .default

        var when = DateComponents()
        when.hour = hour
        when.minute = minute
        let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
        center.add(UNNotificationRequest(identifier: dailyID, content: content, trigger: trigger))
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [dailyID])
    }
}
