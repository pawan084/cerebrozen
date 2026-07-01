import SwiftUI

struct RootView: View {
    @EnvironmentObject var state: AppState
    // Skip the splash under UI tests (they pass -resetState) so the suite stays fast.
    @State private var showSplash = !ProcessInfo.processInfo.arguments.contains("-resetState")

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
        .task {
            guard showSplash else { return }
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation(.easeOut(duration: 0.6)) { showSplash = false }
        }
    }
}

struct MainTabView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService

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
        .tint(Theme.Palette.soft)
        .toolbarBackground(.ultraThinMaterial, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        // Keep the server's crisis region in sync with the app's effective region
        // (explicit override, else device locale) so AI crisis replies are correct.
        .task {
            backend.syncCrisisRegion(CrisisDirectory.effectiveRegion(state.crisisRegion))
            backend.syncConsent(state.consent)
        }
        .onChange(of: state.crisisRegion) { _, new in
            backend.syncCrisisRegion(CrisisDirectory.effectiveRegion(new))
        }
        .onChange(of: state.consent) { _, new in backend.syncConsent(new) }
    }
}
