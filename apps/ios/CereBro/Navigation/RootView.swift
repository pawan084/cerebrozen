import SwiftUI

struct RootView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var theme: AppTheme
    // Skip the splash under UI tests (they pass -resetState) so the suite stays fast.
    @State private var showSplash = !ProcessInfo.processInfo.arguments.contains("-resetState")
    /// The device's own appearance. Reads true because "Match device" applies
    /// no `preferredColorScheme` override (see `AppTheme.preferredScheme`); it
    /// simply echoes our own override in the two pinned modes, where it is
    /// unused. Switching back to "Match device" therefore self-heals.
    @Environment(\.colorScheme) private var systemScheme

    /// Splash + the signed-out funnel are Night regardless of preference: both
    /// are bespoke night art, and both are exclusive full-window experiences,
    /// which is what makes one global flag safe (nothing else is on screen to
    /// be painted the wrong way).
    private var pinsNight: Bool { showSplash || !state.hasOnboarded }

    var body: some View {
        ZStack {
            Group {
                if state.hasOnboarded {
                    MainTabView()
                } else {
                    OnboardingFlow()
                }
            }
            .animation(.easeInOut, value: state.hasOnboarded)

            if showSplash {
                SplashView()
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        // The design tokens are global statics (SwiftUI has no scoped-variable
        // equivalent), so a theme flip is made visible by rebuilding the tree
        // once. `generation` only moves when the RESOLVED theme changes, so
        // this costs nothing on an input change that doesn't flip the outcome.
        .id(theme.generation)
        .task {
            guard showSplash else { return }
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation(.easeOut(duration: 0.6)) { showSplash = false }
        }
        .onAppear {
            theme.setSystemDark(systemScheme == .dark)
            theme.setForceNight(pinsNight)
        }
        .onChange(of: systemScheme) { _, s in theme.setSystemDark(s == .dark) }
        .onChange(of: pinsNight) { _, pin in theme.setForceNight(pin) }
    }
}

struct MainTabView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var showTour = false

    var body: some View {
        TabView(selection: $state.selectedTab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppState.Tab.home)

            NavigationStack { SleepHomeView() }
                .tabItem { Label("Sleep", systemImage: "moon.fill") }
                .tag(AppState.Tab.sleep)

            NavigationStack { TalkView() }
                .tabItem { Label("Talk", systemImage: "mic.fill") }
                .tag(AppState.Tab.talk)

            NavigationStack { JournalHomeView() }
                .tabItem { Label("Journal", systemImage: "book.fill") }
                .tag(AppState.Tab.journal)

            NavigationStack { ProfileView() }
                .tabItem { Label("You", systemImage: "person.fill") }
                .tag(AppState.Tab.you)
        }
        .tint(Theme.Palette.tint)
        // The material follows the resolved colour scheme on its own, so the
        // tab bar lightens with Dawn without a token of its own.
        .toolbarBackground(.ultraThinMaterial, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        // Keep the server's crisis region in sync with the app's effective region
        // (explicit override, else device locale) so AI crisis replies are correct.
        .task {
            backend.syncCrisisRegion(CrisisDirectory.effectiveRegion(state.crisisRegion))
            backend.syncConsent(state.consent)
            // The 18+ tap time from onboarding rides along with attest() at the
            // next connect (a sign-in can happen many launches later).
            backend.syncAgeConfirmation(state.ageConfirmedAt)
            // Seed the assessment cache from persisted state each launch, so a
            // sign-in on a later launch still syncs the onboarding choices —
            // but only a REAL answered reflection: pushing the app defaults
            // would overwrite a returning user's server-side selection.
            if state.hasAssessment {
                backend.saveAssessment(motivations: state.selectedMotivations, goals: state.selectedGoals)
            }
        }
        // Returning user on a fresh install (signed in without re-answering the
        // reflection): adopt the server's selection so Home/plan personalize.
        .onChange(of: backend.isConnected) { _, connected in
            guard connected, !state.hasAssessment, let u = backend.user else { return }
            let motivations = u.motivations ?? []
            if !u.goals.isEmpty || !motivations.isEmpty {
                state.selectedMotivations = motivations
                state.selectedGoals = u.goals
                state.hasAssessment = true
            }
        }
        // Returning user on a fresh install: adopt the server's companion style
        // unless this device has already been switched off the default (the
        // picker pushes local changes itself, so local intent still wins).
        .onChange(of: backend.isConnected) { _, connected in
            guard connected, let u = backend.user, !u.companion.isEmpty else { return }
            if state.companion == "Calm Guide", u.companion != state.companion {
                state.companion = u.companion
            }
        }
        .onChange(of: state.crisisRegion) { _, new in
            backend.syncCrisisRegion(CrisisDirectory.effectiveRegion(new))
        }
        .onChange(of: state.consent) { _, new in backend.syncConsent(new) }
        // Guided tour lives at the tab level so it renders above any pushed
        // screen — the You-tab replay row works from anywhere, not just when
        // Home happens to be at its stack root. First run auto-shows it;
        // never under -resetState (UITests must stay deterministic).
        .onAppear {
            if !GuidedTourOverlay.isDone,
               !UserDefaults.standard.bool(forKey: "resetState") {
                showTour = true
            }
        }
        .onChange(of: state.tourRequested) { _, requested in
            guard requested else { return }
            state.selectedTab = .home
            showTour = true
            state.tourRequested = false
        }
        .overlay { if showTour { GuidedTourOverlay(isPresented: $showTour) } }
    }
}
