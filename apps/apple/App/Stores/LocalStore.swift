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
    }
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

/// İzleme ilerlemesi (resume / devam et). Spec §6.
struct Progress: Codable, Hashable {
    var positionSec: Double
    var durationSec: Double
    var updatedAt: Date
    /// %95+ izlendiyse baştan başlat.
    var resumePosition: Double { durationSec > 0 && positionSec / durationSec > 0.95 ? 0 : positionSec }
    var fraction: Double { durationSec > 0 ? min(1, positionSec / durationSec) : 0 }
}
