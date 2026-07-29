import Foundation
import Security
import Core

/// Provider kimlik bilgilerini Keychain'de şifreli saklar. Spec §6 — PWA'daki düz localStorage
/// (index.html:1508) yerine. Cihazda kalır, sunucuya gitmez.
enum KeychainStore {
    private static let service = "app.cheesino.credentials"
    private static let legacyAccount = "provider"
    private static func account(_ id: String) -> String { "provider_\(id)" }

    // MARK: - Playlist id bazlı (çoklu kaynak)
    static func save(_ creds: ProviderCredentials, id: String) { write(creds, account: account(id)) }
    static func load(id: String) -> ProviderCredentials? { read(account: account(id)) }
    static func clear(id: String) { delete(account: account(id)) }

    // MARK: - Eski tek-kaynak (migrasyon + geriye uyumluluk)
    static func save(_ creds: ProviderCredentials) { write(creds, account: legacyAccount) }
    static func load() -> ProviderCredentials? { read(account: legacyAccount) }
    static func clear() { delete(account: legacyAccount) }

    // MARK: - Ortak
    private static func write(_ creds: ProviderCredentials, account: String) {
        guard let data = try? JSONEncoder().encode(creds) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attrs = query
        attrs[kSecValueData as String] = data
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attrs as CFDictionary, nil)
    }

    private static func read(account: String) -> ProviderCredentials? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return nil }
        return try? JSONDecoder().decode(ProviderCredentials.self, from: data)
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
