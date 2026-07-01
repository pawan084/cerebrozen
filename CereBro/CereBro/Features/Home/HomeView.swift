import SwiftUI

struct HomeView: View {
    @EnvironmentObject var state: AppState
    @State private var showSearch = false
    @State private var route: HomeRoute?
    @State private var celebrateStreak = false
    @Namespace private var playerZoom

    private var part: DayPart { .current() }

    /// Reflect the most recent mood check-in once one exists.
    private var moodSubtitle: String {
        state.moodLogs.first.map { "Last check-in: \($0.mood)" } ?? "Personalize your next best action"
    }
    /// Reflect plan progress once any step is completed.
    private var planSubtitle: String {
        let total = Dummy.planSteps.count
        let done = Dummy.planSteps.filter { state.completedSteps.contains($0.title) }.count
        return done > 0 ? "\(done) of \(total) done · Breathing · Journal" : "Breathing reset · Journal · Wind-down"
    }

    var body: some View {
        let focus = state.homeFocus(part)
        ScreenScaffold(eyebrow: "Today · \(state.primaryGoal)",
                       title: "\(part.greeting),\n\(Dummy.userName)",
                       trailingSystemImage: "magnifyingglass", trailingAction: { showSearch = true },
                       trailingAccessibilityLabel: "Search", isRoot: true) {
            // One clear next action, chosen from the time of day + today's progress.
            HeroCard(tag: focus.tag, title: focus.title, subtitle: focus.subtitle,
                     cta: focus.cta, imageURL: Dummy.Img.calm) { route = focus.route }
                .entrance(0, y: 22)

            StreakCard(streak: state.currentStreak, best: state.bestStreak, week: state.last7Days())
                .entrance(1)

            SectionTitle(title: part.railTitle).entrance(2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(Array(state.homeRail(part).enumerated()), id: \.element.id) { idx, item in
                        NavigationLink { PlayerView(item: item, zoomNamespace: playerZoom) } label: { MiniCard(item: item) }
                            .buttonStyle(.pressable)
                            .matchedZoomSource(id: item.id, in: playerZoom)
                            .entrance(2 + idx)
                    }
                }
            }

            NavRow(title: "Check how you feel", subtitle: moodSubtitle,
                   systemImage: "heart", imageURL: Dummy.Img.mood, emphasis: true) { MoodCheckinView() }
                .entrance(5)
            NavRow(title: "Today's plan", subtitle: planSubtitle,
                   systemImage: "checkmark.circle", imageURL: Dummy.Img.plan) { DailyPlanView() }
                .entrance(6)
            NavRow(title: "Programs", subtitle: "Guided multi-day plans",
                   systemImage: "sparkles", imageURL: Dummy.Img.premium) { ProgramsView() }
                .entrance(7)
            NavRow(title: "Calm games", subtitle: "Playful resets for a busy mind",
                   systemImage: "gamecontroller", imageURL: Dummy.Img.breath) { GamesHubView() }
                .entrance(8)
        }
        .navigationDestination(isPresented: $showSearch) { SearchView() }
        .navigationDestination(item: $route) { r in
            switch r {
            case .mood:    MoodCheckinView()
            case .plan:    DailyPlanView()
            case .breathe: BreathingView()
            case .sleep:   PlayerView(item: Dummy.sleepContent[0])
            }
        }
        // Celebrate a streak milestone the moment it's reached (fires once).
        .onChange(of: state.newMilestone) { _, m in
            if m != nil { celebrateStreak = true; state.newMilestone = nil }
        }
        .celebration(trigger: $celebrateStreak)
    }
}

// MARK: - Streak ("mindful days")
/// A calm, non-punitive consistency nudge: how many days in a row you've shown
/// up, with a soft last-7-days ring. Deliberately gentle — "no pressure" framing,
/// today is highlighted, and missed days simply rest rather than scold.
struct StreakCard: View {
    let streak: Int
    let best: Int
    let week: [(date: Date, active: Bool)]

    private func weekdayLetter(_ date: Date) -> String {
        let i = Calendar.current.component(.weekday, from: date) - 1   // 0=Sun
        return ["S", "M", "T", "W", "T", "F", "S"][max(0, min(6, i))]
    }
    private func isToday(_ date: Date) -> Bool { Calendar.current.isDateInToday(date) }
    private var isMilestone: Bool { AppState.milestones.contains(streak) }

    var body: some View {
        Card {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(Theme.Gradient.orb).frame(width: 44, height: 44)
                    Image(systemName: "leaf.fill")
                        .appFont(17, weight: .semibold).foregroundStyle(Theme.Palette.ink)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(streak == 0 ? "Begin your streak" : "\(streak)-day streak")
                        .appFont(16, weight: .bold).foregroundStyle(Theme.Palette.text)
                    Text(isMilestone ? "🎉 \(streak)-day milestone — beautifully done"
                         : (streak == 0 ? "Show up once today to start"
                                        : "Best \(best) days · gentle, no pressure"))
                        .appFont(11.5).foregroundStyle(isMilestone ? Theme.Palette.lav : Theme.Palette.muted)
                }
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    ForEach(Array(week.enumerated()), id: \.offset) { _, day in
                        VStack(spacing: 5) {
                            Circle()
                                .fill(day.active ? Theme.Palette.lav : Theme.Palette.card)
                                .frame(width: 9, height: 9)
                                .overlay(Circle().stroke(isToday(day.date) ? Theme.Palette.soft
                                                          : (day.active ? .clear : Theme.Palette.line),
                                                         lineWidth: 1))
                            Text(weekdayLetter(day.date))
                                .appFont(8, weight: .bold).foregroundStyle(Theme.Palette.muted2)
                        }
                    }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(streak == 0 ? "No active streak yet"
                            : "\(streak) day streak, best \(best) days")
    }
}

struct MiniCard: View {
    let item: ContentItem
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Photo(url: item.imageURL, symbol: item.symbol)
                .frame(width: 145, height: 78)
                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
            Text(item.title).displayFont(15).foregroundStyle(Theme.Palette.soft).padding(.top, 8).lineLimit(1)
            Text(item.subtitle).appFont(11.5).foregroundStyle(Theme.Palette.muted).padding(.top, 2)
        }
        .frame(width: 145)
        .padding(10)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.line))
    }
}

// MARK: - Mood check-in
struct MoodCheckinView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var selected: MoodOption.ID?
    @State private var saved = false
    var body: some View {
        ScreenScaffold(eyebrow: "Mood, intensity and trigger", title: "Mood Check-in",
                       trailingSystemImage: "heart", accent: Theme.Accent.warm) {
            ToolBanner(imageURL: Dummy.Img.mood, symbol: "heart",
                       caption: "Name how you feel — we'll shape your next step.",
                       accent: Theme.Accent.warm)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(Dummy.moods) { mood in
                    Button { selected = mood.id } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Image(systemName: mood.symbol).appFont(18).foregroundStyle(Theme.Palette.soft)
                            Text(mood.name).appFont(15, weight: .semibold).foregroundStyle(Theme.Palette.soft).padding(.top, 4)
                            Text(mood.note).appFont(12).foregroundStyle(Theme.Palette.muted2)
                        }
                        .frame(maxWidth: .infinity, minHeight: 82, alignment: .topLeading)
                        .padding(12)
                        .background(selected == mood.id ? Theme.Palette.cardEmphasis : Theme.Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(selected == mood.id ? Theme.Palette.lav.opacity(0.7) : Theme.Palette.line,
                                    lineWidth: selected == mood.id ? 1.5 : 1))
                        .scaleEffect(selected == mood.id ? 1.03 : 1)
                        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: selected)
                    }
                    .buttonStyle(.pressable)
                }
            }
            .sensoryFeedback(.selection, trigger: selected)
            InsightCard(label: "Next best action", title: "Ground the body first, then reflect.")
            PrimaryButton(title: "Start gentle support", systemImage: "mic.fill") {
                guard let id = selected, let mood = Dummy.moods.first(where: { $0.id == id }) else { return }
                let log = MoodLog(mood: mood.name, note: mood.note, symbol: mood.symbol, date: Date())
                state.moodLogs.insert(log, at: 0)
                state.recordActivity()
                backend.mirrorMood(log)
                saved.toggle()
            }
        }
        .celebration(trigger: $saved, accent: Theme.Accent.warm)
    }
}

// MARK: - Daily plan
struct DailyPlanView: View {
    @EnvironmentObject var state: AppState
    @State private var recheck = false
    private var doneCount: Int { Dummy.planSteps.filter { state.completedSteps.contains($0.title) }.count }
    var body: some View {
        ScreenScaffold(eyebrow: "Agentic plan", title: "Daily Plan", trailingSystemImage: "checkmark.circle") {
            HeroCard(tag: "Agentic plan", title: "Why this plan changed",
                     subtitle: "Because stress spikes after meetings and sleep is inconsistent.",
                     cta: "Update plan", imageURL: Dummy.Img.plan) { recheck = true }
            InsightCard(label: "Today's progress",
                        title: "\(doneCount) of \(Dummy.planSteps.count) steps complete")
            ForEach(Dummy.planSteps) { step in
                PlanStepRow(step: step)
            }
        }
        .navigationDestination(isPresented: $recheck) { MoodCheckinView() }
    }
}

/// A plan step with a tappable completion check (persisted) plus a tap-through
/// to the breathing tool. The check and the row are siblings so each stays its
/// own control.
struct PlanStepRow: View {
    let step: PlanStep
    @EnvironmentObject var state: AppState
    private var done: Bool { state.completedSteps.contains(step.title) }
    var body: some View {
        HStack(spacing: 8) {
            Button {
                if done { state.completedSteps.remove(step.title) }
                else { state.completedSteps.insert(step.title); state.recordActivity() }
            } label: {
                Image(systemName: done ? "checkmark.circle.fill" : "circle")
                    .appFont(22, weight: .semibold)
                    .foregroundStyle(done ? Theme.Palette.lav : Theme.Palette.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(done ? "Mark \(step.title) not done" : "Mark \(step.title) done")
            .sensoryFeedback(.success, trigger: done)

            NavRow(title: step.title, subtitle: step.detail, systemImage: step.symbol,
                   imageURL: step.imageURL, emphasis: done) { destination }
        }
    }

    /// Each step opens its own tool, not always breathing.
    @ViewBuilder private var destination: some View {
        switch step.symbol {
        case "book": JournalEntryView()
        case "bell": ProfileView()          // reminder timing lives in settings
        default:     BreathingView()
        }
    }
}

// MARK: - Programs
struct ProgramsView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Guided multi-day plans", title: "Programs", trailingSystemImage: "sparkles") {
            HeroCard(tag: "Featured", title: "Ease work stress",
                     subtitle: "A 7-day agentic plan built around your baseline.",
                     cta: "Start", imageURL: Dummy.Img.plan)
            ForEach(Dummy.programs) { p in
                NavRow(title: p.title, subtitle: p.subtitle, systemImage: p.symbol, imageURL: p.imageURL) { DailyPlanView() }
            }
        }
    }
}

// MARK: - Search
struct SearchView: View {
    @State private var query = ""

    /// Searchable catalogue across meditations + sleep content.
    private var catalogue: [ContentItem] { Dummy.meditations + Dummy.sleepContent }
    private var results: [ContentItem] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return Dummy.meditations }
        return catalogue.filter {
            $0.title.localizedCaseInsensitiveContains(q) || $0.subtitle.localizedCaseInsensitiveContains(q)
        }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Search state", title: "Search", trailingSystemImage: "magnifyingglass") {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.Palette.muted)
                TextField("Search calm, sleep, journal…", text: $query)
                    .foregroundStyle(Theme.Palette.text)
                if !query.isEmpty {
                    Button { query = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.Palette.muted)
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal, 14).frame(height: 50)
            .background(Theme.Palette.field)
            .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 17, style: .continuous).stroke(Theme.Palette.line))

            SectionTitle(title: query.isEmpty ? "Suggested" : "Results", trailing: nil)
            if results.isEmpty {
                InsightCard(label: "No matches", title: "Nothing found for “\(query)”.",
                            detail: "Try a calmer keyword like sleep, breath, or focus.")
            } else {
                ForEach(results) { m in
                    NavRow(title: m.title, subtitle: m.subtitle, systemImage: m.symbol, imageURL: m.imageURL) { PlayerView(item: m) }
                }
            }
        }
    }
}
