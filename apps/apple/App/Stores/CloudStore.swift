import Foundation

/// iCloud key-value senkronizasyonu (favori/son izlenen/ilerleme). Spec §6.
/// Küçük kullanıcı durumu için CloudKit'e gerek yok — NSUbiquitousKeyValueStore otomatik senkronlar
/// (1MB / 1024 anahtar sınırı bizim veriye fazlasıyla yeter). Kanal kataloğu senkronlanmaz; her cihaz
/// kendi Xtream/M3U kaynağından çeker. Kimlik bilgisi buraya YAZILMAZ (o Keychain'de).
///
/// NOT: KVS'ye yalnız iCloud kullanılabilirken dokunulur. Aksi halde `.default`'a erişmek
/// "BUG IN CLIENT OF KVS: … without a store identifier" uyarısı üretir (entitlement yoksa).
enum CloudStore {
    /// Kullanıcı iCloud hesabına giriş yapmış mı? (KVS entitlement + hesap gerekir.)
    static var isAvailable: Bool { FileManager.default.ubiquityIdentityToken != nil }

    /// Store'a yalnız kullanılabilirken eriş — erken erişim uyarı loglar.
    private static var store: NSUbiquitousKeyValueStore? {
        isAvailable ? .default : nil
    }

    static func save<T: Encodable>(_ value: T, key: String) {
        guard let kv = store, let data = try? JSONEncoder().encode(value) else { return }
        kv.set(data, forKey: key)
        kv.synchronize()
    }

    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let kv = store, let data = kv.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    /// Dışarıdan (başka cihaz) değişiklik geldiğinde çağrılır. iCloud yoksa nil döner (no-op).
    static func startObserving(_ onChange: @escaping () -> Void) -> NSObjectProtocol? {
        guard let kv = store else { return nil }
        kv.synchronize()
        return NotificationCenter.default.addObserver(
            forName: NSUbiquitousKeyValueStore.didChangeExternallyNotification,
            object: kv, queue: .main) { _ in onChange() }
    }
}
