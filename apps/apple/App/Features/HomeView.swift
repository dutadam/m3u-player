import SwiftUI
import Core
import Design

/// Ana ekran — otomatik dönen rasgele hero + yatay raylar. Tasarım: docs/design/ui-preview.html §01.
struct HomeView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?
    @State private var showLibrary = false
    @State private var heroItems: [HeroItem] = []

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 22) {
                    if library.isRefreshing {
                        HStack(spacing: 8) {
                            ProgressView().scaleEffect(0.8)
                            Text("İçerik yenileniyor…").font(.caption).foregroundStyle(Color.sgDim)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 4)
                    }
                    if !heroItems.isEmpty {
                        HeroCarousel(items: heroItems) { selected = $0 }
                    }

                    if !library.recentChannels.isEmpty {
                        PosterPlayRail(title: "Devam Et", channels: library.recentChannels) { selected = $0 }
                    }
                    if !library.favoriteChannels.isEmpty {
                        PosterPlayRail(title: "Favoriler", channels: library.favoriteChannels) { selected = $0 }
                    }

                    // Net içerik ayrımı (gizli kategoriler hariç)
                    let sports = library.visibleLive.filter { $0.group.localizedCaseInsensitiveContains("spor") }
                    if !sports.isEmpty { ChannelRail(title: "Canlı · Spor", channels: sports) { selected = $0 } }
                    if !library.visibleLive.isEmpty {
                        ChannelRail(title: "Canlı TV", channels: Array(library.visibleLive.prefix(20))) { selected = $0 }
                    }

                    // Akıllı film kategorileri (metadata'dan üretilen)
                    if !library.recentlyAddedMovies.isEmpty {
                        MovieRail(title: "Son Eklenenler", movies: library.recentlyAddedMovies)
                    }
                    if !library.topRatedMovies.isEmpty {
                        MovieRail(title: "Yüksek Puanlı · IMDb", movies: library.topRatedMovies)
                    }
                    if !library.cultClassicMovies.isEmpty {
                        MovieRail(title: "Kült & Klasik", movies: library.cultClassicMovies)
                    }
                    if !library.visibleMovies.isEmpty {
                        MovieRail(title: "Filmler", movies: library.visibleMovies)
                    }
                    if !library.series.isEmpty { SeriesRailHome(series: library.series) }
                }
                .padding(.vertical, 8)
            }
            .background(Color.sgGround)
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { await library.refresh() }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { Task { await library.refresh() } } label: {
                        Image(systemName: "arrow.clockwise").font(.system(size: 15, weight: .semibold))
                    }.disabled(library.isRefreshing)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showLibrary = true } label: {
                        Image(systemName: "line.3.horizontal").font(.system(size: 16, weight: .semibold))
                    }
                }
            }
            .sheet(isPresented: $showLibrary) { LibraryView() }
            .navigationDestination(for: Channel.self) { MovieDetailView(channel: $0) }
            .navigationDestination(for: SeriesRef.self) { SeriesDetailView(ref: $0) }
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
            .onAppear(perform: buildHero)
        }
    }

    /// Rasgele film + dizi seç (görseli olanlardan) → dönen hero.
    private func buildHero() {
        guard heroItems.isEmpty else { return }
        var pool: [HeroItem] = []
        pool += library.visibleMovies.filter { $0.logo != nil }.map { .movie($0) }
        pool += library.series.filter { $0.cover != nil }.map { .series($0) }
        heroItems = Array(pool.shuffled().prefix(8))
    }
}

/// Hero öğesi — rasgele film veya dizi.
enum HeroItem: Identifiable {
    case movie(Channel)
    case series(SeriesRef)
    var id: String {
        switch self { case .movie(let c): return "m_" + c.id; case .series(let s): return "s_\(s.id)" }
    }
    var title: String { switch self { case .movie(let c): return c.name; case .series(let s): return s.name } }
    var image: URL? { switch self { case .movie(let c): return c.logo; case .series(let s): return s.cover } }
    var subtitle: String { switch self { case .movie(let c): return c.group; case .series(let s): return s.genre ?? s.group } }
}

/// Otomatik dönen sinematik hero carousel (rasgele film/dizi, süreyle değişir).
struct HeroCarousel: View {
    let items: [HeroItem]
    var onPlayMovie: (Channel) -> Void
    @State private var index = 0
    private let timer = Timer.publish(every: 6, on: .main, in: .common).autoconnect()

    var body: some View {
        TabView(selection: $index) {
            ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                HeroSlide(item: item, onPlayMovie: onPlayMovie).tag(i)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .frame(height: 320)
        .onReceive(timer) { _ in
            guard items.count > 1 else { return }
            withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % items.count }
        }
    }
}

/// Tek hero slaytı — büyük görsel + başlık + aksiyon (film → detay, dizi → detay).
struct HeroSlide: View {
    let item: HeroItem
    var onPlayMovie: (Channel) -> Void
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GeometryReader { geo in
                AsyncImage(url: item.image) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    LinearGradient(colors: [Color(hex: 0x20304F), Color(hex: 0x101521)],
                                   startPoint: .top, endPoint: .bottom)
                }
                .frame(width: geo.size.width, height: geo.size.height).clipped()
            }
            LinearGradient(colors: [.clear, .clear, .sgGround.opacity(0.98)], startPoint: .top, endPoint: .bottom)

            VStack(alignment: .leading, spacing: 8) {
                Text(item.subtitle.uppercased()).font(.system(size: 11, weight: .heavy))
                    .foregroundStyle(Color.sgAccent).lineLimit(1)
                Text(item.title).font(.system(size: 22, weight: .heavy)).foregroundStyle(.white).lineLimit(2)
                destinationLink
            }
            .padding(18).padding(.bottom, 22)
        }
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, SGMetric.gutter)
    }

    @ViewBuilder private var destinationLink: some View {
        switch item {
        case .movie(let c):
            NavigationLink(value: c) { heroButton("Detay") }.buttonStyle(PressableStyle())
        case .series(let s):
            NavigationLink(value: s) { heroButton("Detay") }.buttonStyle(PressableStyle())
        }
    }

    private func heroButton(_ label: String) -> some View {
        Label(label, systemImage: "info.circle.fill")
            .font(.system(size: 14, weight: .bold))
            .padding(.horizontal, 16).padding(.vertical, 9)
            .background(.white, in: RoundedRectangle(cornerRadius: 10))
            .foregroundStyle(Color.sgGround)
    }
}

/// Büyük poster play-rayı — Devam Et / Favoriler (dokun → oynat, resume sorar).
struct PosterPlayRail: View {
    @EnvironmentObject private var library: LibraryStore
    let title: String
    let channels: [Channel]
    var onTap: (Channel) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title).font(.headline).foregroundStyle(Color.sgText)
                .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(channels.prefix(30)) { ch in
                        Button { onTap(ch) } label: {
                            PosterCard(title: ch.name, poster: ch.logo,
                                       watched: library.isWatched(ch.url.absoluteString),
                                       progress: library.watchFraction(for: ch.url.absoluteString),
                                       width: 120)
                        }.buttonStyle(PressableStyle())
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Sinematik hero kartı.
struct HeroCard: View {
    @EnvironmentObject private var library: LibraryStore
    let channel: Channel
    var onPlay: () -> Void

    private var now: EpgEntry? { library.epg?.nowPlaying(for: channel) }
    private var progress: Double {
        guard let n = now else { return 0 }
        let total = n.stop.timeIntervalSince(n.start)
        guard total > 0 else { return 0 }
        return min(1, max(0, Date().timeIntervalSince(n.start) / total))
    }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [Color(hex: 0x20304F), Color(hex: 0x3A2340), Color(hex: 0x101521)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            LinearGradient(colors: [.clear, .sgGround.opacity(0.96)], startPoint: .center, endPoint: .bottom)

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    LivePill()
                    if let q = channel.quality { QualityBadge(q.rawValue) }
                }
                Text(channel.name).font(.system(size: 22, weight: .heavy)).foregroundStyle(.white)
                if let n = now {
                    Text(n.title).font(.subheadline).foregroundStyle(Color.sgText).lineLimit(1)
                    ProgressBarLine(fraction: progress).frame(width: 160)
                } else {
                    Text(channel.group).font(.subheadline).foregroundStyle(Color.sgDim)
                }
                Button(action: onPlay) {
                    Label("İzle", systemImage: "play.fill")
                        .font(.system(size: 14, weight: .bold))
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(.white, in: RoundedRectangle(cornerRadius: 10))
                        .foregroundStyle(Color.sgGround)
                }
                .buttonStyle(.plain)
                .padding(.top, 6)
            }
            .padding(16)
        }
        .frame(height: 230)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, SGMetric.gutter)
    }
}

/// Yatay film rayı — 2:3 poster kartları, film detayına gider (izlendi/ilerleme rozetli).
/// category verilirse başlıkta "Tümü ›" ile kategori grid'ine köprü.
struct MovieRail: View {
    @EnvironmentObject private var library: LibraryStore
    let title: String
    let movies: [Channel]
    var category: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(title).font(.headline).foregroundStyle(Color.sgText).lineLimit(1)
                Spacer()
                if let category {
                    NavigationLink(value: MovieCatDest(name: category)) {
                        Text("Tümü ›").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.sgAccent)
                    }
                }
            }
            .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(movies.prefix(30)) { m in
                        NavigationLink(value: m) {
                            PosterCard(title: m.name, poster: m.logo,
                                       watched: library.isWatched(m.url.absoluteString),
                                       progress: library.watchFraction(for: m.url.absoluteString),
                                       width: 120)
                        }.buttonStyle(PressableStyle())
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Film kategori grid'i navigasyon hedefi.
struct MovieCatDest: Hashable { let name: String }
/// Dizi kategori grid'i navigasyon hedefi.
struct SeriesCatDest: Hashable { let name: String }

/// Yatay dizi rayı — poster kartları, detaya gider. category verilirse "Tümü ›".
struct SeriesRailHome: View {
    var title: String = "Diziler"
    let series: [SeriesRef]
    var category: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(title).font(.headline).foregroundStyle(Color.sgText).lineLimit(1)
                Spacer()
                if let category {
                    NavigationLink(value: SeriesCatDest(name: category)) {
                        Text("Tümü ›").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.sgAccent)
                    }
                }
            }
            .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(series.prefix(30)) { s in
                        NavigationLink(value: s) {
                            SeriesPoster(ref: s, width: 120)
                        }.buttonStyle(PressableStyle())
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Yatay kanal rayı.
struct ChannelRail: View {
    let title: String
    let channels: [Channel]
    var onTap: (Channel) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title).font(.headline).foregroundStyle(Color.sgText)
                .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(channels.prefix(20)) { ch in
                        ChannelCard(channel: ch).onTapGesture { onTap(ch) }
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Kanal kartı (logo + isim + kalite). displayName ile grup base adı gösterilebilir.
struct ChannelCard: View {
    let channel: Channel
    var displayName: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 11)
                    .fill(LinearGradient(colors: [Color(hex: 0x26304A), Color(hex: 0x171B28)],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                AsyncImage(url: channel.logo) { img in
                    img.resizable().scaledToFit().padding(10)
                } placeholder: {
                    Text(String((displayName ?? channel.name).prefix(2)).uppercased())
                        .font(.system(size: 15, weight: .heavy)).foregroundStyle(.white.opacity(0.9))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if let q = channel.quality {
                    QualityBadge(q.rawValue).padding(6)
                }
            }
            .frame(width: 118, height: 70)
            Text(displayName ?? channel.name).font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.sgText).lineLimit(1).frame(width: 118, alignment: .leading)
        }
    }
}
