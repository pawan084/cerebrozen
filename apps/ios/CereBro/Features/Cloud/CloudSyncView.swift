import AuthenticationServices
import SwiftUI

struct CloudSyncView: View {
    @EnvironmentObject var backend: BackendService
    // Empty by default — production users start with a clean form. Demo
    // credentials + the local server are pre-filled only in DEBUG (see onAppear).
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var server = ""
    @State private var mode: Mode = .signIn
    @State private var googleAuth = GoogleAuth()
    @State private var googleMessage: String?
    @State private var resetMessage: String?
    @State private var showDevOptions = false

    enum Mode { case signIn, signUp }

    var body: some View {
        ScreenScaffold(eyebrow: backend.isConnected ? "Your account" : "Private by design",
                       title: backend.isConnected ? "Account" : "Sign in",
                       trailingSystemImage: "person.crop.circle", accent: Theme.Palette.lav) {
            ToolBanner(imageURL: Dummy.Img.privacy, symbol: "person.crop.circle",
                       caption: "Sign in to keep your plan, journal and check-ins in sync across devices.")

            statusCard

            if backend.isConnected {
                connectedBody
            } else {
                authForm
            }
        }
    }

    // MARK: Status

    private var statusCard: some View {
        Card {
            HStack(spacing: 12) {
                Circle().fill(statusColor).frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(statusTitle).appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    #if DEBUG
                    Text(backend.baseURL).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    #endif
                }
                Spacer()
            }
        }
    }

    private var statusColor: Color {
        switch backend.status {
        case .connected: return Theme.Palette.success
        case .connecting: return Theme.Palette.cream
        case .error: return Theme.Palette.danger
        case .signedOut: return Theme.Palette.muted
        }
    }
    private var statusTitle: String {
        switch backend.status {
        case let .connected(email): return "Connected as \(email)"
        case .connecting: return "Connecting…"
        case let .error(msg): return msg
        case .signedOut: return "Not connected"
        }
    }

    // MARK: Auth form

    private var authForm: some View {
        VStack(spacing: 12) {
            // Social sign-in first — the modern pattern. (Apple is required by the
            // App Store wherever a third-party sign-in like Google is offered.)
            SignInWithAppleButton(.signIn) { request in
                request.requestedScopes = [.fullName, .email]
            } onCompletion: { result in
                handleApple(result)
            }
            .signInWithAppleButtonStyle(.white)
            .frame(height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .accessibilityIdentifier("Sign in with Apple")

            googleButton
            if let googleMessage {
                Text(googleMessage).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 8) {
                Rectangle().fill(Theme.Palette.line).frame(height: 1)
                Text("or use email").appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
                Rectangle().fill(Theme.Palette.line).frame(height: 1)
            }

            Picker("", selection: $mode) {
                Text("Sign in").tag(Mode.signIn)
                Text("Create account").tag(Mode.signUp)
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("Sign in or create account")

            if mode == .signUp {
                field("Name", text: $name)
            }
            field("Email", text: $email, keyboard: .emailAddress)
            field("Password", text: $password, secure: true)

            PrimaryButton(title: mode == .signIn ? "Continue with email" : "Create my account",
                          systemImage: "envelope.fill") {
                Task {
                    await backend.setServer(server)   // point at this server first
                    if mode == .signIn {
                        await backend.signIn(email: email, password: password)
                    } else {
                        await backend.signUp(email: email, password: password, name: name)
                    }
                }
            }

            if mode == .signIn {
                Button {
                    Task {
                        await backend.setServer(server)
                        try? await backend.requestPasswordReset(email: email)
                        resetMessage = "If that email exists, a reset link is on its way."
                    }
                } label: {
                    Text("Forgot password?").appFont(12, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                }
                .buttonStyle(.pressable)
                .disabled(email.isEmpty)
                if let resetMessage {
                    Text(resetMessage).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            #if DEBUG
            // Dev plumbing lives behind a collapsed disclosure so the page
            // reads like a traditional login; Release has no server field.
            DisclosureGroup(isExpanded: $showDevOptions) {
                VStack(alignment: .leading, spacing: 8) {
                    field("Server URL", text: $server, keyboard: .URL)
                    Text("On device, use your Mac's LAN address — e.g. http://192.168.x.x:8000 or http://<mac-name>.local:8000. The Simulator can keep localhost.")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Demo: pawan@cerebro.app · demo12345")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                }
                .padding(.top, 8)
            } label: {
                Text("Developer options")
                    .appFont(12, weight: .semibold).foregroundStyle(Theme.Palette.muted2)
            }
            .tint(Theme.Palette.muted2)
            #endif
        }
        .onAppear {
            if server.isEmpty { server = backend.baseURL }
            #if DEBUG
            // Dev convenience only: pre-fill the seeded demo login.
            // Must match the account created in backend/app/seed.py.
            if email.isEmpty { email = "pawan@cerebro.app" }
            if password.isEmpty { password = "demo12345" }
            if name.isEmpty { name = "Pawan" }
            #endif
        }
    }

    // MARK: Connected body — shows server-driven plan + insights

    private var connectedBody: some View {
        VStack(spacing: 14) {
            if let plan = backend.plan {
                SectionTitle(title: "Your agentic plan", trailing: nil)
                InsightCard(label: plan.source == "ai" ? "AI-generated" : "Adaptive plan",
                            title: plan.title, detail: plan.rationale)
                ForEach(plan.steps) { step in
                    Button {
                        Task { await backend.toggleStep(step.id, done: !step.done) }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: step.done ? "checkmark.circle.fill" : "circle")
                                .appFont(20, weight: .semibold)
                                .foregroundStyle(step.done ? Theme.Palette.lav : Theme.Palette.muted)
                            RowLabel(title: step.title, subtitle: step.detail,
                                     systemImage: step.symbol, emphasis: step.done)
                        }
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel(step.done ? "Mark \(step.title) not done" : "Mark \(step.title) done")
                }
            }
            if let insight = backend.insight {
                SectionTitle(title: "This week", trailing: nil)
                InsightCard(label: insight.headline, title: insight.summary)
                ForEach(insight.metrics, id: \.label) { m in
                    MetricBar(label: m.label, value: m.value, progress: m.progress)
                }
            }
            SecondaryButton(title: "Sign out", systemImage: "rectangle.portrait.and.arrow.right") {
                backend.signOut()
            }
        }
    }

    // Modern "Continue with Google" button (white, matching the Apple button).
    private var googleButton: some View {
        Button { signInWithGoogle() } label: {
            HStack(spacing: 8) {
                Image(systemName: "g.circle.fill").appFont(18, weight: .bold)
                Text("Continue with Google").appFont(15, weight: .semibold)
            }
            .foregroundStyle(Theme.Palette.ink)
            .frame(maxWidth: .infinity).frame(height: 50)
            .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier("Continue with Google")
    }

    /// Run Google's OAuth, then exchange the ID token with the backend.
    private func signInWithGoogle() {
        googleMessage = nil
        Task {
            do {
                await backend.setServer(server)
                let token = try await googleAuth.signIn()
                await backend.signInWithGoogle(idToken: token, name: "")
            } catch GoogleAuthError.cancelled {
                // user backed out — no message
            } catch {
                googleMessage = (error as? LocalizedError)?.errorDescription ?? "Google sign-in failed."
            }
        }
    }

    /// Extract Apple's identity token and exchange it with the backend.
    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        guard case let .success(auth) = result,
              let cred = auth.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else { return }
        let fullName = [cred.fullName?.givenName, cred.fullName?.familyName]
            .compactMap { $0 }.joined(separator: " ")
        Task {
            await backend.setServer(server)
            await backend.signInWithApple(identityToken: token, name: fullName)
        }
    }

    private func field(_ label: String, text: Binding<String>,
                       secure: Bool = false, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).appFont(11.5).foregroundStyle(Theme.Palette.muted)
            Group {
                if secure {
                    SecureField(label, text: text).accessibilityIdentifier(label)
                } else {
                    TextField(label, text: text)
                        .keyboardType(keyboard)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier(label)
                }
            }
            .foregroundStyle(Theme.Palette.text)
            .padding(.horizontal, 14).frame(height: 48)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.Palette.line))
        }
    }
}
