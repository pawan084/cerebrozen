import SwiftUI

// MARK: - Morning check-in (the sleep diary's write path)
/// One entry per morning: felt quality + bed/wake times + awakenings. Local-first
/// (AppState), mirrored to `/sleep` when connected. Framed as awareness — never a
/// measurement (docs/SLEEP_TRACKING.md).
struct SleepCheckInView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService

    @State private var quality = 0                 // 0 = not chosen yet
    @State private var bedMinutes = 23 * 60
    @State private var wakeMinutes = 7 * 60
    @State private var awakenings = 0
    @State private var prefilled = false
    @State private var saved = false

    private static let qualityWords = ["Rough", "Poor", "Okay", "Good", "Rested"]

    private var durationText: String {
        SleepEntry(day: "", bedMinutes: bedMinutes, wakeMinutes: wakeMinutes, quality: 1).durationText
    }

    var body: some View {
        ScreenScaffold(eyebrow: "How you slept, not a measurement", title: "Morning Check-in",
                       trailingSystemImage: "sunrise", accent: Theme.Accent.sleep) {
            ToolBanner(imageURL: Dummy.Img.sleep, symbol: "moon.zzz",
                       caption: "A 20-second reflection on last night shapes today's plan.",
                       accent: Theme.Accent.sleep)

            SectionTitle(title: "How rested do you feel?")
            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { level in
                    Button { quality = level; Haptics.selection() } label: {
                        VStack(spacing: 6) {
                            Image(systemName: level <= quality ? "moon.zzz.fill" : "moon.zzz")
                                .appFont(19, weight: .semibold)
                                .foregroundStyle(level <= quality ? Theme.Palette.lav : Theme.Palette.muted2)
                            Text(Self.qualityWords[level - 1])
                                .appFont(9.5, weight: .semibold)
                                .foregroundStyle(level == quality ? Theme.Palette.soft : Theme.Palette.muted2)
                        }
                        .frame(maxWidth: .infinity, minHeight: 62)
                        .background(level == quality ? Theme.Palette.cardEmphasis : Theme.Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(level == quality ? Theme.Palette.lav.opacity(0.7) : Theme.Palette.line))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Sleep quality \(level) of 5, \(Self.qualityWords[level - 1])")
                    .accessibilityAddTraits(level == quality ? .isSelected : [])
                }
            }

            SectionTitle(title: "Your night")
            Card {
                VStack(spacing: 4) {
                    timeRow(label: "In bed around", systemImage: "bed.double", minutes: $bedMinutes)
                    Divider().overlay(Theme.Palette.line)
                    timeRow(label: "Woke up around", systemImage: "sun.horizon", minutes: $wakeMinutes)
                    Divider().overlay(Theme.Palette.line)
                    HStack {
                        Label("Woke during the night", systemImage: "moon.haze")
                            .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                        Spacer()
                        Stepper(value: $awakenings, in: 0...20) {
                            Text(awakenings == 0 ? "Not that I recall" : "\(awakenings)×")
                                .appFont(13).foregroundStyle(Theme.Palette.muted)
                                .frame(maxWidth: .infinity, alignment: .trailing)
                        }
                        .frame(width: 190)
                        .accessibilityLabel("Times woken during the night")
                        .accessibilityValue("\(awakenings)")
                    }
                    .padding(.vertical, 6)
                }
            }

            InsightCard(label: "About \(durationText) in bed",
                        title: "Roughly is perfect — this is awareness, not tracking accuracy.")

            PrimaryButton(title: "Save check-in", systemImage: "sunrise.fill") {
                guard quality > 0 else { return }
                let entry = SleepEntry(day: AppState.dayString(), bedMinutes: bedMinutes,
                                       wakeMinutes: wakeMinutes, quality: quality, awakenings: awakenings)
                state.upsertSleep(entry)
                backend.mirrorSleep(entry)
                saved.toggle()
            }
            .opacity(quality == 0 ? 0.55 : 1)
            .accessibilityHint(quality == 0 ? "Pick how rested you feel first" : "")
        }
        .celebration(trigger: $saved, accent: Theme.Accent.sleep)
        .onAppear(perform: prefill)
    }

    /// Pre-fill from today's entry (editing) or the previous morning's times.
    private func prefill() {
        guard !prefilled else { return }
        prefilled = true
        if let today = state.sleepEntry() {
            quality = today.quality; bedMinutes = today.bedMinutes
            wakeMinutes = today.wakeMinutes; awakenings = today.awakenings
        } else if let last = state.sleepEntries.first {
            bedMinutes = last.bedMinutes; wakeMinutes = last.wakeMinutes
        }
    }

    private func timeRow(label: String, systemImage: String, minutes: Binding<Int>) -> some View {
        HStack {
            Label(label, systemImage: systemImage)
                .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.soft)
            Spacer()
            DatePicker("", selection: timeBinding(minutes), displayedComponents: .hourAndMinute)
                .labelsHidden()
                .tint(Theme.Accent.sleep)
                .accessibilityLabel(label)
        }
        .padding(.vertical, 2)
    }

    /// Bridge wall-clock minutes ↔ the hour/minute of an arbitrary Date for DatePicker.
    private func timeBinding(_ minutes: Binding<Int>) -> Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(bySettingHour: (minutes.wrappedValue / 60) % 24,
                                      minute: minutes.wrappedValue % 60, second: 0, of: Date()) ?? Date()
            },
            set: { picked in
                let c = Calendar.current.dateComponents([.hour, .minute], from: picked)
                minutes.wrappedValue = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            }
        )
    }
}

// MARK: - Trend strip (real data only)
/// The last 7 nights as soft bars — height is time in bed, tone is felt quality.
/// Gaps stay gaps, and nothing is charted until 3 mornings exist (honest trend).
struct SleepTrendCard: View {
    @EnvironmentObject var state: AppState

    private func weekdayLetter(_ date: Date) -> String {
        let i = Calendar.current.component(.weekday, from: date) - 1
        return ["S", "M", "T", "W", "T", "F", "S"][max(0, min(6, i))]
    }

    var body: some View {
        let week = state.last7Sleep()
        let logged = week.compactMap(\.entry)
        Card {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Last 7 nights").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.text)
                    Spacer()
                    if logged.count >= 3 {
                        let avg = logged.map(\.durationMin).reduce(0, +) / logged.count
                        Text("avg \(avg / 60)h \(String(format: "%02d", avg % 60))m")
                            .appFont(11.5, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                    }
                }
                HStack(alignment: .bottom, spacing: 9) {
                    ForEach(Array(week.enumerated()), id: \.offset) { _, night in
                        VStack(spacing: 5) {
                            if let e = night.entry {
                                Capsule()
                                    .fill(Theme.Accent.sleep.opacity(0.35 + 0.13 * Double(e.quality)))
                                    // 4h → ~22pt … 10h → 56pt; honest floor/cap, no drama.
                                    .frame(height: max(10, min(56, CGFloat(e.durationMin) / 600 * 56)))
                            } else {
                                Circle().fill(Theme.Palette.card)
                                    .frame(width: 7, height: 7)
                                    .overlay(Circle().stroke(Theme.Palette.line))
                            }
                            Text(weekdayLetter(night.date))
                                .appFont(8, weight: .bold).foregroundStyle(Theme.Palette.muted2)
                        }
                        .frame(maxWidth: .infinity, alignment: .bottom)
                    }
                }
                .frame(height: 76, alignment: .bottom)
                if logged.count < 3 {
                    Text(logged.isEmpty
                         ? "Log your first morning to start seeing your nights here."
                         : "Log \(3 - logged.count) more morning\(logged.count == 2 ? "" : "s") to unlock your weekly average.")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(logged.isEmpty ? "Sleep trend: no nights logged yet"
                            : "Sleep trend: \(logged.count) of the last 7 nights logged")
    }
}

// MARK: - Diary history
struct SleepDiaryView: View {
    @EnvironmentObject var state: AppState

    private static let parse: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"; return f
    }()
    private func dayLabel(_ key: String) -> String {
        guard let d = Self.parse.date(from: key) else { return key }
        if Calendar.current.isDateInToday(d) { return "This morning" }
        if Calendar.current.isDateInYesterday(d) { return "Yesterday" }
        return d.formatted(.dateTime.weekday(.wide).day().month())
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Morning check-ins", title: "Sleep Diary",
                       trailingSystemImage: "moon.zzz", accent: Theme.Accent.sleep) {
            SleepTrendCard()
            if state.sleepEntries.isEmpty {
                InsightCard(label: "Nothing here yet",
                            title: "Your mornings will collect here once you start checking in.")
            }
            ForEach(state.sleepEntries) { e in
                Card {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(dayLabel(e.day)).appFont(14, weight: .bold).foregroundStyle(Theme.Palette.text)
                            Text("\(SleepEntry.clockLabel(e.bedMinutes)) → \(SleepEntry.clockLabel(e.wakeMinutes)) · \(e.durationText)\(e.awakenings > 0 ? " · woke \(e.awakenings)×" : "")")
                                .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        }
                        Spacer()
                        HStack(spacing: 2) {
                            ForEach(1...5, id: \.self) { i in
                                Image(systemName: i <= e.quality ? "moon.zzz.fill" : "moon.zzz")
                                    .appFont(10)
                                    .foregroundStyle(i <= e.quality ? Theme.Palette.lav : Theme.Palette.muted2)
                            }
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(dayLabel(e.day)), \(e.durationText) in bed, quality \(e.quality) of 5")
            }
        }
    }
}
