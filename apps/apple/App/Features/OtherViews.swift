import SwiftUI
import Core
import Design

/// Canlı — kategori bazlı yatay raylar + benzer kanal (kalite varyantı) gruplama.
/// Rehber ve Çoklu Ekran girişleri ikon+metin etiketli belirgin kartlar.
struct LiveView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?
    @State private var cats: [String] = []
    @State private var grouped: [String: [ChannelGroup]] = [:]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    actionRow
                    ForEach(cats, id: \.self) { cat in
                        if let gs = grouped[cat], !gs.isEmpty {
                            LiveCategoryRail(category: cat, groups: gs) { selected = $0 }
                        }
                    }
                    if cats.isEmpty {
                        Text("Canlı kanal bulunamadı").font(.subheadline)
                            .foregroundStyle(Color.sgDim).padding(.top, 40)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 10)
            }
            .background(Color.sgGround)
            .navigationTitle("Canlı")
            .navigationDestination(for: LiveDest.self) { dest in
                switch dest {
                case .guide: GuideView()
                case .multi: MultiView()
                case .category(let c): CategoryBrowseView(title: c, channels: library.live.filter { $0.group == c })
                }
            }
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
            .onAppear(perform: rebuild)
            .onChange(of: library.channels.count) { _ in rebuild() }
            .onChange(of: library.hiddenCategories) { _ in rebuild() }
        }
    }

    private var actionRow: some View {
        HStack(spacing: 12) {
            NavigationLink(value: LiveDest.guide) {
                LiveActionCard(title: "Rehber", subtitle: "TV yayın akışı", icon: "rectangle.grid.1x2.fill")
            }.buttonStyle(PressableStyle())
            NavigationLink(value: LiveDest.multi) {
                LiveActionCard(title: "Çoklu Ekran", subtitle: "6 yayına kadar", icon: "square.grid.2x2.fill")
            }.buttonStyle(PressableStyle())
        }
        .padding(.horizontal, SGMetric.gutter)
    }

    /// visibleLive'ı tek geçişte kategori → base-ad → varyant olarak grupla (kalite HD/FHD/4K birleşir).
    private func rebuild() {
        var order: [String] = []
        var seen = Set<String>()
        var buckets: [String: [String: [Channel]]] = [:]
        for ch in library.visibleLive {
            if !seen.contains(ch.group) { seen.insert(ch.group); order.append(ch.group) }
            let base = Self.baseName(ch.name)
            buckets[ch.group, default: [:]][base, default: []].append(ch)
        }
        var out: [String: [ChannelGroup]] = [:]
        for (cat, bmap) in buckets {
            out[cat] = bmap.map { ChannelGroup(id: cat + "|" + $0.key, base: $0.key, category: cat, variants: $0.value) }
                           .sorted { $0.base.localizedCaseInsensitiveCompare($1.base) == .orderedAscending }
        }
        cats = order
        grouped = out
    }

    /// Kalite eklerini sök → benzer kanalları aynı gruba topla.
    static func baseName(_ n: String) -> String {
        var s = " " + n.uppercased() + " "
        for t in [" 4K ", " UHD ", " FHD ", " FULLHD ", " FULL HD ", " HD ", " SD ",
                  " HEVC ", " H265 ", " H.265 ", " RAW ", " ᴴᴰ "] {
            s = s.replacingOccurrences(of: t, with: " ")
        }
        let joined = s.split(separator: " ").joined(separator: " ")
        return joined.isEmpty ? n : joined
    }
}

/// Canlı ekranı navigasyon hedefleri.
enum LiveDest: Hashable { case guide, multi, category(String) }

/// Benzer kanal grubu (aynı base ad, farklı kalite varyantları).
struct ChannelGroup: Identifiable, Hashable {
    let id: String
    let base: String
    let category: String
    let variants: [Channel]
    var primary: Channel { variants.max { qRank($0) < qRank($1) } ?? variants[0] }
    private func qRank(_ c: Channel) -> Int {
        switch c.quality {
        case .uhd4k?: return 4
        case .fhd?: return 3
        case .hd?: return 2
        case .sd?: return 1
        case .none: return 0
        }
    }
}

/// İkon + başlık + alt-metin etiketli aksiyon kartı (Rehber / Çoklu Ekran).
struct LiveActionCard: View {
    let title: String; let subtitle: String; let icon: String
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 20, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 42, height: 42)
                .background(LinearGradient(colors: [Color.sgAccent, Color.sgAccent2],
                                           startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: RoundedRectangle(cornerRadius: 11))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 14, weight: .bold)).foregroundStyle(Color.sgText)
                Text(subtitle).font(.system(size: 10)).foregroundStyle(Color.sgMute)
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .frame(maxWidth: .infinity)
        .background(Color.sgSurface, in: RoundedRectangle(cornerRadius: 13))
    }
}

/// Kategori rayı — başlık + "Tümü" + yatay kanal kartları.
struct LiveCategoryRail: View {
    let category: String
    let groups: [ChannelGroup]
    var onPlay: (Channel) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(category).font(.headline).foregroundStyle(Color.sgText).lineLimit(1)
                Spacer()
                NavigationLink(value: LiveDest.category(category)) {
                    Text("Tümü ›").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.sgAccent)
                }
            }
            .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(groups.prefix(20)) { g in
                        Button { onPlay(g.primary) } label: {
                            ChannelCard(channel: g.primary, displayName: g.base)
                        }
                        .buttonStyle(PressableStyle())
                        .contextMenu {
                            if g.variants.count > 1 {
                                ForEach(g.variants) { v in
                                    Button { onPlay(v) } label: {
                                        Label(v.quality?.rawValue ?? v.name, systemImage: "play.fill")
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Filmler — ana sayfa dilinde kategori rayları (son eklenene göre, 30 içerik) + akıllı raylar.
/// Ray başlığındaki "Tümü ›" mevcut kategori grid'ine gider.
struct MoviesView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var cats: [String] = []
    @State private var byCat: [String: [Channel]] = [:]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    if !library.recentlyAddedMovies.isEmpty {
                        MovieRail(title: "Son Eklenenler", movies: library.recentlyAddedMovies)
                    }
                    if !library.topRatedMovies.isEmpty {
                        MovieRail(title: "Yüksek Puanlı · IMDb", movies: library.topRatedMovies)
                    }
                    ForEach(cats, id: \.self) { c in
                        if let ms = byCat[c], !ms.isEmpty {
                            MovieRail(title: c, movies: ms, category: c)
                        }
                    }
                    if cats.isEmpty {
                        Text("Film bulunamadı").font(.subheadline)
                            .foregroundStyle(Color.sgDim).frame(maxWidth: .infinity).padding(.top, 40)
                    }
                }
                .padding(.vertical, 10)
            }
            .background(Color.sgGround)
            .navigationTitle("Filmler")
            .navigationDestination(for: Channel.self) { MovieDetailView(channel: $0) }
            .navigationDestination(for: MovieCatDest.self) { d in
                CategoryBrowseView(title: d.name, channels: library.movies.filter { $0.group == d.name }, poster: true)
            }
            .onAppear(perform: rebuild)
            .onChange(of: library.channels.count) { _ in rebuild() }
            .onChange(of: library.hiddenCategories) { _ in rebuild() }
        }
    }

    private func rebuild() {
        var d: [String: [Channel]] = [:]
        for m in library.visibleMovies { d[m.group, default: []].append(m) }
        // Her kategori: beğeni + tür afinitesi + yenilik karışık sıralama
        for k in d.keys { d[k] = library.feedSorted(d[k] ?? []) }
        cats = d.keys.sorted { (d[$0]?.count ?? 0) > (d[$1]?.count ?? 0) }
        byCat = d
    }
}

/// Diziler — kategori rayları (ana sayfa dili) + "Tümü ›" ile kategori grid'i.
struct SeriesTabView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var cats: [String] = []
    @State private var byCat: [String: [SeriesRef]] = [:]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    ForEach(cats, id: \.self) { c in
                        if let ss = byCat[c], !ss.isEmpty {
                            SeriesRailHome(title: c, series: ss, category: c)
                        }
                    }
                    if cats.isEmpty {
                        Text("Dizi bulunamadı").font(.subheadline)
                            .foregroundStyle(Color.sgDim).frame(maxWidth: .infinity).padding(.top, 40)
                    }
                }
                .padding(.vertical, 10)
            }
            .background(Color.sgGround)
            .navigationTitle("Diziler")
            .navigationDestination(for: SeriesRef.self) { SeriesDetailView(ref: $0) }
            .navigationDestination(for: SeriesCatDest.self) { d in
                SeriesGridView(category: d.name,
                               series: library.series.filter { $0.group == d.name && !library.isCategoryHidden($0.group) })
            }
            .onAppear(perform: rebuild)
            .onChange(of: library.series.count) { _ in rebuild() }
            .onChange(of: library.hiddenCategories) { _ in rebuild() }
        }
    }

    private func rebuild() {
        var d: [String: [SeriesRef]] = [:]
        for s in library.series where !library.isCategoryHidden(s.group) { d[s.group, default: []].append(s) }
        cats = d.keys.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        byCat = d
    }
}

/// Gelişmiş arama — canlı + film + dizi tek aramada, tür filtresiyle.
/// Gizli kategoriler hariç tutulur. Kanal sonuçları oynatıcıyı açar, dizi sonuçları detaya gider.
struct SearchView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var query = ""
    @State private var scope: SearchScope = .all
    @State private var selected: Channel?

    enum SearchScope: String, CaseIterable, Identifiable {
        case all = "Tümü", live = "Canlı", movie = "Film", series = "Dizi"
        var id: String { rawValue }
    }

    private var trimmed: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var liveResults: [Channel] {
        guard scope == .all || scope == .live, !trimmed.isEmpty else { return [] }
        return library.visibleLive.filter { $0.name.localizedCaseInsensitiveContains(trimmed) }
    }
    private var movieResults: [Channel] {
        guard scope == .all || scope == .movie, !trimmed.isEmpty else { return [] }
        return library.visibleMovies.filter { $0.name.localizedCaseInsensitiveContains(trimmed) }
    }
    private var seriesResults: [SeriesRef] {
        guard scope == .all || scope == .series, !trimmed.isEmpty else { return [] }
        return library.series.filter {
            !library.isCategoryHidden($0.group) && $0.name.localizedCaseInsensitiveContains(trimmed)
        }
    }

    private var totalCount: Int { liveResults.count + movieResults.count + seriesResults.count }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                scopePicker
                Divider().overlay(Color.sgLineSoft)
                content
            }
            .background(Color.sgGround)
            .navigationTitle("Ara")
            .searchable(text: $query, prompt: "Kanal, film, dizi ara")
            .navigationDestination(for: SeriesRef.self) { SeriesDetailView(ref: $0) }
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
        }
    }

    private var scopePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(SearchScope.allCases) { s in
                    let on = scope == s
                    Button { scope = s } label: {
                        Text(s.rawValue)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(on ? Color.white : Color.sgDim)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(on ? Color.sgAccent : Color.sgSurface, in: Capsule())
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, SGMetric.gutter).padding(.vertical, 10)
        }
    }

    @ViewBuilder private var content: some View {
        if trimmed.isEmpty {
            emptyState(icon: "magnifyingglass", text: "Kanal, film veya dizi ara")
        } else if totalCount == 0 {
            emptyState(icon: "tray", text: "“\(trimmed)” için sonuç bulunamadı")
        } else {
            List {
                if !seriesResults.isEmpty {
                    Section("Diziler (\(seriesResults.count))") {
                        ForEach(seriesResults.prefix(40)) { s in
                            NavigationLink(value: s) { seriesRow(s) }
                                .listRowBackground(Color.sgGround)
                        }
                    }
                }
                if !liveResults.isEmpty {
                    Section("Canlı (\(liveResults.count))") {
                        ForEach(liveResults.prefix(60)) { ch in
                            resultButton(ch, kindLabel: ch.group)
                        }
                    }
                }
                if !movieResults.isEmpty {
                    Section("Filmler (\(movieResults.count))") {
                        ForEach(movieResults.prefix(60)) { ch in
                            resultButton(ch, kindLabel: ch.group)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackgroundHiddenIfAvailable()
            .background(Color.sgGround)
        }
    }

    private func resultButton(_ ch: Channel, kindLabel: String) -> some View {
        Button { selected = ch } label: {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8).fill(Color.sgElevated)
                    AsyncImage(url: ch.logo) { $0.resizable().scaledToFit().padding(6) } placeholder: {
                        Text(String(ch.name.prefix(2)).uppercased())
                            .font(.system(size: 12, weight: .heavy)).foregroundStyle(.white.opacity(0.85))
                    }
                }
                .frame(width: 52, height: 34).clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(ch.name).foregroundStyle(Color.sgText).lineLimit(1)
                    Text(kindLabel).font(.caption).foregroundStyle(Color.sgMute).lineLimit(1)
                }
                Spacer()
                Image(systemName: "play.circle.fill").foregroundStyle(Color.sgAccent)
            }
        }.listRowBackground(Color.sgGround)
    }

    private func seriesRow(_ s: SeriesRef) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8).fill(Color.sgElevated)
                AsyncImage(url: s.cover) { $0.resizable().scaledToFill() } placeholder: {
                    Image(systemName: "play.tv").foregroundStyle(Color.sgMute)
                }
            }
            .frame(width: 34, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 2) {
                Text(s.name).foregroundStyle(Color.sgText).lineLimit(1)
                Text(s.genre ?? s.group).font(.caption).foregroundStyle(Color.sgMute).lineLimit(1)
            }
        }
    }

    private func emptyState(icon: String, text: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon).font(.largeTitle).foregroundStyle(Color.sgMute)
            Text(text).font(.subheadline).foregroundStyle(Color.sgDim)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private extension View {
    @ViewBuilder func scrollContentBackgroundHiddenIfAvailable() -> some View {
        if #available(iOS 16.0, tvOS 16.0, *) { self.scrollContentBackground(.hidden) }
        else { self }
    }
}

/// Kitaplık — favoriler / son izlenenler / filmler / diziler / çoklu ekran / ayarlar.
struct LibraryView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        NavigationStack {
            List {
                NavigationLink { PlaylistsView() } label: { Label("Kaynaklar", systemImage: "square.stack.3d.up.fill") }
                NavigationLink { FavoritesView() } label: { Label("Favoriler", systemImage: "heart.fill") }
                NavigationLink { RecentsView() } label: { Label("Son İzlenenler", systemImage: "clock.arrow.circlepath") }
                NavigationLink { MultiView() } label: { Label("Çoklu Ekran", systemImage: "square.grid.2x2.fill") }
                NavigationLink { CategoryManagerView() } label: { Label("Kategori Yönetimi", systemImage: "line.3.horizontal.decrease.circle") }
                NavigationLink { SettingsView() } label: { Label("Ayarlar", systemImage: "gearshape.fill") }
            }
            .listStyle(.plain)
            .navigationTitle("Kitaplık")
        }
    }
}

// MultiView → MultiViewScreen.swift (gerçek 4-yayın oynatma)
