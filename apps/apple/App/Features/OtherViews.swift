import SwiftUI
import Core
import Design

/// Canlı kanallar — kategori-bazlı gözatma + Rehber girişi.
struct LiveView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        NavigationStack {
            CategoryBrowseView(title: "Canlı", channels: library.live)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        NavigationLink { GuideView() } label: {
                            Label("Rehber", systemImage: "rectangle.grid.1x2")
                        }
                    }
                }
        }
    }
}

/// Filmler (VOD) — kategori-bazlı gözatma.
struct MoviesView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        NavigationStack {
            CategoryBrowseView(title: "Filmler", channels: library.movies)
        }
    }
}

/// Diziler sekmesi — katalog + detay.
struct SeriesTabView: View {
    var body: some View {
        NavigationStack { SeriesListView() }
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
