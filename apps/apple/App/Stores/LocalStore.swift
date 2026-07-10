import Foundation

/// Basit Codable kalıcılık (favoriler, son izlenenler, ilerleme, playlist meta).
/// Kimlik bilgisi HARİÇ her şey burada; kimlik bilgisi Keychain'de (bkz. KeychainStore).
enum LocalStore {
    private static let defaults = UserDefaults.standard

    static func save<T: Encodable>(_ value: T, key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }
    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    enum Key {
        static let favorites = "cheesino.favorites"     // Set<String> (channel url)
        static let recents = "cheesino.recents"         // [RecentItem]
        static let progress = "cheesino.progress"       // [String: Progress]
        static let lastSource = "cheesino.lastSource"   // PlaylistSource
        static let seriesResume = "cheesino.seriesResume" // [String: SeriesResume] (seriesId → son bölüm)
        static let reminders = "cheesino.reminders"     // [String: Reminder]
        static let userAgent = "cheesino.userAgent"     // String (özel User-Agent)
        static let playlists = "cheesino.playlists"     // [PlaylistMeta]
        static let activePlaylist = "cheesino.activePlaylist" // String (aktif playlist id)
    }
}

/// Kayıtlı kaynak (playlist) üst verisi. Şifre HARİÇ — o Keychain'de (id ile).
struct PlaylistMeta: Codable, Identifiable, Hashable {
    enum Kind: String, Codable { case xtream, m3u }
    var id: String                 // UUID
    var name: String
    var kind: Kind
    var m3uURL: String?            // .m3u için
    var server: String?           // .xtream için (görüntü + yeniden yükleme)
    var username: String?         // .xtream için (görüntü)
    var createdAt: Date

    var subtitle: String {
        switch kind {
        case .xtream: return [username, URL(string: server ?? "")?.host].compactMap { $0 }.joined(separator: " · ")
        case .m3u: return URL(string: m3uURL ?? "")?.host ?? "M3U"
        }
    }
}

/// Program hatırlatıcısı — planlanmış yerel bildirim.
struct Reminder: Codable, Hashable, Identifiable {
    var id: String            // kanalId_programStartTs
    var channelName: String
    var programTitle: String
    var fireDate: Date        // bildirim zamanı (program başlangıcından biraz önce)
    var start: Date           // program başlangıcı
}

/// Uygulama ayarları (basit tek-değer erişimi). Kimlik bilgisi HARİÇ.
enum AppSettings {
    static var userAgent: String {
        get { LocalStore.load(String.self, key: LocalStore.Key.userAgent) ?? "" }
        set { LocalStore.save(newValue, key: LocalStore.Key.userAgent) }
    }
    /// Özel UA varsa HTTP başlığı sözlüğü, yoksa nil.
    static var uaHeaders: [String: String]? {
        let ua = userAgent.trimmingCharacters(in: .whitespaces)
        return ua.isEmpty ? nil : ["User-Agent": ua]
    }
    /// Açılışta içeriği otomatik yenile (varsayılan açık).
    static var autoRefresh: Bool {
        get { LocalStore.load(Bool.self, key: "cheesino.autoRefresh") ?? true }
        set { LocalStore.save(newValue, key: "cheesino.autoRefresh") }
    }
}

/// İçerik önbelleği — kanal/dizi anlık görüntüsü (dosya tabanlı; UserDefaults için fazla büyük).
/// Açılışta anında göster, arka planda yenile.
enum ContentCache {
    struct Snapshot: Codable { var channels: [Channel]; var series: [SeriesRef]; var savedAt: Date }

    private static func fileURL(_ id: String) -> URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("cheesino-content-\(id).json")
    }

    /// Arka planda kaydet (binlerce kanalın encode'u ana thread'i kilitlemesin).
    static func save(id: String, channels: [Channel], series: [SeriesRef]) {
        let snap = Snapshot(channels: channels, series: series, savedAt: Date())
        let url = fileURL(id)
        Task.detached(priority: .utility) {
            if let data = try? JSONEncoder().encode(snap) { try? data.write(to: url, options: .atomic) }
        }
    }

    static func load(id: String) -> Snapshot? {
        guard let data = try? Data(contentsOf: fileURL(id)) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }

    static func clear(id: String) { try? FileManager.default.removeItem(at: fileURL(id)) }

    /// Eski tek-kaynak cache dosyasını yeni playlist id'sine taşır (migrasyon).
    static func migrateLegacy(to id: String) {
        let legacy = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("cheesino-content.json")
        guard FileManager.default.fileExists(atPath: legacy.path) else { return }
        try? FileManager.default.moveItem(at: legacy, to: fileURL(id))
    }
}

/// Dizi "kaldığın yerden devam" — dizi başına son izlenen bölüm.
struct SeriesResume: Codable, Hashable {
    var seriesId: String
    var season: Int
    var episodeNum: Int
    var episodeURL: String       // per-url ilerleme anahtarı (WatchProgress ile eşleşir)
    var episodeTitle: String
    var updatedAt: Date
}

/// Son izlenen öğe (hafif — kanal referansı).
struct RecentItem: Codable, Hashable, Identifiable {
    var id: String { url }
    var name: String
    var group: String
    var logo: String?
    var url: String
    var watchedAt: Date
}

/// İzleme ilerlemesi (resume / devam et). Spec §6. (Foundation.Progress ile çakışmaması için WatchProgress.)
struct WatchProgress: Codable, Hashable {
    var positionSec: Double
    var durationSec: Double
    var updatedAt: Date
    /// %95+ izlendiyse baştan başlat.
    var resumePosition: Double { durationSec > 0 && positionSec / durationSec > 0.95 ? 0 : positionSec }
    var fraction: Double { durationSec > 0 ? min(1, positionSec / durationSec) : 0 }
}
