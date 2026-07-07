import Foundation

/// iCloud key-value senkronizasyonu (favori/son izlenen/ilerleme). Spec §6.
/// Küçük kullanıcı durumu için CloudKit'e gerek yok — NSUbiquitousKeyValueStore otomatik senkronlar
/// (1MB / 1024 anahtar sınırı bizim veriye fazlasıyla yeter). Kanal kataloğu senkronlanmaz; her cihaz
/// kendi Xtream/M3U kaynağından çeker. Kimlik bilgisi buraya YAZILMAZ (o Keychain'de).
enum CloudStore {
    private static let kv = NSUbiquitousKeyValueStore.default

    /// Kullanıcı bir iCloud hesabına giriş yapmış mı?
    static var isAvailable: Bool { FileManager.default.ubiquityIdentityToken != nil }

    static func save<T: Encodable>(_ value: T, key: String) {
        guard isAvailable, let data = try? JSONEncoder().encode(value) else { return }
        kv.set(data, forKey: key)
        kv.synchronize()
    }

    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = kv.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    /// Dışarıdan (başka cihaz) değişiklik geldiğinde çağrılır.
    static func startObserving(_ onChange: @escaping () -> Void) -> NSObjectProtocol {
        kv.synchronize()
        return NotificationCenter.default.addObserver(
            forName: NSUbiquitousKeyValueStore.didChangeExternallyNotification,
            object: kv, queue: .main) { _ in onChange() }
    }
}
