import SwiftUI
import Core
import Design

/// Xtream dizi referansı (liste öğesi). Bölümler detayda get_series_info ile lazy çekilir.
struct SeriesRef: Identifiable, Hashable {
    let id: Int
    let name: String
    let cover: URL?
    let genre: String?
    let group: String
}

/// Dizi kataloğu — poster grid. Tasarım diline uygun.
struct SeriesListView: View {
    @EnvironmentObject private var library: LibraryStore
    private let cols = [GridItem(.adaptive(minimum: 110), spacing: 12)]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: cols, spacing: 16) {
                ForEach(library.series) { s in
                    NavigationLink(value: s) { SeriesPoster(ref: s) }
                        .buttonStyle(.plain)
                }
            }
            .padding(SGMetric.gutter)
        }
        .background(Color.sgGround)
        .navigationTitle("Diziler")
        .navigationDestination(for: SeriesRef.self) { SeriesDetailView(ref: $0) }
    }
}

struct SeriesPoster: View {
    let ref: SeriesRef
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.sgElevated)
                AsyncImage(url: ref.cover) { $0.resizable().scaledToFill() } placeholder: {
                    Image(systemName: "play.tv").font(.title).foregroundStyle(.sgMute)
                }
            }
            .frame(height: 156).clipShape(RoundedRectangle(cornerRadius: 12))
            Text(ref.name).font(.system(size: 12, weight: .semibold)).foregroundStyle(.sgText).lineLimit(2)
        }
    }
}

/// Dizi detayı — poster + özet + sezon seçici + bölüm listesi. PWA'daki "JSON indir-seç" workaround'unun
/// yerine native get_series_info (LibraryStore.loadSeries) kullanır.
struct SeriesDetailView: View {
    let ref: SeriesRef
    @EnvironmentObject private var library: LibraryStore
    @State private var series: Core.Series?
    @State private var season: Int = 0
    @State private var loading = true
    @State private var playing: Channel?

    private var currentSeason: Season? {
        series?.seasons.first { $0.number == season } ?? series?.seasons.first
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                } else if let s = series, !s.seasons.isEmpty {
                    seasonPicker(s)
                    episodeList
                } else {
                    Text("Bu dizide oynatılabilir bölüm bulunamadı.")
                        .font(.footnote).foregroundStyle(.sgDim).padding(.top, 20)
                }
            }
            .padding(SGMetric.gutter)
        }
        .background(Color.sgGround)
        .navigationTitle(ref.name).navigationBarTitleDisplayModeInlineIfAvailable()
        .task {
            series = await library.loadSeries(seriesId: ref.id, name: ref.name)
            season = series?.seasons.first?.number ?? 0
            loading = false
        }
        .fullScreenCover(item: $playing) { PlayerView(channel: $0) }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.sgElevated)
                AsyncImage(url: series?.cover ?? ref.cover) { $0.resizable().scaledToFill() } placeholder: {
                    Image(systemName: "play.tv").foregroundStyle(.sgMute)
                }
            }
            .frame(width: 110, height: 160).clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 6) {
                Text(ref.name).font(.system(size: 18, weight: .bold)).foregroundStyle(.sgText)
                if let g = series?.genre ?? ref.genre { Text(g).font(.caption).foregroundStyle(.sgAccent2) }
                if let plot = series?.plot { Text(plot).font(.caption).foregroundStyle(.sgDim).lineLimit(6) }
            }
        }
    }

    private func seasonPicker(_ s: Core.Series) -> some View {
        Menu {
            ForEach(s.seasons, id: \.number) { se in
                Button("Sezon \(se.number)") { season = se.number }
            }
        } label: {
            HStack {
                Text("Sezon \(season)").font(.system(size: 14, weight: .semibold))
                Image(systemName: "chevron.down").font(.caption)
            }
            .foregroundStyle(.sgText)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Color.sgSurface, in: Capsule())
        }
    }

    private var episodeList: some View {
        VStack(spacing: 8) {
            ForEach(currentSeason?.episodes ?? []) { ep in
                Button { playing = channel(for: ep) } label: { EpisodeRow(ep: ep) }
                    .buttonStyle(.plain)
            }
        }
    }

    private func channel(for ep: Episode) -> Channel {
        let label = "\(ref.name) S\(ep.season)B\(ep.episodeNum)" + (ep.title.isEmpty ? "" : " · \(ep.title)")
        return Channel(id: "ep_\(ep.id)", name: label, logo: ep.thumb,
                       group: "Dizi", url: ep.url, kind: .series)
    }
}

struct EpisodeRow: View {
    let ep: Episode
    var body: some View {
        HStack(spacing: 12) {
            Text("S\(String(format: "%02d", ep.season))B\(String(format: "%02d", ep.episodeNum))")
                .font(.system(size: 11, weight: .heavy)).monospacedDigit()
                .foregroundStyle(.sgAccent2).frame(width: 58, alignment: .leading)
            ZStack {
                RoundedRectangle(cornerRadius: 7).fill(Color.sgElevated)
                AsyncImage(url: ep.thumb) { $0.resizable().scaledToFill() } placeholder: { EmptyView() }
            }
            .frame(width: 64, height: 38).clipShape(RoundedRectangle(cornerRadius: 7))
            Text(ep.title).font(.system(size: 13)).foregroundStyle(.sgText).lineLimit(1)
            Spacer()
            Image(systemName: "play.circle.fill").foregroundStyle(.sgAccent)
        }
        .padding(.vertical, 4)
    }
}

// tvOS'ta olmayan inline title modifier'ını güvenli uygula.
private extension View {
    @ViewBuilder func navigationBarTitleDisplayModeInlineIfAvailable() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}
