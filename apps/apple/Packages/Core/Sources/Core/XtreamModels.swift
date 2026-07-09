import Foundation

// Xtream `player_api.php` yanıt modelleri. Spec: docs/spec/xtream-m3u-epg.md §3.
// NOT: Xtream sunucuları tipleri tutarsız döndürür (Int alanlar bazen String). Bu yüzden
// id/sayı alanlarında `LenientInt` kullanılır.

/// Int değeri Int veya String olarak decode edebilen yardımcı.
public struct LenientInt: Codable, Hashable, Sendable, ExpressibleByIntegerLiteral {
    public let value: Int
    public init(_ v: Int) { value = v }
    public init(integerLiteral v: Int) { value = v }
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let i = try? c.decode(Int.self) { value = i }
        else if let s = try? c.decode(String.self), let i = Int(s) { value = i }
        else { value = 0 }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer(); try c.encode(value)
    }
}

public struct XtreamCategory: Codable, Hashable, Sendable {
    public let categoryId: String
    public let categoryName: String
    enum CodingKeys: String, CodingKey {
        case categoryId = "category_id"
        case categoryName = "category_name"
    }
}

public struct XtreamLiveStream: Codable, Hashable, Sendable {
    public let name: String
    public let streamId: LenientInt
    public let streamIcon: String?
    public let epgChannelId: String?
    public let categoryId: String?
    public let tvArchive: LenientInt?           // 1 → catchup destekli
    enum CodingKeys: String, CodingKey {
        case name
        case streamId = "stream_id"
        case streamIcon = "stream_icon"
        case epgChannelId = "epg_channel_id"
        case categoryId = "category_id"
        case tvArchive = "tv_archive"
    }
    public var hasCatchup: Bool { (tvArchive?.value ?? 0) > 0 }
}

public struct XtreamVodStream: Codable, Hashable, Sendable {
    public let name: String
    public let streamId: LenientInt
    public let streamIcon: String?
    public let categoryId: String?
    public let containerExtension: String?
    enum CodingKeys: String, CodingKey {
        case name
        case streamId = "stream_id"
        case streamIcon = "stream_icon"
        case categoryId = "category_id"
        case containerExtension = "container_extension"
    }
}

public struct XtreamSeriesItem: Codable, Hashable, Sendable {
    public let name: String
    public let seriesId: LenientInt
    public let cover: String?
    public let plot: String?
    public let genre: String?
    public let categoryId: String?
    enum CodingKeys: String, CodingKey {
        case name, cover, plot, genre
        case seriesId = "series_id"
        case categoryId = "category_id"
    }
}

/// get_vod_info yanıtı — film detay ekranı için (poster/backdrop/özet/oyuncu/TMDB).
public struct XtreamVodInfo: Codable, Sendable {
    public struct Info: Codable, Sendable {
        public let movieImage: String?
        public let backdropPath: [String]?
        public let tmdbId: String?
        public let genre: String?
        public let plot: String?
        public let cast: String?
        public let director: String?
        public let releaseDate: String?
        public let rating: String?
        public let duration: String?
        public let youtubeTrailer: String?
        enum CodingKeys: String, CodingKey {
            case movieImage = "movie_image"
            case backdropPath = "backdrop_path"
            case tmdbId = "tmdb_id"
            case genre, plot, cast, director, rating, duration
            case releaseDate = "releasedate"
            case youtubeTrailer = "youtube_trailer"
        }
        // Xtream `rating`/`tmdb_id` bazen sayı döndürür — esnek decode.
        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            movieImage = try? c.decodeIfPresent(String.self, forKey: .movieImage)
            backdropPath = try? c.decodeIfPresent([String].self, forKey: .backdropPath)
            tmdbId = Self.lenientString(c, .tmdbId)
            genre = try? c.decodeIfPresent(String.self, forKey: .genre)
            plot = try? c.decodeIfPresent(String.self, forKey: .plot)
            cast = try? c.decodeIfPresent(String.self, forKey: .cast)
            director = try? c.decodeIfPresent(String.self, forKey: .director)
            releaseDate = Self.lenientString(c, .releaseDate)
            rating = Self.lenientString(c, .rating)
            duration = try? c.decodeIfPresent(String.self, forKey: .duration)
            youtubeTrailer = try? c.decodeIfPresent(String.self, forKey: .youtubeTrailer)
        }
        private static func lenientString(_ c: KeyedDecodingContainer<CodingKeys>, _ k: CodingKeys) -> String? {
            if let s = try? c.decodeIfPresent(String.self, forKey: k) { return s }
            if let d = try? c.decodeIfPresent(Double.self, forKey: k) { return String(d) }
            if let i = try? c.decodeIfPresent(Int.self, forKey: k) { return String(i) }
            return nil
        }
    }
    public let info: Info?
    enum CodingKeys: String, CodingKey { case info }
}

/// get_series_info yanıtı — PWA'daki manuel JSON-indir-seç akışının (index.html:1535) yerine geçer.
public struct XtreamSeriesInfo: Codable, Sendable {
    public struct Info: Codable, Sendable {
        public let name: String?
        public let cover: String?
        public let plot: String?
        public let genre: String?
    }
    public struct EpisodeRaw: Codable, Sendable {
        public let id: String
        public let episodeNum: LenientInt
        public let title: String?
        public let containerExtension: String?
        public let season: LenientInt?
        public let info: EpInfo?
        public struct EpInfo: Codable, Sendable {
            public let movieImage: String?
            public let duration: String?
            enum CodingKeys: String, CodingKey { case movieImage = "movie_image", duration }
        }
        enum CodingKeys: String, CodingKey {
            case id, title, info, season
            case episodeNum = "episode_num"
            case containerExtension = "container_extension"
        }
    }
    public let info: Info?
    public let episodes: [String: [EpisodeRaw]]   // sezon no → bölümler
    enum CodingKeys: String, CodingKey { case info, episodes }
}
