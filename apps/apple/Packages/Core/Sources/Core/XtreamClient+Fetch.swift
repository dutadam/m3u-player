import Foundation

// Xtream veri çekme + Channel/Series'e mapping. Spec: docs/spec/xtream-m3u-epg.md §3.
public extension XtreamClient {

    func fetchJSON<T: Decodable>(_ url: URL, as type: T.Type) async throws -> T {
        let data = try await fetchData(url)
        do { return try JSONDecoder().decode(T.self, from: data) }
        catch { throw XtreamError.decoding }
    }

    // MARK: - Ham listeler

    func liveCategories() async throws -> [XtreamCategory] {
        try await fetchJSON(apiURL(.liveCategories), as: [XtreamCategory].self)
    }
    func liveStreams(categoryId: String? = nil) async throws -> [XtreamLiveStream] {
        try await fetchJSON(apiURL(.liveStreams, params: catParam(categoryId)), as: [XtreamLiveStream].self)
    }
    func vodCategories() async throws -> [XtreamCategory] {
        try await fetchJSON(apiURL(.vodCategories), as: [XtreamCategory].self)
    }
    func vodStreams(categoryId: String? = nil) async throws -> [XtreamVodStream] {
        try await fetchJSON(apiURL(.vodStreams, params: catParam(categoryId)), as: [XtreamVodStream].self)
    }
    func seriesCategories() async throws -> [XtreamCategory] {
        try await fetchJSON(apiURL(.seriesCategories), as: [XtreamCategory].self)
    }
    func seriesList(categoryId: String? = nil) async throws -> [XtreamSeriesItem] {
        try await fetchJSON(apiURL(.series, params: catParam(categoryId)), as: [XtreamSeriesItem].self)
    }
    func seriesInfo(seriesId: Int) async throws -> XtreamSeriesInfo {
        try await fetchJSON(apiURL(.seriesInfo, params: ["series_id": "\(seriesId)"]), as: XtreamSeriesInfo.self)
    }
    func vodInfo(vodId: Int) async throws -> XtreamVodInfo {
        try await fetchJSON(apiURL(.vodInfo, params: ["vod_id": "\(vodId)"]), as: XtreamVodInfo.self)
    }

    private func catParam(_ id: String?) -> [String: String] {
        guard let id else { return [:] }
        return ["category_id": id]
    }

    // MARK: - Channel mapping

    /// Tüm canlı kanalları kategori adlarıyla birlikte döndürür.
    func allLiveChannels() async throws -> [Channel] {
        async let cats = liveCategories()
        async let streams = liveStreams()
        let catMap = Dictionary((try await cats).map { ($0.categoryId, $0.categoryName) },
                                uniquingKeysWith: { a, _ in a })
        return try await streams.map { s in
            Channel(
                id: "live_\(s.streamId.value)",
                name: s.name,
                logo: s.streamIcon.flatMap { URL(string: $0) },
                group: catMap[s.categoryId ?? ""] ?? "Canlı",
                url: liveURL(streamId: s.streamId.value),
                tvgId: s.epgChannelId.flatMap { $0.isEmpty ? nil : $0 },
                kind: .live
            )
        }
    }

    /// Tüm VOD (film) kanallarını döndürür.
    func allVODChannels() async throws -> [Channel] {
        async let cats = vodCategories()
        async let streams = vodStreams()
        let catMap = Dictionary((try await cats).map { ($0.categoryId, $0.categoryName) },
                                uniquingKeysWith: { a, _ in a })
        return try await streams.map { s in
            Channel(
                id: "vod_\(s.streamId.value)",
                name: s.name,
                logo: s.streamIcon.flatMap { URL(string: $0) },
                group: catMap[s.categoryId ?? ""] ?? "Filmler",
                url: vodURL(streamId: s.streamId.value, ext: s.containerExtension ?? "mp4"),
                kind: .vod,
                rating: s.ratingValue,
                added: s.addedDate
            )
        }
    }

    /// vod_id için film detayı (özet/oyuncu/backdrop/TMDB). Detay ekranı için.
    func movieDetail(vodId: Int) async throws -> MovieDetail {
        let raw = try await vodInfo(vodId: vodId)
        let info = raw.info
        let backdrop = info?.backdropPath?.first.flatMap { URL(string: $0) }
        return MovieDetail(
            plot: info?.plot.flatMap { $0.isEmpty ? nil : $0 },
            cast: info?.cast.flatMap { $0.isEmpty ? nil : $0 },
            director: info?.director.flatMap { $0.isEmpty ? nil : $0 },
            genre: info?.genre.flatMap { $0.isEmpty ? nil : $0 },
            releaseDate: info?.releaseDate.flatMap { $0.isEmpty ? nil : $0 },
            rating: info?.rating.flatMap { $0.isEmpty || $0 == "0" ? nil : $0 },
            duration: info?.duration.flatMap { $0.isEmpty ? nil : $0 },
            cover: info?.movieImage.flatMap { URL(string: $0) },
            backdrop: backdrop,
            tmdbId: info?.tmdbId.flatMap { $0.isEmpty ? nil : $0 }
        )
    }

    /// series_id için tam Series (sezon/bölüm + oynatma URL'leri). PWA workaround'unun native karşılığı.
    func fullSeries(seriesId: Int, name fallbackName: String = "") async throws -> Series {
        let raw = try await seriesInfo(seriesId: seriesId)
        let seasons: [Season] = raw.episodes
            .sorted { (Int($0.key) ?? 0) < (Int($1.key) ?? 0) }
            .map { (seasonKey, eps) in
                let number = Int(seasonKey) ?? (eps.first?.season?.value ?? 0)
                let episodes = eps
                    .sorted { $0.episodeNum.value < $1.episodeNum.value }
                    .map { ep in
                        let ext = ep.containerExtension ?? "mkv"
                        return Episode(
                            id: ep.id,
                            seriesId: "\(seriesId)",
                            season: number,
                            episodeNum: ep.episodeNum.value,
                            title: ep.title ?? "Bölüm \(ep.episodeNum.value)",
                            ext: ext,
                            thumb: ep.info?.movieImage.flatMap { URL(string: $0) },
                            url: seriesURL(episodeId: ep.id, ext: ext)
                        )
                    }
                return Season(number: number, episodes: episodes)
            }
        return Series(
            id: "\(seriesId)",
            name: raw.info?.name ?? fallbackName,
            cover: raw.info?.cover.flatMap { URL(string: $0) },
            plot: raw.info?.plot,
            genre: raw.info?.genre,
            seasons: seasons
        )
    }
}
