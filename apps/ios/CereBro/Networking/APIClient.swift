import Foundation

// MARK: - Wire models (mirror the FastAPI schemas)

struct AuthTokens: Codable { let access_token: String; let refresh_token: String }

struct RemoteUser: Codable, Identifiable {
    let id: String
    let email: String
    let name: String
    let language: String
    let companion: String
    let goals: [String]
    /// Self-reflection assessment drivers (optional for older responses).
    let motivations: [String]?
    /// Effective crisis region (optional for older responses).
    let region: String?
    /// Subscription entitlement ("free" | "premium" | "premium_human"). The
    /// tier the server will *enforce*, which is not always the stored column:
    /// an organisation can sponsor a seat.
    let subscription_tier: String?
    /// True when the tier above is paid for by an organisation rather than
    /// bought. Optional so a response without it decodes as "not sponsored"
    /// rather than failing the whole profile.
    let sponsored: Bool?
    /// Compliance attestation timestamps (ISO8601), nil until recorded.
    let age_confirmed_at: String?
    let ai_disclosure_ack_at: String?
}

/// Consent snapshot returned by the consent endpoints.
struct RemoteConsent: Codable {
    let mood_history: Bool
    let ai_memory: Bool
    let voice_storage: Bool
    let model_training: Bool
    let journal_memory: Bool?
    let sleep_history: Bool?
}

/// The person to notify if a crisis is detected (consent-gated).
struct RemoteTrustedContact: Codable {
    var name: String = ""
    var method: String = "email"
    var value: String = ""
    var relationship: String = ""
    var notify_consent: Bool = false
}

/// One tappable conversation starter generated from the self-reflection.
struct RemoteTopic: Codable, Identifiable, Equatable { let id: Int; let topic: String }
struct TopicsResponse: Codable { let topics: [RemoteTopic]; let source: String }

struct RemoteMood: Codable, Identifiable {
    let id: String
    let mood: String
    let note: String
    let symbol: String
    let intensity: Int
    let created_at: String
}

/// Active multi-day journey — the day is computed server-side from the start
/// date (nothing to advance, nothing to fail).
/// One day of a program — the `{title, body}` the server serves for the day the
/// user is actually on (`routes/programs.py::_today_guide`, clamped to the last
/// guide so a long enrollment never errors).
struct RemoteDayGuide: Codable, Equatable {
    let title: String
    let body: String
    /// Blank guides are treated as no guide, matching Android's `parseTodayGuide`.
    var isEmpty: Bool {
        title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

struct RemoteProgram: Codable, Equatable {
    let content_id: String
    let title: String
    let day: Int
    let days: Int
    let completed: Bool
    /// Additive: omitted entirely for programs without day guides, and by
    /// servers older than migration `b8e6d1a4f527`.
    let today_guide: RemoteDayGuide?
}

struct RemoteProgramEnvelope: Codable { let program: RemoteProgram? }

/// One learned statement + the data that backs it (`basis` carries the counts).
struct RemotePattern: Codable, Identifiable {
    var id: String { statement }
    let statement: String
    let basis: String
}

struct RemotePatterns: Codable {
    let patterns: [RemotePattern]
    let enough_data: Bool
}

struct RemoteMemoryWipe: Codable {
    let chat_messages: Int
    let insights: Int
    // Added with per-item memory; older servers omit it.
    let memories: Int?
}

/// One addressable thing the app remembers — the user's own words or something
/// they approved. Unlike `RemotePattern` (computed per request) this has an id,
/// so it can be edited and deleted individually.
struct RemoteMemory: Codable, Identifiable, Equatable {
    let id: String
    let body: String
    let source: String
}

/// One published catalogue item from the public `/content` route.
struct RemoteContent: Codable, Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let kind: String
    let symbol: String
    let image_url: String
    /// Narration MP3 — relative "/media/…" (resolve against the API base) or
    /// absolute; optional so older servers still decode.
    let audio_url: String?
    let duration_min: Int
    let premium: Bool
}

struct RemoteSleep: Codable, Identifiable {
    let id: String
    let date: String
    let bedtime: String
    let wake_time: String
    let quality: Int
    let awakenings: Int
    let source: String
    let duration_min: Int
    let created_at: String
}

struct RemoteJournal: Codable, Identifiable {
    let id: String
    let title: String
    let body: String
    let tags: [String]
    let symbol: String
    let risk_level: String
    let created_at: String
}

struct RemoteWidget: Codable, Equatable {
    let widget_kind: String
    let title: String
    let description: String
}
struct RemoteSuggestion: Codable, Equatable, Identifiable {
    let label: String
    let action: String
    var id: String { label + action }
}
struct RemoteChat: Codable, Identifiable {
    let id: String
    let role: String
    let text: String
    let created_at: String
    /// Inline activity attached to an assistant turn (set in memory from the
    /// send response; absent from history JSON).
    var widget: RemoteWidget? = nil
}
struct RemoteChatReply: Codable {
    let user_message: RemoteChat
    let reply: RemoteChat
    let widget: RemoteWidget?
    let suggestions: [RemoteSuggestion]
}

struct STTResponse: Codable { let transcript: String }
struct OracleStatusDTO: Codable { let available: Bool }

struct RemotePlanStep: Codable, Identifiable {
    let id: String; let title: String; let detail: String
    let symbol: String; let order: Int; let done: Bool
}
struct RemotePlan: Codable, Identifiable {
    let id: String; let title: String; let focus: String
    let rationale: String; let source: String; let steps: [RemotePlanStep]
}

/// Something the user is working towards. `status` is active | achieved |
/// **released** — letting a goal go is an outcome, not a failure.
struct RemoteGoal: Codable, Identifiable, Equatable {
    let id: String
    let title: String
    let why: String
    let status: String
}

/// A small thing the user chose to repeat.
///
/// Note what is absent: there is no streak. `recent_days` is a 7-day window, so
/// a gap is just a gap — the client cannot render "you broke it" because the
/// server never says it.
struct RemoteHabit: Codable, Identifiable, Equatable {
    let id: String
    let title: String
    let cue: String
    let target_per_week: Int
    let recent_days: [String]
    let done_today: Bool
}

/// A practice suggested because of a mined pattern. `reason` is the pattern
/// statement verbatim — a suggestion with no visible basis is exactly what the
/// Pattern Dashboard exists to avoid, so it is not optional here either.
struct RemoteRecommendation: Codable, Identifiable, Equatable {
    let id: String
    let slug: String
    let title: String
    let body: String
    let action: String
    let reason: String
    let status: String
}

/// A personal safety plan — the six Stanley-Brown sections, in the user's own
/// words. Codable so it can be cached on device: this is the one screen that
/// must open without a network.
struct RemoteSafetyPlan: Codable, Equatable {
    let id: String
    let version: Int
    var warning_signs: String
    var internal_coping: String
    var social_distractors: String
    var social_support: String
    var professionals: String
    var means_safety: String
    var notes: String
}

struct RemoteMetric: Codable { let label: String; let value: String; let progress: Double }
struct RemoteInsight: Codable {
    let period: String; let headline: String; let summary: String; let metrics: [RemoteMetric]
}

// MARK: - Errors

/// Everything the free-tier cap tells a client. `resetsAt` is a real
/// timestamp, not the word "midnight": the window is UTC, so in India it
/// clears at 05:30 local and copy claiming otherwise would be wrong.
struct FreeLimitInfo: Equatable, Identifiable {
    /// Sheet presentation keys off this; the reset time is what changes between
    /// one cap event and the next.
    var id: String { "\(limit)-\(used)-\(resetsAt?.timeIntervalSince1970 ?? 0)" }
    let message: String
    let limit: Int
    let used: Int
    let resetsAt: Date?

    /// "resets at 5:30 am" in the user's own timezone.
    var resetText: String {
        guard let resetsAt else { return "resets at midnight UTC" }
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return "resets at \(f.string(from: resetsAt))"
    }
}

enum APIError: LocalizedError {
    case unauthorized
    /// The free-tier daily cap, specifically. Distinct from `.server(429, …)`
    /// because the IP rate limiter also returns 429 and means something else
    /// entirely — showing an upgrade prompt to someone who typed too fast
    /// would be both wrong and manipulative.
    case freeLimit(FreeLimitInfo)
    case server(Int, String)
    case invalidResponse
    case transport(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: return "Your session expired. Please sign in again."
        case let .freeLimit(info): return info.message
        case let .server(code, msg): return msg.isEmpty ? "Server error (\(code))." : msg
        case .invalidResponse: return "Unexpected response from server."
        case let .transport(m): return m
        }
    }
}

// MARK: - Client

/// Async HTTP client for the CereBro backend. Token + base URL persist in
/// UserDefaults so a signed-in session survives relaunches.
actor APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private var accessToken: String?
    private var baseURL: URL

    private static let tokenKey = "cerebro_access_token"
    private static let baseKey = "cerebro_api_url"
    // Release builds hit production; Debug uses the dev backend. There is no
    // in-app server field — tests override via the -cerebro_api_url launch arg.
    #if DEBUG
    #if targetEnvironment(simulator)
    static let defaultBaseURL = "http://localhost:8000"
    #else
    // Dev builds on a physical device reach the dev Mac's backend over Bonjour.
    static let defaultBaseURL = "http://Pawans-MacBook-Pro.local:8000"
    #endif
    #else
    static let defaultBaseURL = "https://api.cerebrozen.in"
    #endif

    init() {
        let defaults = UserDefaults.standard
        let stored = defaults.string(forKey: Self.baseKey) ?? Self.defaultBaseURL
        baseURL = URL(string: stored) ?? URL(string: Self.defaultBaseURL)!
        accessToken = defaults.string(forKey: Self.tokenKey)
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 15
        session = URLSession(configuration: cfg)
    }

    var isAuthenticated: Bool { accessToken != nil }
    var currentBaseURL: String { baseURL.absoluteString }

    func setBaseURL(_ string: String) {
        if let url = URL(string: string) {
            baseURL = url
            UserDefaults.standard.set(string, forKey: Self.baseKey)
        }
    }

    private func storeToken(_ token: String?) {
        accessToken = token
        UserDefaults.standard.set(token, forKey: Self.tokenKey)
    }

    func signOut() { storeToken(nil) }

    /// Revoke the session server-side (bumps token_version), then drop the token.
    func logout() async {
        struct Empty: Codable {}
        _ = try? await request("/auth/logout", method: "POST") as Empty?
        storeToken(nil)
    }

    /// Request a password-reset email. Always succeeds server-side (no enumeration).
    func requestPasswordReset(email: String) async throws {
        struct Ack: Codable { let sent: Bool }
        let _: Ack = try await request("/auth/password/forgot", method: "POST",
                                       json: ["email": email], authed: false)
    }

    /// Anonymous product event (see Analytics.swift). Sent WITHOUT auth so it
    /// can never be joined to the account; errors are swallowed by the caller.
    func sendEvent(name: String, step: String, anonId: String) async {
        struct Ack: Codable { let accepted: Int }
        let _: Ack? = try? await request(
            "/events", method: "POST",
            json: ["anon_id": anonId, "source": "ios",
                   "events": [["name": name, "step": step]]],
            authed: false)
    }

    /// Request an email one-time sign-in code (passwordless). Always succeeds
    /// server-side — the account is only created at verify, so nothing enumerates.
    func requestOtp(email: String) async throws {
        struct Ack: Codable { let sent: Bool }
        let _: Ack = try await request("/auth/otp/request", method: "POST",
                                       json: ["email": email], authed: false)
    }

    /// Exchange an emailed one-time code for our tokens (new addresses get an
    /// account created server-side, like Apple/Google sign-in).
    func verifyOtp(email: String, code: String) async throws -> AuthTokens {
        let tokens: AuthTokens = try await request(
            "/auth/otp/verify", method: "POST",
            json: ["email": email, "code": code], authed: false)
        storeToken(tokens.access_token)
        return tokens
    }

    // MARK: Auth

    func signup(email: String, password: String, name: String) async throws -> AuthTokens {
        let tokens: AuthTokens = try await request(
            "/auth/signup", method: "POST",
            json: ["email": email, "password": password, "name": name], authed: false
        )
        storeToken(tokens.access_token)
        return tokens
    }

    func login(email: String, password: String) async throws -> AuthTokens {
        // OAuth2 password form: field name is `username`.
        let form = "username=\(encode(email))&password=\(encode(password))"
        let tokens: AuthTokens = try await request(
            "/auth/login", method: "POST", formBody: form, authed: false
        )
        storeToken(tokens.access_token)
        return tokens
    }

    func me() async throws -> RemoteUser { try await request("/auth/me", method: "GET") }

    /// Sign in with Apple: exchange the Apple identity token for our tokens.
    func appleSignIn(identityToken: String, name: String) async throws -> AuthTokens {
        let tokens: AuthTokens = try await request(
            "/auth/apple", method: "POST",
            json: ["identity_token": identityToken, "name": name], authed: false)
        storeToken(tokens.access_token)
        return tokens
    }

    /// Sign in with Google: exchange the Google ID token for our tokens.
    func googleSignIn(idToken: String, name: String) async throws -> AuthTokens {
        let tokens: AuthTokens = try await request(
            "/auth/google", method: "POST",
            json: ["id_token": idToken, "name": name], authed: false)
        storeToken(tokens.access_token)
        return tokens
    }

    /// Permanently delete the account + all server data (App Store 5.1.1(v)).
    func deleteAccount() async throws {
        let _: EmptyResponse = try await request("/users/me", method: "DELETE")
        storeToken(nil)
    }

    /// Download a full copy of the user's data as raw JSON bytes.
    func exportData() async throws -> Data {
        try await rawRequest("/users/me/export", method: "GET", body: Data(),
                             contentType: "application/json") { $0 }
    }

    // MARK: Data

    func createMood(mood: String, note: String, symbol: String, intensity: Int) async throws -> RemoteMood {
        try await request("/moods", method: "POST",
                          json: ["mood": mood, "note": note, "symbol": symbol, "intensity": intensity])
    }

    /// The published content catalogue (public route — works signed out).
    func contentList() async throws -> [RemoteContent] {
        try await request("/content", method: "GET", authed: false)
    }

    /// Upsert the sleep-diary entry for a date (server keeps one row per night).
    @discardableResult
    func upsertSleep(date: String, bedtime: String, wakeTime: String,
                     quality: Int, awakenings: Int, source: String = "manual") async throws -> RemoteSleep {
        try await request("/sleep", method: "POST",
                          json: ["date": date, "bedtime": bedtime, "wake_time": wakeTime,
                                 "quality": quality, "awakenings": awakenings, "source": source])
    }

    func createJournal(title: String, body: String, tags: [String]) async throws -> RemoteJournal {
        try await request("/journal", method: "POST",
                          json: ["title": title, "body": body, "tags": tags, "symbol": "book"])
    }

    func journalHistory() async throws -> [RemoteJournal] { try await request("/journal", method: "GET") }

    func chatHistory() async throws -> [RemoteChat] { try await request("/chat", method: "GET") }

    func sendChat(_ text: String) async throws -> RemoteChatReply {
        try await request("/chat/messages", method: "POST", json: ["text": text])
    }

    func activePlan() async throws -> RemotePlan { try await request("/plans/active", method: "GET") }

    func toggleStep(_ id: String, done: Bool) async throws -> RemotePlan {
        try await request("/plans/steps/\(id)", method: "PATCH", json: ["done": done])
    }

    func weeklyInsights() async throws -> RemoteInsight { try await request("/insights/weekly", method: "GET") }

    // MARK: Programs (multi-day journeys — "DAY X OF Y" card)

    func activeProgram() async throws -> RemoteProgramEnvelope {
        try await request("/programs/active", method: "GET")
    }

    func enrollProgram(contentId: String) async throws -> RemoteProgramEnvelope {
        try await request("/programs/enroll", method: "POST", json: ["content_id": contentId])
    }

    func leaveProgram() async throws -> RemoteProgramEnvelope {
        try await request("/programs/active", method: "DELETE")
    }

    // MARK: Pattern dashboard (transparent AI memory)

    func patterns() async throws -> RemotePatterns { try await request("/insights/patterns", method: "GET") }

    func deleteMemory() async throws -> RemoteMemoryWipe { try await request("/users/me/memory", method: "DELETE") }

    // MARK: Per-item memory

    func memories() async throws -> [RemoteMemory] {
        try await request("/users/me/memory", method: "GET")
    }

    @discardableResult
    func addMemory(_ body: String) async throws -> RemoteMemory {
        try await request("/users/me/memory", method: "POST", json: ["body": body])
    }

    @discardableResult
    func editMemory(id: String, body: String) async throws -> RemoteMemory {
        try await request("/users/me/memory/\(id)", method: "PATCH", json: ["body": body])
    }

    func deleteMemory(id: String) async throws {
        let _: EmptyResponse = try await request("/users/me/memory/\(id)", method: "DELETE")
    }

    /// Stop showing one computed pattern. Keyed by its statement — patterns are
    /// derived per request and have no id of their own.
    func suppressPattern(_ statement: String) async throws {
        let _: EmptyResponse = try await request(
            "/users/me/memory/suppress-pattern", method: "POST",
            json: ["statement": statement])
    }

    // MARK: Goals + habits (the things the user defines)

    func goals() async throws -> [RemoteGoal] { try await request("/goals", method: "GET") }

    @discardableResult
    func addGoal(_ title: String) async throws -> RemoteGoal {
        try await request("/goals", method: "POST", json: ["title": title])
    }

    @discardableResult
    func setGoalStatus(id: String, status: String) async throws -> RemoteGoal {
        try await request("/goals/\(id)", method: "PATCH", json: ["status": status])
    }

    /// Turn a goal into today's plan. Replaces the active plan — the product
    /// has exactly one at a time.
    @discardableResult
    func decomposeGoal(id: String) async throws -> RemotePlan {
        try await request("/goals/\(id)/decompose", method: "POST", json: [:])
    }

    func habits() async throws -> [RemoteHabit] { try await request("/habits", method: "GET") }

    @discardableResult
    func addHabit(title: String, cue: String) async throws -> RemoteHabit {
        try await request("/habits", method: "POST", json: ["title": title, "cue": cue])
    }

    /// Toggle today. Completing is idempotent server-side and undoable, so a
    /// mis-tap is never permanent.
    @discardableResult
    func setHabitToday(id: String, done: Bool) async throws -> RemoteHabit {
        try await request("/habits/\(id)/complete", method: done ? "POST" : "DELETE",
                          json: done ? [:] : nil)
    }

    // MARK: Recommendations (derived from the user's own patterns)

    /// Pending suggestions. The server seeds from current patterns on read and
    /// returns [] for thin data, so this is safe to call unconditionally.
    func recommendations() async throws -> [RemoteRecommendation] {
        try await request("/recommendations/mine", method: "GET")
    }

    @discardableResult
    func resolveRecommendation(id: String, accept: Bool) async throws -> RemoteRecommendation {
        try await request("/recommendations/\(id)/\(accept ? "accept" : "dismiss")",
                          method: "POST", json: [:])
    }

    // MARK: Safety plan (user-authored; the model never writes one)

    /// The live plan, or nil when the user hasn't written one. Nil is a normal
    /// state — never surfaced as an error.
    func safetyPlan() async throws -> RemoteSafetyPlan? {
        try await request("/safety-plan/me", method: "GET")
    }

    /// Save one or more sections. Unset fields carry over server-side, so the
    /// guided flow can save a single section without blanking the rest.
    @discardableResult
    func saveSafetyPlan(_ fields: [String: Any]) async throws -> RemoteSafetyPlan {
        try await request("/safety-plan/me", method: "PUT", json: fields)
    }

    // MARK: Assessment (self-reflection → conversation topics)

    /// Persist onboarding choices (goals + motivations) to the user's profile.
    @discardableResult
    func updateProfile(goals: [String], motivations: [String]) async throws -> RemoteUser {
        try await request("/users/me", method: "PATCH",
                          json: ["goals": goals, "motivations": motivations])
    }

    /// Persist the user's effective crisis region (drives locale-correct hotlines
    /// in server-side crisis replies).
    @discardableResult
    func updateRegion(_ region: String) async throws -> RemoteUser {
        try await request("/users/me", method: "PATCH", json: ["region": region])
    }

    /// The user's saved trusted contact (nil when none set).
    func trustedContact() async throws -> RemoteTrustedContact? {
        try await request("/users/me/trusted-contact", method: "GET")
    }

    /// Create/update the trusted contact to notify on a detected crisis.
    @discardableResult
    func saveTrustedContact(name: String, method: String, value: String,
                            relationship: String, notifyConsent: Bool) async throws -> RemoteTrustedContact {
        try await request("/users/me/trusted-contact", method: "PUT",
                          json: ["name": name, "method": method, "value": value,
                                 "relationship": relationship, "notify_consent": notifyConsent])
    }

    /// Record the onboarding age (18+) + AI-disclosure acknowledgement
    /// (compliance). Carries the on-device confirmation time when known — the
    /// tap can predate the first connect; the server caps future clocks.
    @discardableResult
    func attest(ageConfirmedAt: Date? = nil) async throws -> RemoteUser {
        if let date = ageConfirmedAt {
            let iso = ISO8601DateFormatter().string(from: date)
            return try await request("/users/me/attest", method: "POST",
                                     json: ["age_confirmed_at": iso])
        }
        return try await request("/users/me/attest", method: "POST")
    }

    /// Update the companion style ("Calm Guide" etc.) on the server profile.
    @discardableResult
    func updateCompanion(_ companion: String) async throws -> RemoteUser {
        try await request("/users/me", method: "PATCH", json: ["companion": companion])
    }

    /// Verify a StoreKit 2 signed transaction server-side; the server sets the
    /// authoritative subscription tier and returns the updated profile.
    @discardableResult
    func verifySubscription(_ signedTransaction: String) async throws -> RemoteUser {
        try await request("/users/me/subscription/verify", method: "POST",
                          json: ["signed_transaction": signedTransaction])
    }

    /// Sync the user's privacy/consent choices to the server, where each
    /// category is enforced at its read site (chat recall, plan signals,
    /// weekly insights).
    @discardableResult
    func updateConsent(moodHistory: Bool, aiMemory: Bool,
                       voiceStorage: Bool, modelTraining: Bool,
                       journalMemory: Bool, sleepHistory: Bool) async throws -> RemoteConsent {
        try await request("/users/me/consent", method: "PATCH",
                          json: ["mood_history": moodHistory, "ai_memory": aiMemory,
                                 "voice_storage": voiceStorage, "model_training": modelTraining,
                                 "journal_memory": journalMemory, "sleep_history": sleepHistory])
    }

    /// The account's recorded consent. Read so a fresh device can ADOPT the
    /// server's truth (Privacy & Memory then shows what the account really
    /// granted) — never the other direction.
    func consent() async throws -> RemoteConsent {
        try await request("/users/me/consent", method: "GET")
    }

    /// Generate personalized, tappable conversation starters. Passing the
    /// selection explicitly lets onboarding preview topics before they're saved.
    func assessmentTopics(motivations: [String], goals: [String],
                          language: String, count: Int = 8) async throws -> [RemoteTopic] {
        let resp: TopicsResponse = try await request(
            "/assessment/topics", method: "POST",
            json: ["motivations": motivations, "goals": goals, "language": language, "count": count])
        return resp.topics
    }

    func registerPushToken(_ token: String) async throws {
        let _: RemoteUser = try await request("/users/me/push-token", method: "PUT",
                                              json: ["push_token": token])
    }

    // MARK: Voice

    /// Upload recorded audio and return the Deepgram transcript.
    func transcribe(audio: Data, contentType: String, filename: String = "speech.m4a") async throws -> String {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        func append(_ s: String) { body.append(s.data(using: .utf8)!) }
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"audio\"; filename=\"\(filename)\"\r\n")
        append("Content-Type: \(contentType)\r\n\r\n")
        body.append(audio)
        append("\r\n--\(boundary)--\r\n")

        let resp: STTResponse = try await rawRequest(
            "/voice/stt", method: "POST", body: body,
            contentType: "multipart/form-data; boundary=\(boundary)"
        ) { data in
            try JSONDecoder().decode(STTResponse.self, from: data)
        }
        return resp.transcript
    }

    /// Synthesize speech for `text` and return MP3 audio bytes.
    func synthesize(text: String) async throws -> Data {
        let json = try JSONSerialization.data(withJSONObject: ["text": text])
        return try await rawRequest("/voice/tts", method: "POST", body: json,
                                    contentType: "application/json") { $0 }
    }

    /// Generic request that returns a decoded value from raw response bytes —
    /// used for multipart uploads and binary (audio) downloads the JSON helper
    /// can't express. Honors auth + the same status-code handling.
    private func rawRequest<T>(
        _ path: String, method: String, body: Data, contentType: String,
        decode: (Data) throws -> T
    ) async throws -> T {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        if let accessToken { req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }

        let data: Data, response: URLResponse
        do { (data, response) = try await session.data(for: req) }
        catch { throw APIError.transport(error.localizedDescription) }
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }

        switch http.statusCode {
        case 200...299:
            do { return try decode(data) } catch { throw APIError.invalidResponse }
        case 401, 403:
            throw APIError.unauthorized
        default:
            let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            if http.statusCode == 429, let info = Self.freeLimit(from: body) {
                throw APIError.freeLimit(info)
            }
            // `error` is slowapi's key for the IP rate limiter; without it a
            // throttled user sees only "Server error (429)".
            let detail = (body?["detail"] as? String)
                ?? Self.validationMessage(from: body)
                ?? (body?["error"] as? String)
            throw APIError.server(http.statusCode, detail ?? "")
        }
    }

    /// Pydantic validation errors (422) carry `detail` in a THIRD shape — an
    /// ARRAY of {loc, msg, type}, with the human-written reason in `msg`
    /// behind a "Value error, " prefix. Without this the server's own
    /// sentence ("That doesn't look like an email address.") collapsed to
    /// "Server error (422)". Android's `Session.raw` closed the same gap the
    /// same day (e5697f83).
    static func validationMessage(from body: [String: Any]?) -> String? {
        guard let items = body?["detail"] as? [[String: Any]],
              let msg = items.first?["msg"] as? String else { return nil }
        let prefix = "Value error, "
        return msg.hasPrefix(prefix) ? String(msg.dropFirst(prefix.count)) : msg
    }

    /// Recognise the quota 429 by its `code`, never by the status alone.
    static func freeLimit(from body: [String: Any]?) -> FreeLimitInfo? {
        guard let detail = body?["detail"] as? [String: Any],
              detail["code"] as? String == "free_daily_limit"
        else { return nil }
        var resets: Date?
        if let iso = detail["resets_at"] as? String {
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            resets = f.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        }
        return FreeLimitInfo(
            message: detail["message"] as? String ?? "You've reached today's free messages.",
            limit: detail["limit"] as? Int ?? 0,
            used: detail["used"] as? Int ?? 0,
            resetsAt: resets)
    }

    // MARK: Oracle (streaming agent)

    func oracleStatus() async -> Bool {
        let dto: OracleStatusDTO? = try? await request("/oracle/status", method: "GET")
        return dto?.available ?? false
    }

    /// Build an authed POST request for an SSE endpoint. The caller streams it
    /// with `URLSession.bytes` (see `oracleEventStream`).
    func oracleRequest(path: String, json: [String: Any]) -> URLRequest? {
        guard let accessToken else { return nil }
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "POST"
        req.timeoutInterval = 60
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try? JSONSerialization.data(withJSONObject: json)
        return req
    }

    // MARK: Core request

    private func request<T: Decodable>(
        _ path: String,
        method: String,
        json: [String: Any]? = nil,
        formBody: String? = nil,
        authed: Bool = true
    ) async throws -> T {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method

        if let formBody {
            req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            req.httpBody = formBody.data(using: .utf8)
        } else if let json {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: json)
        }
        if authed, let accessToken {
            req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        let data: Data, response: URLResponse
        do {
            (data, response) = try await session.data(for: req)
        } catch {
            throw APIError.transport(error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }

        switch http.statusCode {
        case 200...299:
            if T.self == EmptyResponse.self { return EmptyResponse() as! T }
            do { return try JSONDecoder().decode(T.self, from: data) }
            catch { throw APIError.invalidResponse }
        case 401, 403:
            throw APIError.unauthorized
        default:
            // Same fallback chain as `rawRequest`: string `detail`, then the
            // pydantic array shape (the server's own validation sentence),
            // then slowapi's `error`. This path also recognises the free-tier
            // cap so a JSON call never renders it as a bare 429.
            let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            if http.statusCode == 429, let info = Self.freeLimit(from: body) {
                throw APIError.freeLimit(info)
            }
            let detail = (body?["detail"] as? String)
                ?? Self.validationMessage(from: body)
                ?? (body?["error"] as? String)
            throw APIError.server(http.statusCode, detail ?? "")
        }
    }

    private func encode(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s
    }
}

struct EmptyResponse: Decodable {}
