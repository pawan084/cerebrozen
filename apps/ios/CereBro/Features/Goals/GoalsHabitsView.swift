import SwiftUI

/// The two things in this app the *user* defines — everything else (the daily
/// plan, the patterns, the suggestions) is generated for them.
///
/// Deliberately not gamified. Habits show which of the last seven days
/// happened and a plain count; there is no streak, no chain, no "don't break
/// it". The server doesn't send one and this screen doesn't compute one — a
/// broken-chain counter on mental-health activity is exactly what the
/// anti-dark-pattern rules here exist to prevent.
struct GoalsHabitsView: View {
    @EnvironmentObject var backend: BackendService

    @State private var goals: [RemoteGoal] = []
    @State private var habits: [RemoteHabit] = []
    @State private var goalDraft = ""
    @State private var habitDraft = ""
    @State private var cueDraft = ""
    @State private var loading = true
    @State private var error: String?

    /// The last seven calendar days, oldest first, as (iso, single-letter).
    private var week: [(iso: String, label: String)] {
        let iso = DateFormatter()
        iso.locale = Locale(identifier: "en_US_POSIX")
        iso.dateFormat = "yyyy-MM-dd"
        let letters = ["S", "M", "T", "W", "T", "F", "S"]
        let cal = Calendar.current
        return (0..<7).reversed().compactMap { offset in
            guard let day = cal.date(byAdding: .day, value: -offset, to: Date()) else { return nil }
            let weekday = cal.component(.weekday, from: day) - 1
            return (iso.string(from: day), letters[max(0, min(6, weekday))])
        }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Yours to define", title: "Goals & habits",
                       trailingSystemImage: "target") {
            Text("The rest of CereBro suggests things to you. This is the part you decide.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            if let error {
                Text(error).appFont(12).foregroundStyle(Theme.Palette.danger)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if !backend.isConnected {
                Card {
                    Text("Sign in to keep goals and habits in sync across your devices.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else if loading {
                Text("Loading…").appFont(13).foregroundStyle(Theme.Palette.muted)
            } else {
                goalsCard
                habitsCard
            }
        }
        .task { await reload() }
    }

    private var goalsCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text("What you're working towards")
                    .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.soft)
                if goals.isEmpty {
                    Text("Nothing yet. One is plenty to start with.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                } else {
                    ForEach(goals) { goal in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(goal.title)
                                .appFont(14, weight: .bold).foregroundStyle(Theme.Palette.text)
                            if !goal.why.isEmpty {
                                Text(goal.why)
                                    .appFont(12).foregroundStyle(Theme.Palette.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            HStack(spacing: 16) {
                                Button("Make this today's plan") { decompose(goal) }
                                    .appFont(12.5, weight: .semibold)
                                    .foregroundStyle(Theme.Palette.lav)
                                Button("Done") { setStatus(goal, "achieved") }
                                    .appFont(12.5).foregroundStyle(Theme.Palette.success)
                                // Letting a goal go is an outcome, not a failure.
                                Button("Let it go") { setStatus(goal, "released") }
                                    .appFont(12.5).foregroundStyle(Theme.Palette.muted)
                            }
                            .frame(minHeight: 44)
                        }
                        .padding(.top, 2)
                    }
                }
                HStack(spacing: 10) {
                    TextField("Something you're working towards…", text: $goalDraft)
                        .appFont(13).foregroundStyle(Theme.Palette.soft)
                    Button("Add") { addGoal() }
                        .appFont(13, weight: .semibold)
                        .foregroundStyle(goalDraft.trimmed.isEmpty
                                         ? Theme.Palette.muted2 : Theme.Palette.lav)
                        .disabled(goalDraft.trimmed.isEmpty)
                        .frame(minHeight: 44)
                }
            }
        }
    }

    private var habitsCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text("Small things you repeat")
                    .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.soft)
                Text("The last seven days, so you can see the shape of it. No streak to break.")
                    .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                    .fixedSize(horizontal: false, vertical: true)
                if habits.isEmpty {
                    Text("Nothing yet.").appFont(13).foregroundStyle(Theme.Palette.muted)
                } else {
                    ForEach(habits) { habit in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(habit.title)
                                .appFont(14, weight: .bold).foregroundStyle(Theme.Palette.text)
                            if !habit.cue.isEmpty {
                                Text(habit.cue).appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                            }
                            HStack(spacing: 6) {
                                ForEach(week, id: \.iso) { day in
                                    let hit = habit.recent_days.contains(day.iso)
                                    Text(day.label)
                                        .appFont(10, weight: .bold)
                                        .foregroundStyle(hit ? Theme.Palette.ink : Theme.Palette.muted2)
                                        .frame(width: 22, height: 22)
                                        .background(hit ? Theme.Palette.lav : Theme.Palette.field,
                                                    in: Circle())
                                }
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(habit.recent_days.count) of the last 7 days")
                            Text("\(habit.recent_days.count) of the last 7 days")
                                .appFont(11).foregroundStyle(Theme.Palette.muted2)
                            Button(habit.done_today ? "Done today ✓" : "Mark today") {
                                toggle(habit)
                            }
                            .appFont(13, weight: .semibold)
                            .foregroundStyle(Theme.Palette.lav)
                            .frame(minHeight: 44)
                        }
                        .padding(.top, 2)
                    }
                }
                VStack(spacing: 8) {
                    TextField("A small thing to repeat…", text: $habitDraft)
                        .appFont(13).foregroundStyle(Theme.Palette.soft)
                    HStack(spacing: 10) {
                        // An implementation intention — "after I brush my teeth"
                        // — is the one habit mechanism with decent evidence, so
                        // the cue gets its own field rather than a placeholder.
                        TextField("After I…", text: $cueDraft)
                            .appFont(13).foregroundStyle(Theme.Palette.soft)
                        Button("Add") { addHabit() }
                            .appFont(13, weight: .semibold)
                            .foregroundStyle(habitDraft.trimmed.isEmpty
                                             ? Theme.Palette.muted2 : Theme.Palette.lav)
                            .disabled(habitDraft.trimmed.isEmpty)
                            .frame(minHeight: 44)
                    }
                }
            }
        }
    }

    // MARK: - Actions

    private func reload() async {
        guard backend.isConnected else { loading = false; return }
        goals = (try? await APIClient.shared.goals()) ?? []
        habits = (try? await APIClient.shared.habits()) ?? []
        loading = false
    }

    private func run(_ work: @escaping () async throws -> Void, failure: String) {
        Task {
            do {
                try await work()
                error = nil
            } catch {
                self.error = failure
            }
            await reload()
        }
    }

    private func addGoal() {
        let title = goalDraft.trimmed
        guard !title.isEmpty else { return }
        run({ try await APIClient.shared.addGoal(title); goalDraft = "" },
            failure: "Couldn't save that goal.")
    }

    private func setStatus(_ goal: RemoteGoal, _ status: String) {
        run({ try await APIClient.shared.setGoalStatus(id: goal.id, status: status) },
            failure: "Couldn't update that goal.")
    }

    private func decompose(_ goal: RemoteGoal) {
        run({ try await APIClient.shared.decomposeGoal(id: goal.id) },
            failure: "Couldn't build a plan from that.")
    }

    private func addHabit() {
        let title = habitDraft.trimmed
        guard !title.isEmpty else { return }
        run({
            try await APIClient.shared.addHabit(title: title, cue: cueDraft.trimmed)
            habitDraft = ""; cueDraft = ""
        }, failure: "Couldn't save that habit.")
    }

    private func toggle(_ habit: RemoteHabit) {
        run({ try await APIClient.shared.setHabitToday(id: habit.id, done: !habit.done_today) },
            failure: "Couldn't record that.")
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
