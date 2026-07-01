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
    /// Subscription entitlement ("free" | "premium" | "premium_human").
    let subscription_tier: String?
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

struct RemoteMetric: Codable { let label: String; let value: String; let progress: Double }
struct RemoteInsight: Codable {
    let period: String; let headline: String; let summary: String; let metrics: [RemoteMetric]
}

// MARK: - Errors

enum APIError: LocalizedError {
    case unauthorized
    case server(Int, String)
    case invalidResponse
    case transport(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: return "Your session expired. Please sign in again."
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
    static let defaultBaseURL = "http://localhost:8000"

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

    /// Record the onboarding age (18+) + AI-disclosure acknowledgement (compliance).
    @discardableResult
    func attest() async throws -> RemoteUser {
        try await request("/users/me/attest", method: "POST")
    }

    /// Verify a StoreKit 2 signed transaction server-side; the server sets the
    /// authoritative subscription tier and returns the updated profile.
    @discardableResult
    func verifySubscription(_ signedTransaction: String) async throws -> RemoteUser {
        try await request("/users/me/subscription/verify", method: "POST",
                          json: ["signed_transaction": signedTransaction])
    }

    /// Sync the user's privacy/consent choices to the server (enforced in the
    /// chat/oracle pipeline — e.g. AI memory off drops long-term recall).
    @discardableResult
    func updateConsent(moodHistory: Bool, aiMemory: Bool,
                       voiceStorage: Bool, modelTraining: Bool) async throws -> RemoteConsent {
        try await request("/users/me/consent", method: "PATCH",
                          json: ["mood_history": moodHistory, "ai_memory": aiMemory,
                                 "voice_storage": voiceStorage, "model_training": modelTraining])
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
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"] as? String
            throw APIError.server(http.statusCode, detail ?? "")
        }
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
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"] as? String
            throw APIError.server(http.statusCode, detail ?? "")
        }
    }

    private func encode(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s
    }
}

struct EmptyResponse: Decodable {}
