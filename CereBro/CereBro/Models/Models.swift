import Foundation

// Lightweight value types backing the static/dummy content.

struct ContentItem: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let subtitle: String
    let symbol: String
    let imageURL: String
}

struct MoodOption: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let note: String
    let symbol: String
}

struct JournalEntry: Identifiable, Hashable, Codable {
    var id = UUID()
    let title: String
    let tags: [String]
    let date: String
    let symbol: String
    let imageURL: String
}

struct ChatMessage: Identifiable, Hashable, Codable {
    var id = UUID()
    let text: String
    let isUser: Bool
}

/// A recorded mood check-in (persisted; feeds Home + Insights).
struct MoodLog: Identifiable, Hashable, Codable {
    var id = UUID()
    let mood: String
    let note: String
    let symbol: String
    let date: Date
}

/// Persisted privacy/consent choices, shared by onboarding + the Privacy dashboard.
struct Consent: Hashable, Codable {
    var moodHistory = true
    var aiMemory = true
    var voiceStorage = false
    var modelTraining = false
}

struct PlanStep: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let detail: String
    let symbol: String
    let imageURL: String
    var done: Bool = false
}

struct Metric: Identifiable, Hashable {
    let id = UUID()
    let label: String
    let value: String
    let progress: Double
}

struct PricePlan: Identifiable, Hashable {
    let id = UUID()
    let tier: String
    let price: String
    let detail: String
    let featured: Bool
}
