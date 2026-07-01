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
    let date: String            // display label ("Today"/"Yesterday"/"Mon")
    let symbol: String
    let imageURL: String
    /// The full written text. Stored on-device so History reopens the real
    /// entry, not a placeholder. Optional so older persisted JSON still decodes.
    var body: String = ""
    /// True creation instant (for chronological sort / "on this day").
    var createdAt: Date = Date()

    init(id: UUID = UUID(), title: String, tags: [String], date: String,
         symbol: String, imageURL: String, body: String = "", createdAt: Date = Date()) {
        self.id = id; self.title = title; self.tags = tags; self.date = date
        self.symbol = symbol; self.imageURL = imageURL; self.body = body; self.createdAt = createdAt
    }

    // Tolerant decoding: entries persisted before `body`/`createdAt` existed
    // simply default those fields instead of failing to decode.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(UUID.self, forKey: .id)) ?? UUID()
        title = try c.decode(String.self, forKey: .title)
        tags = (try? c.decode([String].self, forKey: .tags)) ?? []
        date = (try? c.decode(String.self, forKey: .date)) ?? "Today"
        symbol = (try? c.decode(String.self, forKey: .symbol)) ?? "book"
        imageURL = (try? c.decode(String.self, forKey: .imageURL)) ?? ""
        body = (try? c.decode(String.self, forKey: .body)) ?? ""
        createdAt = (try? c.decode(Date.self, forKey: .createdAt)) ?? Date()
    }
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
