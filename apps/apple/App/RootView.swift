import SwiftUI
import Design

/// Kök görünüm: kaynak yoksa onboarding, varsa sekmeli ana arayüz.
struct RootView: View {
    @EnvironmentObject private var library: LibraryStore

    var body: some View {
        Group {
            if library.channels.isEmpty {
                OnboardingView()
            } else {
                RootTabView()
            }
        }
        .tint(.sgAccent)
        .background(Color.sgGround.ignoresSafeArea())
        .task { await library.restoreLastSession() }   // kayıtlı Xtream → otomatik giriş
    }
}

/// Ana sekmeler — tasarım şartnamesindeki tab bar (İzle/Canlı/Rehber/Ara/Kitaplık).
struct RootTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("İzle", systemImage: "house.fill") }
            LiveView()
                .tabItem { Label("Canlı", systemImage: "dot.radiowaves.left.and.right") }
            GuideView()
                .tabItem { Label("Rehber", systemImage: "rectangle.grid.1x2") }
            SearchView()
                .tabItem { Label("Ara", systemImage: "magnifyingglass") }
            LibraryView()
                .tabItem { Label("Kitaplık", systemImage: "square.stack.fill") }
        }
    }
}
