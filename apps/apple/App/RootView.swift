import SwiftUI
import Core
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
    var caption: String = "cheesino yükleniyor…"
    var body: some View {
        BrandLoader(size: 108, caption: caption)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.sgGround.ignoresSafeArea())
    }
}

// MARK: - Marka bileşenleri

/// cheesino logo işareti (asset: BrandMark, template render → istenen renk + glow).
struct BrandMark: View {
    var size: CGFloat = 72
    var color: Color = .sgAccent
    var glow: Bool = true
    var body: some View {
        Image("BrandMark")
            .renderingMode(.template)
            .resizable().scaledToFit()
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .shadow(color: glow ? color.opacity(0.5) : .clear, radius: glow ? size * 0.18 : 0)
    }
}

/// Markalı yükleme animasyonu — logo alttan üste marka gradyanıyla "dolar" + glow nabzı.
/// Dizi/film/kanal yüklenirken kullanılır. Tek giriş noktası: ileride Lottie/Rive eklenirse
/// (MobileVLCKit gibi SPM ile) `#if canImport(Lottie)` dalında buraya LottieView takılabilir;
/// aşağıdaki native animasyon güvenli fallback olarak kalır.
struct BrandLoader: View {
    var size: CGFloat = 96
    var caption: String? = nil
    @State private var fill: CGFloat = 0
    @State private var glow = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                // Sönük taban (logo silüeti)
                Image("BrandMark").renderingMode(.template).resizable().scaledToFit()
                    .foregroundStyle(.white.opacity(0.10))
                // Dolan marka gradyanı — alttan üste maske
                Image("BrandMark").renderingMode(.template).resizable().scaledToFit()
                    .foregroundStyle(LinearGradient(colors: [Color.sgGold, Color.sgAccent],
                                                    startPoint: .bottom, endPoint: .top))
                    .mask(alignment: .bottom) {
                        GeometryReader { g in
                            Rectangle()
                                .frame(height: g.size.height * fill)
                                .frame(maxHeight: .infinity, alignment: .bottom)
                        }
                    }
            }
            .frame(width: size, height: size)
            .shadow(color: Color.sgAccent.opacity(glow ? 0.55 : 0.18), radius: glow ? size * 0.28 : size * 0.12)
            if let caption {
                Text(caption).font(.caption).foregroundStyle(Color.sgDim)
            }
        }
        .onAppear(perform: animate)
    }

    private func animate() {
        if reduceMotion { fill = 1; return }
        withAnimation(.easeInOut(duration: 1.15).repeatForever(autoreverses: true)) { fill = 1 }
        withAnimation(.easeInOut(duration: 1.15).repeatForever(autoreverses: true)) { glow = true }
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
