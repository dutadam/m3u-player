import SwiftUI
import Design

/// Kök görünüm: kaynak yoksa onboarding, varsa sekmeli ana arayüz.
struct RootView: View {
    @EnvironmentObject private var library: LibraryStore
    @StateObject private var mini = MiniPlayerStore()
    @State private var expanded: Channel?

    var body: some View {
        ZStack {
            Group {
                if library.channels.isEmpty {
                    if library.isLoading { LoadingView() }   // açılışta kayıtlı kaynak yükleniyor
                    else { OnboardingView() }
                } else {
                    RootTabView()
                }
            }
            // VLC mini pencere — kök seviyede, sekmeler üstünde yüzer
            MiniPlayerWindow { ch in mini.close(); expanded = ch }
        }
        .environmentObject(mini)
        .tint(Color.sgAccent)
        .background(Color.sgGround.ignoresSafeArea())
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: mini.isActive)
        .fullScreenCover(item: $expanded) { PlayerView(channel: $0) }
        .task { await library.restoreLastSession() }   // kayıtlı Xtream → otomatik giriş
    }
}

/// Markalı yükleme ekranı (açılışta kaynak geri yüklenirken).
struct LoadingView: View {
    var body: some View {
        VStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 18)
                .fill(LinearGradient(colors: [Color(hex: 0x1E2A42), Color.sgSurface],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 72, height: 72)
                .overlay(Image(systemName: "play.rectangle.fill").font(.system(size: 30)).foregroundStyle(Color.sgAccent))
                .shadow(color: Color.sgAccent.opacity(0.35), radius: 20)
            ProgressView().tint(Color.sgAccent)
            Text("cheesino yükleniyor…").font(.caption).foregroundStyle(Color.sgDim)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.sgGround.ignoresSafeArea())
    }
}

/// Ana sekmeler — içerik türüne göre net ayrım: Ana Sayfa · Canlı · Filmler · Diziler · Ara.
struct RootTabView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Ana Sayfa", systemImage: "house.fill") }
            LiveView()
                .tabItem { Label("Canlı", systemImage: "dot.radiowaves.left.and.right") }
            if !library.movies.isEmpty {
                MoviesView()
                    .tabItem { Label("Filmler", systemImage: "film.fill") }
            }
            if !library.series.isEmpty {
                SeriesTabView()
                    .tabItem { Label("Diziler", systemImage: "play.tv.fill") }
            }
            SearchView()
                .tabItem { Label("Ara", systemImage: "magnifyingglass") }
        }
    }
}
