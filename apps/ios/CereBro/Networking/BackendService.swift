import Foundation
import SwiftUI
import UIKit

/// Observable wrapper around `APIClient` that the UI binds to. Keeps cloud sync
/// strictly additive: when not connected, the app behaves exactly as the local
/// prototype. When connected, writes are mirrored best-effort and the agentic
/// plan + insights are fetched from the server.
@MainActor
final class BackendService: ObservableObject {
    enum Status: Equatable {
        case signedOut
        case connecting
        case connected(email: String)
        case error(String)
    }

    @Published private(set) var status: Status = .signedOut
    @Published private(set) var user: RemoteUser?
    @Published private(set) var plan: RemotePlan?
    @Published private(set) var insight: RemoteInsight?
    /// Active multi-day journey ("PROGRAM · DAY X OF Y" Home card).
    @Published private(set) var program: RemoteProgram?
    /// Served program catalogue with backend ids (enrollment needs them).
    @Published private(set) var remotePrograms: [RemoteContent] = []
    @Published private(set) var chat: [RemoteChat] = []
    @Published private(set) var suggestions: [RemoteSuggestion] = []
    /// Personalized, tappable conversation starters from the self-reflection.
    @Published private(set) var starters: [RemoteTopic] = []
    /// Server content catalogue grouped by kind (public route; empty offline —
    /// screens fall back to their local `Dummy` rails).
    @Published private(set) var catalogue: [String: [ContentItem]] = [:]
    private var catalogueLoaded = false
    @Published var baseURL: String = APIClient.defaultBaseURL

    // Oracle (streaming agent) state.
    @Published private(set) var oracleAvailable = false
    @Published private(set) var isStreaming = false
    @Published private(set) var streamingText = ""
    @Published private(set) var streamingWidget: RemoteWidget?
    @Published private(set) var pendingConfirm: OracleConfirm?
    private var oracleThread: String { "ios-" + (user?.id ?? "anon") }
    /// Effective crisis region (override or device locale) cached so it can be
    /// (re)applied to the server profile on connect — drives locale-correct
    /// hotlines in server-side crisis replies.
    private var effectiveRegion = ""
    /// Latest local consent, cached so it can be pushed on connect.
    private var pendingConsent: Consent?
    /// Latest local self-reflection (motivations + goals), cached so choices
    /// made while signed out (the normal onboarding order) reach the profile
    /// at the next connect — the server plan/insights personalize off it.
    private var pendingAssessment: (motivations: [String], goals: [String])?
    /// On-device 18+ confirmation time, carried by attest() at connect.
    private var pendingAgeConfirmedAt: Date?
    /// Companion style chosen locally, pushed to the profile on connect.
    private var pendingCompanion: String?

    var isConnected: Bool { if case .connected = status { return true } else { return false } }
    /// Subscription entitlement from the server profile.
    var subscriptionTier: String { user?.subscription_tier ?? "free" }
    var isPremium: Bool { ["premium", "premium_human"].contains(subscriptionTier) }

    init() {
        Task { await bootstrap() }
    }

    /// Restore an existing session on launch (silent — no error surfaced).
    private func bootstrap() async {
        baseURL = await APIClient.shared.currentBaseURL
        guard await APIClient.shared.isAuthenticated else { return }
        do {
            let me = try await APIClient.shared.me()
            user = me
            status = .connected(email: me.email)
            await refresh()   // restored sessions should load the plan + insights too
            oracleAvailable = await APIClient.shared.oracleStatus()
        } catch {
            // Token stale/unreachable — fall back to local silently.
            status = .signedOut
        }
    }

    func updateBaseURL(_ string: String) {
        baseURL = string
        Task { await APIClient.shared.setBaseURL(string) }
    }

    /// Apply a new server URL and wait for it to take effect before the caller
    /// proceeds (used on real devices to point at the Mac's LAN address before
    /// signing in — avoids a race with the next request).
    func setServer(_ string: String) async {
        let url = string.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty else { return }
        baseURL = url
        await APIClient.shared.setBaseURL(url)
    }

    func signUp(email: String, password: String, name: String) async {
        status = .connecting
        do {
            _ = try await APIClient.shared.signup(email: email, password: password, name: name)
            try await finishConnect()
        } catch {
            status = .error(message(error))
        }
    }

    func signIn(email: String, password: String) async {
        status = .connecting
        do {
            _ = try await APIClient.shared.login(email: email, password: password)
            try await finishConnect()
        } catch {
            status = .error(message(error))
        }
    }

    private func finishConnect() async throws {
        let me = try await APIClient.shared.me()
        user = me
        status = .connected(email: me.email)
        // Push the local self-reflection BEFORE the first refresh, so the very
        // first plan/insights fetch personalizes off the user's onboarding
        // choices instead of profile defaults.
        if let a = pendingAssessment {
            _ = try? await APIClient.shared.updateProfile(goals: a.goals, motivations: a.motivations)
        }
        await refresh()
        oracleAvailable = await APIClient.shared.oracleStatus()
        if !effectiveRegion.isEmpty {
            _ = try? await APIClient.shared.updateRegion(effectiveRegion)
        }
        // Compliance: the user passed onboarding's age + AI-disclosure gates
        // before reaching a connected state, so record the attestation (with
        // the on-device confirmation time when the tap happened offline).
        _ = try? await APIClient.shared.attest(ageConfirmedAt: pendingAgeConfirmedAt)
        if let c = pendingConsent { await pushConsent(c) }
        if let style = pendingCompanion {
            _ = try? await APIClient.shared.updateCompanion(style)
        }
    }

    /// Cache the on-device 18+ confirmation time so attest() can carry it on
    /// the next connect (the tap usually happens before any account exists).
    func syncAgeConfirmation(_ date: Date?) {
        pendingAgeConfirmedAt = date
    }

    /// Sync the chosen companion style to the server profile (drives the AI
    /// voice). Cached and re-applied on connect, like consent/region.
    func syncCompanion(_ companion: String) {
        pendingCompanion = companion
        guard isConnected else { return }
        Task { _ = try? await APIClient.shared.updateCompanion(companion) }
    }

    /// Push the user's effective crisis region to the server profile so crisis
    /// replies surface locale-correct hotlines. Cached and re-applied on connect.
    func syncCrisisRegion(_ region: String) {
        effectiveRegion = region
        guard isConnected, !region.isEmpty else { return }
        Task { _ = try? await APIClient.shared.updateRegion(region) }
    }

    /// Sync privacy/consent choices to the server (enforced in the chat pipeline).
    /// Cached and re-applied on connect.
    func syncConsent(_ consent: Consent) {
        pendingConsent = consent
        guard isConnected else { return }
        Task { await pushConsent(consent) }
    }

    private func pushConsent(_ c: Consent) async {
        _ = try? await APIClient.shared.updateConsent(
            moodHistory: c.moodHistory, aiMemory: c.aiMemory,
            voiceStorage: c.voiceStorage, modelTraining: c.modelTraining,
            journalMemory: c.journalMemory, sleepHistory: c.sleepHistory)
    }

    /// Send a StoreKit signed transaction for server-side verification; the
    /// returned profile carries the authoritative subscription tier.
    func verifySubscription(_ signedTransaction: String) async {
        guard isConnected else { return }
        if let updated = try? await APIClient.shared.verifySubscription(signedTransaction) {
            user = updated
        }
    }

    /// Sign in with Apple — exchange the identity token, then connect.
    func signInWithApple(identityToken: String, name: String) async {
        status = .connecting
        do {
            _ = try await APIClient.shared.appleSignIn(identityToken: identityToken, name: name)
            try await finishConnect()
        } catch {
            status = .error(message(error))
        }
    }

    /// Sign in with Google — exchange the ID token, then connect.
    func signInWithGoogle(idToken: String, name: String) async {
        status = .connecting
        do {
            _ = try await APIClient.shared.googleSignIn(idToken: idToken, name: name)
            try await finishConnect()
        } catch {
            status = .error(message(error))
        }
    }

    /// Permanently delete the account + all server data, then sign out locally.
    /// Returns false (still signs out) if the server call fails.
    @discardableResult
    func deleteAccount() async -> Bool {
        var ok = true
        if isConnected { ok = ((try? await APIClient.shared.deleteAccount()) != nil) }
        signOut()
        return ok
    }

    /// Raw JSON export of everything the server holds for this user.
    func exportData() async -> Data? {
        guard isConnected else { return nil }
        return try? await APIClient.shared.exportData()
    }

    /// Request a password-reset email for the given address (no-op UI feedback
    /// handled by the caller). Throws only on a transport error.
    func requestPasswordReset(email: String) async throws {
        try await APIClient.shared.requestPasswordReset(email: email)
    }

    /// Email the user a one-time sign-in code (passwordless auth).
    func requestEmailCode(email: String) async throws {
        try await APIClient.shared.requestOtp(email: email)
    }

    /// Sign in with an emailed one-time code, then connect.
    func signInWithCode(email: String, code: String) async {
        status = .connecting
        do {
            _ = try await APIClient.shared.verifyOtp(email: email, code: code)
            try await finishConnect()
        } catch {
            status = .error(message(error))
        }
    }

    func signOut() {
        Task { await APIClient.shared.logout() }   // revoke server-side, then clear
        user = nil; plan = nil; insight = nil; program = nil; chat = []; suggestions = []; starters = []
        oracleAvailable = false; isStreaming = false; streamingText = ""
        streamingWidget = nil; pendingConfirm = nil
        status = .signedOut
    }

    /// Pull the agentic plan + weekly insights from the server.
    func refresh() async {
        guard isConnected else { return }
        plan = try? await APIClient.shared.activePlan()
        insight = try? await APIClient.shared.weeklyInsights()
        program = (try? await APIClient.shared.activeProgram())?.program
    }

    // MARK: Programs (multi-day journeys)

    func enrollProgram(contentId: String) async {
        guard isConnected else { return }
        if let p = (try? await APIClient.shared.enrollProgram(contentId: contentId))?.program {
            program = p
        }
    }

    func leaveProgram() async {
        guard isConnected else { return }
        _ = try? await APIClient.shared.leaveProgram()
        program = nil
    }

    // MARK: Content catalogue (public — no sign-in required)

    /// Fetch the published catalogue once per launch. Skipped under `-resetState`
    /// so UITest rails stay deterministic on the local `Dummy` fallback.
    func loadCatalogue() async {
        guard !catalogueLoaded, !UserDefaults.standard.bool(forKey: "resetState") else { return }
        guard let items = try? await APIClient.shared.contentList(), !items.isEmpty else { return }
        catalogueLoaded = true
        var grouped: [String: [ContentItem]] = [:]
        for item in items {
            grouped[item.kind, default: []].append(
                ContentItem(title: item.title, subtitle: item.subtitle,
                            symbol: item.symbol, imageURL: item.image_url))
        }
        catalogue = grouped
        remotePrograms = items.filter { $0.kind == "program" }
    }

    // MARK: Assessment (self-reflection → conversation starters)

    /// Persist the onboarding self-reflection to the profile (best-effort).
    /// Cached and re-applied on connect, like consent/region, so a selection
    /// made before sign-in still reaches the server.
    func saveAssessment(motivations: [String], goals: [String]) {
        pendingAssessment = (motivations, goals)
        guard isConnected else { return }
        Task { _ = try? await APIClient.shared.updateProfile(goals: goals, motivations: motivations) }
    }

    /// Fetch personalized conversation starters for the given selection. The
    /// caller passes the local onboarding choices so topics work before any
    /// profile sync. No-op (keeps prior list) on failure.
    func loadStarters(motivations: [String], goals: [String], language: String) async {
        guard isConnected else { return }
        if let topics = try? await APIClient.shared.assessmentTopics(
            motivations: motivations, goals: goals, language: language) {
            starters = topics
        }
    }

    // MARK: Chat (live AI companion when connected)

    func loadChat() async {
        guard isConnected else { return }
        chat = (try? await APIClient.shared.chatHistory()) ?? []
        suggestions = []
    }

    /// Send a message and append both it and the assistant reply. Returns false
    /// if not connected (caller falls back to the local transcript).
    @discardableResult
    func sendChat(_ text: String) async -> Bool {
        await sendChatGetReply(text) != nil
    }

    /// Like `sendChat` but returns the assistant's reply text (used by the voice
    /// companion so it can speak the response). Appends both messages to `chat`.
    func sendChatGetReply(_ text: String) async -> String? {
        guard isConnected else { return nil }
        guard let reply = try? await APIClient.shared.sendChat(text) else { return nil }
        chat.append(reply.user_message)
        var assistant = reply.reply
        assistant.widget = reply.widget          // render the inline activity
        chat.append(assistant)
        suggestions = reply.suggestions
        return reply.reply.text
    }

    // MARK: Oracle (streaming agent — tool-calling + confirm-before-write)

    /// Send a message through the LangGraph Oracle, streaming the reply token by
    /// token and rendering inline activities / confirmation prompts.
    func sendOracle(_ text: String) async {
        guard isConnected, oracleAvailable, !isStreaming else { return }
        chat.append(RemoteChat(id: UUID().uuidString, role: "user", text: text, created_at: ""))
        suggestions = []
        guard let req = await APIClient.shared.oracleRequest(
            path: "/oracle/messages", json: ["text": text, "thread_id": oracleThread]) else { return }
        await consume(req)
    }

    /// Stream just the Oracle's reply-text tokens for the voice loop's
    /// incremental (sentence-by-sentence) TTS. Does not touch the published chat
    /// transcript — the voice companion manages its own. Yields nothing (finishes
    /// immediately) when the Oracle isn't available so the caller can fall back.
    nonisolated func oracleReplyStream(_ text: String) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task { @MainActor in
                guard isConnected, oracleAvailable,
                      let req = await APIClient.shared.oracleRequest(
                        path: "/oracle/messages", json: ["text": text, "thread_id": oracleThread]) else {
                    continuation.finish(); return
                }
                do {
                    for try await event in oracleEventStream(req) {
                        switch event {
                        case .token(let t): continuation.yield(t)
                        case .error(let e): continuation.finish(throwing: APIError.server(0, e)); return
                        case .done: continuation.finish(); return
                        default: break
                        }
                    }
                    continuation.finish()
                } catch { continuation.finish(throwing: error) }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Approve or decline a paused write action; resumes the same thread.
    func resolveConfirm(approved: Bool) async {
        guard let confirm = pendingConfirm else { return }
        pendingConfirm = nil
        guard let req = await APIClient.shared.oracleRequest(
            path: "/oracle/confirm", json: ["thread_id": confirm.threadId, "approved": approved]) else { return }
        await consume(req)
        await refresh()   // a confirmed write (mood/journal) may change insights
    }

    private func consume(_ request: URLRequest) async {
        isStreaming = true; streamingText = ""; streamingWidget = nil
        do {
            for try await event in oracleEventStream(request) {
                switch event {
                case .token(let t):       streamingText += t
                case .crisis:
                    // Backend safety layer flagged crisis mid-stream. Surface a
                    // crisis suggestion so TalkView/ChatView raise the CrisisBanner
                    // (inCrisis reads suggestions for action == "crisis").
                    if !suggestions.contains(where: { $0.action == "crisis" }) {
                        suggestions.append(RemoteSuggestion(label: "Get crisis support", action: "crisis"))
                    }
                case .widget(let w):      streamingWidget = w
                case .toolConfirm(let c): pendingConfirm = c
                case .awaitingConfirm:    break
                case .done(let final):    finishStreaming(text: final.isEmpty ? streamingText : final)
                case .error(let e):       finishStreaming(text: streamingText.isEmpty ? e : streamingText)
                }
            }
        } catch {
            // Stream ended/failed; fall through to finalize below.
        }
        if pendingConfirm != nil {
            // Paused for confirmation — clear the live bubble, keep the card.
            isStreaming = false; streamingText = ""; streamingWidget = nil
        } else if isStreaming {
            finishStreaming(text: streamingText)
        }
    }

    private func finishStreaming(text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !t.isEmpty || streamingWidget != nil {
            var msg = RemoteChat(id: UUID().uuidString, role: "assistant", text: t, created_at: "")
            msg.widget = streamingWidget
            chat.append(msg)
        }
        streamingText = ""; streamingWidget = nil; isStreaming = false
        // VoiceOver hears the completed reply once, instead of token noise
        // (the live bubble is marked .updatesFrequently while it streams).
        if UIAccessibility.isVoiceOverRunning, !t.isEmpty {
            UIAccessibility.post(notification: .announcement, argument: t)
        }
    }

    // MARK: Plan step completion (server-driven plan)

    func toggleStep(_ id: String, done: Bool) async {
        guard isConnected else { return }
        if let updated = try? await APIClient.shared.toggleStep(id, done: done) {
            plan = updated
        }
    }

    // MARK: Best-effort write mirroring (no-ops when not connected)

    func mirrorMood(_ mood: MoodLog) {
        guard isConnected else { return }
        Task {
            _ = try? await APIClient.shared.createMood(
                mood: mood.mood, note: mood.note, symbol: mood.symbol, intensity: 3)
            await refresh()
        }
    }

    func mirrorSleep(_ entry: SleepEntry) {
        guard isConnected else { return }
        Task {
            _ = try? await APIClient.shared.upsertSleep(
                date: entry.day,
                bedtime: SleepEntry.apiTime(entry.bedMinutes),
                wakeTime: SleepEntry.apiTime(entry.wakeMinutes),
                quality: entry.quality, awakenings: entry.awakenings, source: entry.source)
        }
    }

    func mirrorJournal(_ entry: JournalEntry, body: String) {
        guard isConnected else { return }
        Task { _ = try? await APIClient.shared.createJournal(title: entry.title, body: body, tags: entry.tags) }
    }

    private func message(_ error: Error) -> String {
        (error as? APIError)?.errorDescription ?? error.localizedDescription
    }
}
