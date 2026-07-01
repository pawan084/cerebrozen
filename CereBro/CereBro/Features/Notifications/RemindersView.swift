import SwiftUI

/// Daily check-in reminder settings. Enabling asks for notification permission
/// (only here, on an explicit tap) and schedules a repeating local notification.
struct RemindersView: View {
    @EnvironmentObject var state: AppState
    @State private var denied = false

    private var timeLabel: String {
        var c = DateComponents(); c.hour = state.reminderHour; c.minute = 0
        let date = Calendar.current.date(from: c) ?? Date()
        let f = DateFormatter(); f.timeStyle = .short
        return f.string(from: date)
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Gentle nudges", title: "Daily Reminder",
                       trailingSystemImage: "bell", accent: Theme.Accent.warm) {
            ToolBanner(imageURL: Dummy.Img.bell, symbol: "bell",
                       caption: "One quiet nudge a day to check in. No streaks lost, no pressure — turn it off anytime.",
                       accent: Theme.Accent.warm)

            Card {
                Toggle(isOn: Binding(get: { state.reminderEnabled }, set: { setEnabled($0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Daily check-in reminder").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                        Text(state.reminderEnabled ? "On · \(timeLabel)" : "Off")
                            .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    }
                }
                .tint(Theme.Palette.lav)
            }

            if state.reminderEnabled {
                Card {
                    DatePicker("Reminder time",
                               selection: Binding(get: { timeFromHour }, set: { setHour(from: $0) }),
                               displayedComponents: .hourAndMinute)
                        .datePickerStyle(.compact)
                        .tint(Theme.Palette.lav)
                        .foregroundStyle(Theme.Palette.soft)
                }
            }

            if denied {
                InsightCard(label: "Notifications are off",
                            title: "Turn on notifications for CereBro in Settings to receive reminders.")
            }
        }
        .task {
            guard state.reminderEnabled else { denied = false; return }
            denied = !(await ReminderManager.isAuthorized())
        }
    }

    private var timeFromHour: Date {
        var c = DateComponents(); c.hour = state.reminderHour; c.minute = 0
        return Calendar.current.date(from: c) ?? Date()
    }

    private func setHour(from date: Date) {
        state.reminderHour = Calendar.current.component(.hour, from: date)
        if state.reminderEnabled { ReminderManager.scheduleDaily(hour: state.reminderHour) }
    }

    private func setEnabled(_ on: Bool) {
        if on {
            Task {
                let ok = await ReminderManager.requestAuthorization()
                await MainActor.run {
                    denied = !ok
                    state.reminderEnabled = ok
                    if ok { ReminderManager.scheduleDaily(hour: state.reminderHour) }
                }
            }
        } else {
            state.reminderEnabled = false
            denied = false
            ReminderManager.cancel()
        }
    }
}
