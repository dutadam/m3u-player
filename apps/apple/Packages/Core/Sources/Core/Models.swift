import Foundation

// MARK: - Temel modeller (spec: docs/spec/provider-playlist-epg.md §1)

public enum MediaKind: String, Codable, Sendable {
    case live, vod, series
}

public enum Quality: String, Codable, Sendable {
    case uhd4k = "4K", fhd = "FHD", hd = "HD", sd = "SD"

    /// Kanal adından kalite tespiti. PWA referansı: `dq()` (index.html:717).
    public static func detect(from name: String) -> Quality? {
        let u = name.uppercased()
        if u.range(of: #"\b(4K|UHD)\b"#, options: .regularExpression) != nil { return .uhd4k }
        if u.contains("FULLHD") || u.range(of: #"\bFHD\b"#, options: .regularExpression) != nil { return .fhd }
        if u.range(of: #"\bHD\b"#, options: .regularExpression) != nil { return .hd }
        if u.range(of: #"\bSD\b"#, options: .regularExpression) != nil { return .sd }
        return nil
    }
}

public struct Channel: Identifiable, Hashable, Codable, Sendable {
    public var id: String            // stabil kimlik (url veya provider stream_id)
    public var name: String
    public var logo: URL?
    public var group: String
    public var url: URL
    public var tvgId: String?        // EPG eşleme anahtarı
    public var kind: MediaKind
    public var quality: Quality?
    public var rating: Double?       // VOD puanı (IMDb/TMDB, akıllı kategoriler için)
    public var added: Date?          // eklenme tarihi (Son Eklenenler için)
    public var supportsCatchup: Bool // canlıda geçmiş program (tv_archive/timeshift) var mı

    public init(id: String, name: String, logo: URL? = nil, group: String,
                url: URL, tvgId: String? = nil, kind: MediaKind = .live, quality: Quality? = nil,
                rating: Double? = nil, added: Date? = nil, supportsCatchup: Bool = false) {
        self.id = id; self.name = name; self.logo = logo; self.group = group
        self.url = url; self.tvgId = tvgId; self.kind = kind
        self.quality = quality ?? Quality.detect(from: name)
        self.rating = rating; self.added = added; self.supportsCatchup = supportsCatchup
    }
}

public struct ProviderCredentials: Codable, Hashable, Sendable {
    public var server: URL           // normalize edilmiş (bkz. normalize)
    public var username: String
    public var password: String

    public init(server: URL, username: String, password: String) {
        self.server = server; self.username = username; self.password = password
    }

    /// Sunucu normalizasyonu. PWA referansı: `_xtNorm()` (index.html:1491).
    /// Sondaki `/` temizlenir; şema yoksa `http://` eklenir.
    public static func normalize(_ raw: String) -> URL? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if !s.lowercased().hasPrefix("http") { s = "http://" + s }
        return URL(string: s)
    }
}

public enum PlaylistSource: Hashable, Codable, Sendable {
    case m3uURL(URL)
    case m3uFile(name: String)
    case provider(ProviderCredentials)
}

public struct Playlist: Identifiable, Hashable, Codable, Sendable {
    public var id: UUID
    public var name: String
    public var source: PlaylistSource
    public var channelCount: Int
    public var updatedAt: Date

    public init(id: UUID = UUID(), name: String, source: PlaylistSource,
                channelCount: Int = 0, updatedAt: Date = .now) {
        self.id = id; self.name = name; self.source = source
        self.channelCount = channelCount; self.updatedAt = updatedAt
    }
}

// MARK: - Film (VOD) detayı

/// VOD detay ekranı için normalize model (get_vod_info'dan). TMDB backdrop/özet/oyuncu içerir.
public struct MovieDetail: Hashable, Codable, Sendable {
    public var plot: String?
    public var cast: String?
    public var director: String?
    public var genre: String?
    public var releaseDate: String?
    public var rating: String?
    public var duration: String?
    public var cover: URL?
    public var backdrop: URL?
    public var tmdbId: String?

    public init(plot: String? = nil, cast: String? = nil, director: String? = nil,
                genre: String? = nil, releaseDate: String? = nil, rating: String? = nil,
                duration: String? = nil, cover: URL? = nil, backdrop: URL? = nil, tmdbId: String? = nil) {
        self.plot = plot; self.cast = cast; self.director = director; self.genre = genre
        self.releaseDate = releaseDate; self.rating = rating; self.duration = duration
        self.cover = cover; self.backdrop = backdrop; self.tmdbId = tmdbId
    }
}

// MARK: - EPG

public struct EpgEntry: Hashable, Codable, Sendable {
    public var channelId: String     // XMLTV channel id ↔ Channel.tvgId
    public var start: Date
    public var stop: Date
    public var title: String
    public var desc: String?

    public init(channelId: String, start: Date, stop: Date, title: String, desc: String? = nil) {
        self.channelId = channelId; self.start = start; self.stop = stop
        self.title = title; self.desc = desc
    }

    public var isLiveNow: Bool { let n = Date(); return start <= n && n < stop }
}

// MARK: - Dizi

public struct Episode: Identifiable, Hashable, Codable, Sendable {
    public var id: String
    public var seriesId: String
    public var season: Int
    public var episodeNum: Int
    public var title: String
    public var ext: String
    public var thumb: URL?
    public var url: URL
}

public struct Season: Hashable, Codable, Sendable {
    public var number: Int
    public var episodes: [Episode]
}

public struct Series: Identifiable, Hashable, Codable, Sendable {
    public var id: String            // series_id
    public var name: String
    public var cover: URL?
    public var plot: String?
    public var genre: String?
    public var seasons: [Season]
}
