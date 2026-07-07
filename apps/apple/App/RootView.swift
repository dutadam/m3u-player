import SwiftUI
import Design

/// Kök görünüm: kaynak yoksa onboarding, varsa sekmeli ana arayüz.
struct RootView: View {
    @EnvironmentObject private var library: LibraryStore

    var body: some View {
        Group {
            if library.channels.isEmpty {
                if library.isLoading { LoadingView() }   // açılışta kayıtlı kaynak yükleniyor
                else { OnboardingView() }
            } else {
                RootTabView()
            }
        }
        .tint(Color.sgAccent)
        .background(Color.sgGround.ignoresSafeArea())
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
