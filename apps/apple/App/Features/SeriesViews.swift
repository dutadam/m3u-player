import SwiftUI
import Core
import Design

/// Xtream dizi referansı (liste öğesi). Bölümler detayda get_series_info ile lazy çekilir.
struct SeriesRef: Identifiable, Hashable, Codable {
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
                        .buttonStyle(PressableStyle())
                }
            }
            .padding(SGMetric.gutter)
        }
        .background(Color.sgGround)
        .navigationTitle("Diziler")
        .navigationDestination(for: SeriesRef.self) { SeriesDetailView(ref: $0) }
    }
}

/// Bir kategorideki tüm diziler — poster grid ("Tümü ›" hedefi).
struct SeriesGridView: View {
    let category: String
    let series: [SeriesRef]
    private let cols = [GridItem(.adaptive(minimum: 112), spacing: 12)]
    var body: some View {
        ScrollView {
            LazyVGrid(columns: cols, spacing: 16) {
                ForEach(series) { s in
                    NavigationLink(value: s) { SeriesPoster(ref: s) }.buttonStyle(PressableStyle())
                }
            }
            .padding(SGMetric.gutter)
        }
        .background(Color.sgGround)
        .navigationTitle(category)
    }
}

struct SeriesPoster: View {
    @EnvironmentObject private var library: LibraryStore
    let ref: SeriesRef
    var width: CGFloat? = nil
    var body: some View {
        PosterCard(title: ref.name, poster: ref.cover,
                   continueBadge: library.seriesResume(for: String(ref.id)) != nil,
                   width: width)
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
                    BrandLoader(size: 64, caption: "Bölümler yükleniyor…")
                        .frame(maxWidth: .infinity).padding(.top, 40)
                } else if let s = series, !s.seasons.isEmpty {
                    continueBar
                    seasonPicker(s)
                    episodeList
                } else {
                    Text("Bu dizide oynatılabilir bölüm bulunamadı.")
                        .font(.footnote).foregroundStyle(Color.sgDim).padding(.top, 20)
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
        .fullScreenCover(item: $playing) { ch in
            let q = (currentSeason?.episodes ?? []).map { channel(for: $0) }
            PlayerView(channel: ch, queue: q, queueIndex: q.firstIndex { $0.id == ch.id } ?? 0)
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.sgElevated)
                AsyncImage(url: series?.cover ?? ref.cover) { $0.resizable().scaledToFill() } placeholder: {
                    Image(systemName: "play.tv").foregroundStyle(Color.sgMute)
                }
            }
            .frame(width: 110, height: 160).clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 6) {
                Text(ref.name).font(.system(size: 18, weight: .bold)).foregroundStyle(Color.sgText)
                if let g = series?.genre ?? ref.genre { Text(g).font(.caption).foregroundStyle(Color.sgAccent2) }
                if let plot = series?.plot { Text(plot).font(.caption).foregroundStyle(Color.sgDim).lineLimit(6) }
            }
        }
    }

    private func seasonPicker(_ s: Core.Series) -> some View {
        // Yatay sezon çipleri — iOS + tvOS uyumlu (Menu tvOS 17+ gerektirir).
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(s.seasons, id: \.number) { se in
                    let on = se.number == season
                    Button { season = se.number } label: {
                        Text("Sezon \(se.number)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(on ? Color.white : Color.sgDim)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(on ? Color.sgAccent : Color.sgSurface, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var episodeList: some View {
        VStack(spacing: 8) {
            ForEach(currentSeason?.episodes ?? []) { ep in
                Button { play(ep) } label: {
                    EpisodeRow(ep: ep,
                               watched: library.isWatched(ep.url.absoluteString),
                               progress: library.watchFraction(for: ep.url.absoluteString))
                }.buttonStyle(PressableStyle())
            }
        }
    }

    // MARK: - Devam et
    private var resumeInfo: SeriesResume? { library.seriesResume(for: String(ref.id)) }
    private func episode(forURL url: String) -> Episode? {
        series?.seasons.flatMap { $0.episodes }.first { $0.url.absoluteString == url }
    }

    @ViewBuilder private var continueBar: some View {
        if let r = resumeInfo, let ep = episode(forURL: r.episodeURL) {
            let f = library.watchFraction(for: r.episodeURL)
            Button { season = ep.season; play(ep) } label: {
                HStack(spacing: 12) {
                    Image(systemName: "play.circle.fill").font(.system(size: 30)).foregroundStyle(.white)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Kaldığın yerden devam et").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        Text("S\(ep.season)B\(ep.episodeNum)" + (ep.title.isEmpty ? "" : " · \(ep.title)"))
                            .font(.caption).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
                        if f > 0.02 {
                            ProgressView(value: f).tint(.white)
                                .background(.white.opacity(0.25)).frame(height: 3)
                        }
                    }
                    Spacer()
                }
                .padding(14)
                .background(LinearGradient(colors: [Color.sgAccent, Color.sgAccent2],
                                           startPoint: .leading, endPoint: .trailing),
                            in: RoundedRectangle(cornerRadius: 14))
            }.buttonStyle(PressableStyle())
        }
    }

    private func play(_ ep: Episode) {
        library.markSeries(SeriesResume(seriesId: String(ref.id), season: ep.season, episodeNum: ep.episodeNum,
                                        episodeURL: ep.url.absoluteString, episodeTitle: ep.title, updatedAt: .now))
        playing = channel(for: ep)
    }

    private func channel(for ep: Episode) -> Channel {
        let label = "\(ref.name) S\(ep.season)B\(ep.episodeNum)" + (ep.title.isEmpty ? "" : " · \(ep.title)")
        return Channel(id: "ep_\(ep.id)", name: label, logo: ep.thumb,
                       group: "Dizi", url: ep.url, kind: .series)
    }
}

struct EpisodeRow: View {
    let ep: Episode
    var watched: Bool = false
    var progress: Double = 0
    var body: some View {
        HStack(spacing: 12) {
            Text("S\(String(format: "%02d", ep.season))B\(String(format: "%02d", ep.episodeNum))")
                .font(.system(size: 11, weight: .heavy)).monospacedDigit()
                .foregroundStyle(Color.sgAccent2).frame(width: 58, alignment: .leading)
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 7).fill(Color.sgElevated)
                AsyncImage(url: ep.thumb) { $0.resizable().scaledToFill() } placeholder: { EmptyView() }
                if progress > 0.02 && !watched {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(.black.opacity(0.5))
                            Rectangle().fill(Color.sgAccent).frame(width: g.size.width * progress)
                        }
                    }.frame(height: 3)
                }
            }
            .frame(width: 64, height: 38).clipShape(RoundedRectangle(cornerRadius: 7))
            Text(ep.title).font(.system(size: 13))
                .foregroundStyle(watched ? Color.sgMute : Color.sgText).lineLimit(1)
            Spacer()
            Image(systemName: watched ? "checkmark.circle.fill" : "play.circle.fill")
                .foregroundStyle(watched ? Color.sgMute : Color.sgAccent)
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
