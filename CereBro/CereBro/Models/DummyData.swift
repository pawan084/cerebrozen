import Foundation

/// All static/dummy content for the app, mirroring the reference copy.
enum Dummy {

    static let userName = "Pawan"

    // Reusable Unsplash imagery (same set the reference design uses).
    enum Img {
        static let welcome   = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1000&q=80"
        static let calm      = "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=1000&q=80"
        static let breath    = "https://images.unsplash.com/photo-1490730141103-6cac27aaab94?auto=format&fit=crop&w=1000&q=80"
        static let mood      = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=1000&q=80"
        static let plan      = "https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1000&q=80"
        static let journal   = "https://images.unsplash.com/photo-1455390582262-044cdead277a?auto=format&fit=crop&w=1000&q=80"
        static let write     = "https://images.unsplash.com/photo-1517842645767-c639042777db?auto=format&fit=crop&w=1000&q=80"
        static let ground    = "https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=1000&q=80"
        static let voice     = "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=1000&q=80"
        static let chat      = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1000&q=80"
        static let sleep     = "https://images.unsplash.com/photo-1444703686981-a3abbc4d4fe3?auto=format&fit=crop&w=1000&q=80"
        static let ocean     = "https://images.unsplash.com/photo-1505142468610-359e7d316be0?auto=format&fit=crop&w=1000&q=80"
        static let meditate  = "https://images.unsplash.com/photo-1499209974431-9dddcece7f88?auto=format&fit=crop&w=1000&q=80"
        static let privacy   = "https://images.unsplash.com/photo-1516321497487-e288fb19713f?auto=format&fit=crop&w=1000&q=80"
        static let premium   = "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=1000&q=80"
        static let support   = "https://images.unsplash.com/photo-1499557354967-2b2d8910bcca?auto=format&fit=crop&w=1000&q=80"
        static let bell      = "https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=1000&q=80"
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

    // Pricing
    static let plans: [PricePlan] = [
        .init(tier: "Free", price: "₹0", detail: "Daily check-ins, breathing & basic journal", featured: false),
        .init(tier: "Premium", price: "₹499/mo", detail: "Sleep library, downloads, unlimited voice", featured: true),
        .init(tier: "Premium + Human", price: "₹1,499/mo", detail: "Everything, plus coach & therapist booking", featured: false)
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
    static let reminderTimes = ["Morning 9 AM", "Evening 7 PM", "Private previews", "No reminders"]

    // MARK: Self-reflection assessment (mirrors backend app/services/assessment.py)
    /// Psychological drivers, selected at the category level.
    static let motivations = ["Focus", "Calm", "Confidence", "Discipline", "Connection"]
    /// Concrete practices, grouped into categories (item-level selection).
    static let goalCategories: [(category: String, items: [String])] = [
        ("Daily Rituals", ["Reduce stress", "Sleep better", "Practice meditation", "Calmer mornings"]),
        ("Personal Development", ["Stop overthinking", "Build confidence", "Feel less alone", "Strengthen will power"]),
    ]
}
