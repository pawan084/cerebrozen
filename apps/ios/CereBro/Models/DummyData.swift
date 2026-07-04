import Foundation

/// All static/dummy content for the app, mirroring the reference copy.
enum Dummy {

    static let userName = "Pawan"

    // Imagery honesty pass (2026-07-04): remote Unsplash URLs removed —
    // every surface renders the branded gradient + SF-Symbol well that
    // `Photo` already draws for empty URLs. Offline-correct, private, and
    // App-Review-safe; bundle real licensed assets here if art ever lands.
    enum Img {
        static let welcome   = ""
        static let calm      = ""
        static let breath    = ""
        static let mood      = ""
        static let plan      = ""
        static let journal   = ""
        static let write     = ""
        static let ground    = ""
        static let voice     = ""
        static let chat      = ""
        static let sleep     = ""
        static let ocean     = ""
        static let meditate  = ""
        static let privacy   = ""
        static let premium   = ""
        static let support   = ""
        static let bell      = ""
    }

    // Home
    static let tonight: [ContentItem] = [
        .init(title: "Rain over quiet hills", subtitle: "Sleep story · 18 min", symbol: "moon.stars", imageURL: Img.welcome),
        .init(title: "3-minute breath", subtitle: "Stress reset", symbol: "wind", imageURL: Img.breath),
        .init(title: "Evening unwind", subtitle: "Soundscape · 30 min", symbol: "waveform", imageURL: Img.ocean)
    ]

    // Mood
    static let moods: [MoodOption] = [
        .init(name: "Good", note: "Clear", symbol: "sparkles"),
        .init(name: "Anxious", note: "Loud thoughts", symbol: "exclamationmark.triangle"),
        .init(name: "Low", note: "Heavy", symbol: "moon"),
        .init(name: "Tired", note: "Need rest", symbol: "drop")
    ]

    // Chat
    static let chat: [ChatMessage] = [
        .init(text: "I keep overthinking about tomorrow's meeting.", isUser: true),
        .init(text: "Let's slow it down. What thought is repeating?", isUser: false),
        .init(text: "I feel like I'll fail.", isUser: true),
        .init(text: "That's a heavy prediction. Want to reframe it or calm the body first?", isUser: false)
    ]

    // Daily plan
    static let planSteps: [PlanStep] = [
        .init(title: "Breathing reset", detail: "3 min · recommended now", symbol: "wind", imageURL: Img.breath, done: true),
        .init(title: "Night journal", detail: "5 min reflection", symbol: "book", imageURL: Img.journal),
        .init(title: "Reminder timing", detail: "Evening private nudge", symbol: "bell", imageURL: Img.bell)
    ]

    // Programs
    static let programs: [ContentItem] = [
        .init(title: "Ease work stress", subtitle: "7-day plan · Breathing + journaling", symbol: "leaf", imageURL: Img.plan),
        .init(title: "Sleep deeper", subtitle: "10-day wind-down program", symbol: "moon.stars", imageURL: Img.sleep),
        .init(title: "Stop overthinking", subtitle: "5-day CBT focus", symbol: "brain", imageURL: Img.write)
    ]

    // Journal history
    static let journalEntries: [JournalEntry] = [
        .init(title: "Meeting pressure", tags: ["Work", "Anxiety"], date: "Today", symbol: "book", imageURL: Img.journal),
        .init(title: "Late-night thoughts", tags: ["Sleep"], date: "Yesterday", symbol: "moon", imageURL: Img.write),
        .init(title: "A small win", tags: ["Gratitude"], date: "Mon", symbol: "sparkles", imageURL: Img.calm)
    ]

    // Sleep
    static let sleepContent: [ContentItem] = [
        .init(title: "Rain over quiet hills", subtitle: "Sleep story · 18 min", symbol: "cloud.rain", imageURL: Img.welcome),
        .init(title: "Ocean breathing", subtitle: "Breathwork · 5 min", symbol: "waveform", imageURL: Img.ocean),
        .init(title: "Deep night drift", subtitle: "Soundscape · 45 min", symbol: "moon.zzz", imageURL: Img.sleep)
    ]

    // Wind-down guide (CBT-I-informed; offline fallback — the server's
    // `wind_down` catalogue kind overrides it when reachable). Awareness copy
    // only, mirroring backend/app/seed.py.
    static let windDown: [ContentItem] = [
        .init(title: "Keep a steady wake time", subtitle: "Anchors your body clock — even after a rough night", symbol: "alarm", imageURL: ""),
        .init(title: "Dim the inputs", subtitle: "Screens down and lights low, 30 minutes before bed", symbol: "moon.haze", imageURL: ""),
        .init(title: "Bed is for sleep", subtitle: "Awake 20+ minutes? Get up, reset gently, return sleepy", symbol: "bed.double", imageURL: ""),
        .init(title: "Slow the body first", subtitle: "Two minutes of soft breathing before lights out", symbol: "wind", imageURL: "")
    ]

    // Meditation library
    static let meditations: [ContentItem] = [
        .init(title: "Morning calm", subtitle: "Start your day · 6 min", symbol: "sun.max", imageURL: Img.calm),
        .init(title: "Soft focus", subtitle: "Deep work · 12 min", symbol: "scope", imageURL: Img.plan),
        .init(title: "Ocean breathing", subtitle: "Breathwork · 5 min", symbol: "waveform", imageURL: Img.ocean),
        .init(title: "Body scan", subtitle: "Release tension · 10 min", symbol: "figure.mind.and.body", imageURL: Img.meditate)
    ]

    // Insights metrics
    static let weeklyMetrics: [Metric] = [
        .init(label: "Calm sessions", value: "12", progress: 0.78),
        .init(label: "Journal entries", value: "5", progress: 0.5),
        .init(label: "Sleep consistency", value: "Improving", progress: 0.62),
        .init(label: "Mood stability", value: "Steady", progress: 0.7)
    ]

    static let baselineMetrics: [Metric] = [
        .init(label: "Stress this week", value: "High", progress: 0.72),
        .init(label: "Sleep quality", value: "Needs support", progress: 0.44)
    ]

    // AI memory / patterns
    static let memoryItems: [ContentItem] = [
        .init(title: "Goal: ease work stress", subtitle: "Set during onboarding", symbol: "target", imageURL: Img.plan),
        .init(title: "Pattern: stress spikes after meetings", subtitle: "Observed 4 times", symbol: "chart.line.uptrend.xyaxis", imageURL: Img.write),
        .init(title: "Preference: calm guide voice", subtitle: "Soft, reflective tone", symbol: "waveform", imageURL: Img.voice)
    ]

    // Pricing (fallback cards — StoreKit displayPrice is authoritative once live)
    static let plans: [PricePlan] = [
        .init(tier: "Free", price: "₹0", detail: "Daily check-ins, breathing & basic journal", featured: false),
        .init(tier: "Premium", price: "₹499/mo", detail: "Full sleep library & richer voice sessions · ₹3,999/yr (two months free)", featured: true),
        .init(tier: "Premium + Human", price: "₹1,499/mo", detail: "Everything, plus priority human handoff · ₹11,999/yr", featured: false)
    ]

    // Offline available items
    static let offline: [ContentItem] = [
        .init(title: "Saved breathing exercise", subtitle: "Available offline", symbol: "wind", imageURL: Img.breath),
        .init(title: "Private journal draft", subtitle: "Saved locally", symbol: "book", imageURL: Img.write),
        .init(title: "Downloaded soundscape", subtitle: "Available offline", symbol: "waveform", imageURL: Img.ocean)
    ]

    // Onboarding language / goals options
    static let languages = ["English", "Hindi", "Hinglish", "Punjabi", "Tamil"]
    static let goals = ["Reduce stress", "Sleep better", "Stop overthinking", "Build confidence", "Feel less alone"]
    // Single-select reminder slot ("Private previews" was removed — it mapped
    // to nothing and made the choice read as a settings grid).
    static let reminderTimes = ["Morning 9 AM", "Evening 7 PM", "No reminders"]

    // MARK: Self-reflection assessment (mirrors backend app/services/assessment.py)
    /// Psychological drivers, selected at the category level.
    static let motivations = ["Focus", "Calm", "Confidence", "Discipline", "Connection"]
    /// Concrete practices, grouped into categories (item-level selection).
    static let goalCategories: [(category: String, items: [String])] = [
        ("Daily Rituals", ["Reduce stress", "Sleep better", "Practice meditation", "Calmer mornings"]),
        ("Personal Development", ["Stop overthinking", "Build confidence", "Feel less alone", "Strengthen willpower"]),
    ]
}
