import SwiftUI

@main
struct CereBroApp: App {
    @StateObject private var state = AppState()
    @StateObject private var backend = BackendService()

    init() {
        // Bare AsyncImage relies on URLCache.shared, whose default capacity is
        // small enough that the app's Unsplash imagery is evicted and re-fetched
        // on every scroll/return. A larger shared cache keeps them resident.
        URLCache.shared = URLCache(memoryCapacity: 64 * 1024 * 1024,   // 64 MB
                                   diskCapacity: 256 * 1024 * 1024)     // 256 MB
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(state)
                .environmentObject(backend)
                .preferredColorScheme(.dark)
                .tint(Theme.Palette.soft)
                // Support Dynamic Type, but cap scaling so the tightly-tuned
                // fixed-height layouts don't clip at the largest sizes.
                .dynamicTypeSize(.xSmall ... .accessibility1)
        }
    }
}

/// App-wide state + lightweight persistence layer. Real user data (journal,
/// chat, mood history, plan progress, consent, onboarding choices) is held here
/// and written through to `UserDefaults` as JSON so it survives relaunches.
final class AppState: ObservableObject {
    private enum Key {
        static let onboarded = "hasOnboarded"
        static let journal = "journalEntries"
        static let chat = "chatHistory"
        static let moods = "moodLogs"
        static let steps = "completedSteps"
        static let consent = "consent"
        static let goals = "selectedGoals"
        static let motivations = "selectedMotivations"
        static let language = "language"
        static let companion = "companion"
        static let activeDays = "activeDays"
        static let journalLock = "journalLocked"
        static let favSleep = "favoriteSleep"
        static let crisisRegion = "crisisRegion"
        static let lastMilestone = "lastStreakMilestone"
        static let reminderOn = "reminderEnabled"
        static let reminderHour = "reminderHour"
    }

    /// Streak milestones worth celebrating (days).
    static let milestones = [3, 7, 14, 30, 60, 100, 150, 365]

    /// Persisted so onboarding is shown only once (until reset from Profile).
    @Published var hasOnboarded: Bool {
        didSet { UserDefaults.standard.set(hasOnboarded, forKey: Key.onboarded) }
    }
    @Published var selectedTab: Tab = .home

    // MARK: Persisted user data
    @Published var journalEntries: [JournalEntry] { didSet { Self.save(journalEntries, Key.journal) } }
    @Published var chatHistory: [ChatMessage]     { didSet { Self.save(chatHistory, Key.chat) } }
    @Published var moodLogs: [MoodLog]            { didSet { Self.save(moodLogs, Key.moods) } }
    /// Completed daily-plan steps, keyed by step title (titles are stable).
    @Published var completedSteps: Set<String>    { didSet { Self.save(Array(completedSteps), Key.steps) } }
    @Published var consent: Consent               { didSet { Self.save(consent, Key.consent) } }
    /// Calendar days (yyyy-MM-dd) on which the user showed up — the source of the
    /// gentle "mindful days" streak. Stored as a capped day list so we can render
    /// both the running streak and the last-7-days ring.
    @Published private(set) var activeDays: [String] { didSet { Self.save(activeDays, Key.activeDays) } }

    // MARK: Persisted onboarding choices
    @Published var selectedGoals: [String] { didSet { Self.save(selectedGoals, Key.goals) } }
    @Published var selectedMotivations: [String] { didSet { Self.save(selectedMotivations, Key.motivations) } }
    @Published var language: String        { didSet { UserDefaults.standard.set(language, forKey: Key.language) } }
    @Published var companion: String       { didSet { UserDefaults.standard.set(companion, forKey: Key.companion) } }
    /// Gate the Journal tab behind Face ID / passcode (off by default).
    @Published var journalLocked: Bool     { didSet { UserDefaults.standard.set(journalLocked, forKey: Key.journalLock) } }
    /// Favorited sleep stories/sounds, keyed by their stable title.
    @Published private(set) var favoriteSleep: Set<String> { didSet { Self.save(Array(favoriteSleep), Key.favSleep) } }
    /// Crisis-resources region override. "" = automatic (device region).
    @Published var crisisRegion: String    { didSet { UserDefaults.standard.set(crisisRegion, forKey: Key.crisisRegion) } }
    /// Highest streak milestone already celebrated (so we fire each one once).
    private var lastMilestone: Int         { didSet { UserDefaults.standard.set(lastMilestone, forKey: Key.lastMilestone) } }
    /// Transient: a milestone just reached this session (Home fires a celebration).
    @Published var newMilestone: Int?
    /// Daily check-in reminder (the habit-loop trigger).
    @Published var reminderEnabled: Bool { didSet { UserDefaults.standard.set(reminderEnabled, forKey: Key.reminderOn) } }
    /// Hour of day (0–23) for the daily reminder. Default 21:00 (evening).
    @Published var reminderHour: Int { didSet { UserDefaults.standard.set(reminderHour, forKey: Key.reminderHour) } }

    init() {
        // UI tests pass `-resetState YES` to start each run from seeded defaults,
        // so screenshots are deterministic regardless of prior writes.
        // UI tests / screenshots pass `-resetState YES`: wipe writes AND seed a
        // small living demo streak so captures are deterministic. Real users get
        // an honest empty start (no fake streak, no pre-completed step).
        let seedDemo = UserDefaults.standard.bool(forKey: "resetState")
        if seedDemo {
            [Key.journal, Key.chat, Key.moods, Key.steps, Key.consent,
             Key.goals, Key.motivations, Key.language, Key.companion, Key.activeDays,
             Key.journalLock, Key.favSleep, Key.crisisRegion, Key.lastMilestone,
             Key.reminderOn, Key.reminderHour,
             "cerebro_access_token"].forEach {   // also drop any cloud session
                UserDefaults.standard.removeObject(forKey: $0)
            }
        }
        hasOnboarded   = UserDefaults.standard.bool(forKey: Key.onboarded)
        journalEntries = Self.load([JournalEntry].self, Key.journal) ?? Dummy.journalEntries
        chatHistory    = Self.load([ChatMessage].self, Key.chat) ?? Dummy.chat
        moodLogs       = Self.load([MoodLog].self, Key.moods) ?? []
        completedSteps = Set(Self.load([String].self, Key.steps)
                             ?? (seedDemo ? Dummy.planSteps.filter(\.done).map(\.title) : []))
        consent        = Self.load(Consent.self, Key.consent) ?? Consent()
        selectedGoals  = Self.load([String].self, Key.goals) ?? ["Reduce stress", "Sleep better"]
        selectedMotivations = Self.load([String].self, Key.motivations) ?? ["Focus", "Calm"]
        language       = UserDefaults.standard.string(forKey: Key.language) ?? "English"
        companion      = UserDefaults.standard.string(forKey: Key.companion) ?? "Calm Guide"
        activeDays     = Self.load([String].self, Key.activeDays) ?? (seedDemo ? Self.seededActiveDays() : [])
        journalLocked  = UserDefaults.standard.bool(forKey: Key.journalLock)
        favoriteSleep  = Set(Self.load([String].self, Key.favSleep) ?? [])
        crisisRegion   = UserDefaults.standard.string(forKey: Key.crisisRegion) ?? ""
        lastMilestone  = UserDefaults.standard.integer(forKey: Key.lastMilestone)
        reminderEnabled = UserDefaults.standard.bool(forKey: Key.reminderOn)
        reminderHour   = UserDefaults.standard.object(forKey: Key.reminderHour) as? Int ?? 21
    }

    /// Wipe all stored user data (used by "Reset & view onboarding").
    func resetAll() {
        journalEntries = Dummy.journalEntries
        chatHistory = Dummy.chat
        moodLogs = []
        completedSteps = []
        consent = Consent()
        selectedGoals = ["Reduce stress", "Sleep better"]
        selectedMotivations = ["Focus", "Calm"]
        language = "English"
        companion = "Calm Guide"
        activeDays = []
        journalLocked = false
        favoriteSleep = []
        crisisRegion = ""
        lastMilestone = 0
        newMilestone = nil
        reminderEnabled = false
        reminderHour = 21
        ReminderManager.cancel()
        hasOnboarded = false
    }

    // MARK: - Sleep favorites

    func isSleepFavorite(_ title: String) -> Bool { favoriteSleep.contains(title) }
    func toggleSleepFavorite(_ title: String) {
        if favoriteSleep.contains(title) { favoriteSleep.remove(title) }
        else { favoriteSleep.insert(title) }
    }

    // MARK: - Streak ("mindful days")

    /// `yyyy-MM-dd` in a fixed locale so day boundaries are stable across the app.
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    private static func dayKey(_ date: Date) -> String {
        dayFormatter.string(from: Calendar.current.startOfDay(for: date))
    }
    /// A small, friendly default so the demo shows a living streak (the last few
    /// days). Real activity extends it via `recordActivity()`.
    private static func seededActiveDays() -> [String] {
        (0..<3).compactMap { Calendar.current.date(byAdding: .day, value: -$0, to: Date()) }
            .map(dayKey).reversed()
    }

    /// Mark today as a "mindful day". Idempotent within a calendar day; called
    /// whenever the user completes a meaningful action (mood, journal, plan step).
    func recordActivity(on date: Date = Date()) {
        let key = Self.dayKey(date)
        guard !activeDays.contains(key) else { return }
        activeDays = Array((activeDays + [key]).suffix(120))   // cap stored history
        // Celebrate crossing a streak milestone (each fires once, ever).
        let streak = currentStreak
        if let reached = Self.milestones.last(where: { $0 <= streak }), reached > lastMilestone {
            lastMilestone = reached
            newMilestone = reached
        }
    }

    private var activeDaySet: Set<String> { Set(activeDays) }

    /// Consecutive days up to today, forgiving a single missed day within the run
    /// (a gentle "grace day" so one slip doesn't erase a long streak). Today is
    /// optional — counts through yesterday so it doesn't read 0 before the day's
    /// first action.
    var currentStreak: Int {
        let cal = Calendar.current
        let set = activeDaySet
        var day = cal.startOfDay(for: Date())
        if !set.contains(Self.dayKey(day)) {
            day = cal.date(byAdding: .day, value: -1, to: day) ?? day
        }
        var count = 0
        var graceUsed = false
        while true {
            if set.contains(Self.dayKey(day)) {
                count += 1
            } else if !graceUsed && count > 0 {
                graceUsed = true          // forgive one gap inside the run
            } else {
                break                     // a second miss (or empty start) ends it
            }
            guard let prev = cal.date(byAdding: .day, value: -1, to: day) else { break }
            day = prev
        }
        return count
    }

    /// Longest run ever recorded (never less than the current streak).
    var bestStreak: Int {
        let cal = Calendar.current
        let set = activeDaySet
        var best = 0
        for key in set {
            guard let d = Self.dayFormatter.date(from: key) else { continue }
            let prev = cal.date(byAdding: .day, value: -1, to: d).map(Self.dayKey)
            if let prev, set.contains(prev) { continue }   // not a run start
            var len = 0, cur: Date? = d
            while let c = cur, set.contains(Self.dayKey(c)) {
                len += 1
                cur = cal.date(byAdding: .day, value: 1, to: c)
            }
            best = max(best, len)
        }
        return max(best, currentStreak)
    }

    /// The last 7 calendar days (oldest → today) with whether each was active —
    /// powers the week ring on Home.
    func last7Days() -> [(date: Date, active: Bool)] {
        let cal = Calendar.current
        let set = activeDaySet
        let today = cal.startOfDay(for: Date())
        return (0..<7).reversed().compactMap { offset in
            guard let d = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            return (d, set.contains(Self.dayKey(d)))
        }
    }

    private static func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
    private static func load<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    enum Tab: Hashable { case home, sleep, talk, journal, you }
}
