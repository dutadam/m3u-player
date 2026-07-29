import Foundation

/// Provider Codes `player_api.php` istemcisi. Spec: docs/spec/provider-playlist-epg.md §3.
/// Native URLSession — CORS/proxy YOK (PWA'daki `loadURL` proxy zinciri gereksiz).
/// PWA'daki "dizi bölümü için JSON indir-seç" workaround'unu (index.html:1522) tamamen değiştirir.
public struct ProviderClient: Sendable {
    public let creds: ProviderCredentials
    private let session: URLSession

    public init(creds: ProviderCredentials, session: URLSession = .shared) {
        self.creds = creds
        self.session = session
    }

    // MARK: - Endpoint URL üretimi

    public enum Action: String {
        case accountInfo = ""                       // boş action → user_info döner
        case liveCategories = "get_live_categories"
        case liveStreams = "get_live_streams"
        case vodCategories = "get_vod_categories"
        case vodStreams = "get_vod_streams"
        case vodInfo = "get_vod_info"
        case seriesCategories = "get_series_categories"
        case series = "get_series"
        case seriesInfo = "get_series_info"
        case shortEPG = "get_short_epg"
    }

    public func apiURL(_ action: Action, params: [String: String] = [:]) -> URL {
        var comps = URLComponents(url: creds.server.appendingPathComponent("player_api.php"),
                                  resolvingAgainstBaseURL: false)!
        var items = [
            URLQueryItem(name: "username", value: creds.username),
            URLQueryItem(name: "password", value: creds.password)
        ]
        if !action.rawValue.isEmpty { items.append(URLQueryItem(name: "action", value: action.rawValue)) }
        items.append(contentsOf: params.map { URLQueryItem(name: $0.key, value: $0.value) })
        comps.queryItems = items
        return comps.url!
    }

    /// XMLTV EPG kaynağı (toplu).
    public var xmltvURL: URL {
        var comps = URLComponents(url: creds.server.appendingPathComponent("xmltv.php"),
                                  resolvingAgainstBaseURL: false)!
        comps.queryItems = [
            URLQueryItem(name: "username", value: creds.username),
            URLQueryItem(name: "password", value: creds.password)
        ]
        return comps.url!
    }

    // MARK: - Stream URL şemaları (spec §3)

    public func liveURL(streamId: Int, ext: String = "m3u8") -> URL {
        creds.server
            .appendingPathComponent("live")
            .appendingPathComponent(creds.username)
            .appendingPathComponent(creds.password)
            .appendingPathComponent("\(streamId).\(ext)")
    }

    public func vodURL(streamId: Int, ext: String) -> URL {
        creds.server
            .appendingPathComponent("movie")
            .appendingPathComponent(creds.username)
            .appendingPathComponent(creds.password)
            .appendingPathComponent("\(streamId).\(ext)")
    }

    public func seriesURL(episodeId: String, ext: String) -> URL {
        creds.server
            .appendingPathComponent("series")
            .appendingPathComponent(creds.username)
            .appendingPathComponent(creds.password)
            .appendingPathComponent("\(episodeId).\(ext)")
    }

    /// Catchup / timeshift URL. Spec §3.
    /// {server}/timeshift/{user}/{pass}/{dur}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts
    public func timeshiftURL(streamId: Int, durationMin: Int, start: Date) -> URL {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd:HH-mm"
        fmt.timeZone = TimeZone.current
        return creds.server
            .appendingPathComponent("timeshift")
            .appendingPathComponent(creds.username)
            .appendingPathComponent(creds.password)
            .appendingPathComponent("\(durationMin)")
            .appendingPathComponent(fmt.string(from: start))
            .appendingPathComponent("\(streamId).ts")
    }

    // MARK: - Ağ çağrıları

    public enum ProviderError: Error { case badResponse, decoding }

    /// Ham JSON verisi getirir (timeout + hata kontrolü).
    public func fetchData(_ url: URL, timeout: TimeInterval = 20) async throws -> Data {
        var req = URLRequest(url: url)
        req.timeoutInterval = timeout
        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw ProviderError.badResponse
        }
        return data
    }

    /// Kimlik doğrulama + hesap bilgisi (abonelik durumu için).
    @discardableResult
    public func authenticate() async throws -> AccountInfo {
        let data = try await fetchData(apiURL(.accountInfo))
        do { return try JSONDecoder().decode(AccountInfo.self, from: data) }
        catch { throw ProviderError.decoding }
    }
}

// MARK: - Yanıt modelleri (kısmi — gerektikçe genişletilir)

public struct AccountInfo: Codable, Sendable {
    public struct UserInfo: Codable, Sendable {
        public let username: String?
        public let status: String?
        public let expDate: String?          // unix ts string
        public let isTrial: String?
        public let activeCons: String?
        public let maxConnections: String?
        enum CodingKeys: String, CodingKey {
            case username, status
            case expDate = "exp_date"
            case isTrial = "is_trial"
            case activeCons = "active_cons"
            case maxConnections = "max_connections"
        }
    }
    public let userInfo: UserInfo?
    enum CodingKeys: String, CodingKey { case userInfo = "user_info" }

    /// Aboneliğin bitiş tarihi (varsa).
    public var expiryDate: Date? {
        guard let ts = userInfo?.expDate, let secs = TimeInterval(ts) else { return nil }
        return Date(timeIntervalSince1970: secs)
    }
    public var isActive: Bool { userInfo?.status?.lowercased() == "active" }
}
