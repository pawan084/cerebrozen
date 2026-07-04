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

/// A logged night of sleep (morning check-in; persisted; feeds Sleep + Insights).
/// Times are wall-clock minutes since midnight — the diary describes the user's
/// night, not instants, so no timezone math applies (mirrors the server model).
struct SleepEntry: Identifiable, Hashable, Codable {
    var id = UUID()
    /// yyyy-MM-dd of the wake-up morning — one entry per day, upserted.
    let day: String
    var bedMinutes: Int
    var wakeMinutes: Int
    /// 1–5 felt restfulness.
    var quality: Int
    var awakenings: Int = 0
    /// "manual" | "healthkit" — whether Apple Health pre-filled the times
    /// (the user still confirmed). Mirrors the server enum.
    var source: String = "manual"

    init(id: UUID = UUID(), day: String, bedMinutes: Int, wakeMinutes: Int,
         quality: Int, awakenings: Int = 0, source: String = "manual") {
        self.id = id; self.day = day; self.bedMinutes = bedMinutes
        self.wakeMinutes = wakeMinutes; self.quality = quality
        self.awakenings = awakenings; self.source = source
    }

    // Tolerant decoding: entries persisted before `source` existed default it
    // instead of failing to decode (same pattern as JournalEntry).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(UUID.self, forKey: .id)) ?? UUID()
        day = try c.decode(String.self, forKey: .day)
        bedMinutes = try c.decode(Int.self, forKey: .bedMinutes)
        wakeMinutes = try c.decode(Int.self, forKey: .wakeMinutes)
        quality = try c.decode(Int.self, forKey: .quality)
        awakenings = (try? c.decode(Int.self, forKey: .awakenings)) ?? 0
        source = (try? c.decode(String.self, forKey: .source)) ?? "manual"
    }

    /// Night length; a bedtime after midnight is same-day, else previous evening.
    var durationMin: Int {
        wakeMinutes > bedMinutes ? wakeMinutes - bedMinutes : 24 * 60 - bedMinutes + wakeMinutes
    }
    var durationText: String { "\(durationMin / 60)h \(String(format: "%02d", durationMin % 60))m" }
    /// "HH:mm:ss" for the backend `/sleep` payload.
    static func apiTime(_ minutes: Int) -> String {
        String(format: "%02d:%02d:00", (minutes / 60) % 24, minutes % 60)
    }
    /// "10:45 pm"-style label for rows.
    static func clockLabel(_ minutes: Int) -> String {
        let h24 = (minutes / 60) % 24, m = minutes % 60
        let h12 = h24 % 12 == 0 ? 12 : h24 % 12
        return String(format: "%d:%02d %@", h12, m, h24 < 12 ? "am" : "pm")
    }
}

/// Persisted privacy/consent choices, shared by onboarding + the Privacy
/// dashboard. One flag per data category (DPDP itemization) — the server
/// enforces each at its read sites.
struct Consent: Hashable, Codable {
    var moodHistory = true
    var aiMemory = true
    var voiceStorage = false
    var modelTraining = false
    var journalMemory = true
    var sleepHistory = true
}

extension Consent {
    /// Tolerant decoding so pre-itemization stored consent keeps the user's
    /// choices: the journal category inherits the old AI-memory umbrella.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        moodHistory = try c.decodeIfPresent(Bool.self, forKey: .moodHistory) ?? true
        aiMemory = try c.decodeIfPresent(Bool.self, forKey: .aiMemory) ?? true
        voiceStorage = try c.decodeIfPresent(Bool.self, forKey: .voiceStorage) ?? false
        modelTraining = try c.decodeIfPresent(Bool.self, forKey: .modelTraining) ?? false
        journalMemory = try c.decodeIfPresent(Bool.self, forKey: .journalMemory) ?? aiMemory
        sleepHistory = try c.decodeIfPresent(Bool.self, forKey: .sleepHistory) ?? true
    }
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
