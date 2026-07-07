import Foundation
import Core

/// Kanal/playlist durumunu yöneten store. Core modülünü tüketir.
/// Faz 1 iskelet: yükleme + EPG indeksleme + türe göre bölütleme.
@MainActor
final class LibraryStore: ObservableObject {
    @Published var channels: [Channel] = []
    @Published var series: [SeriesRef] = []
    @Published var epg: EPGIndex?
    @Published var isLoading = false
    @Published var errorMessage: String?

    // Kullanıcı durumu (kalıcı)
    @Published private(set) var favorites: Set<String> = []
    @Published private(set) var recents: [RecentItem] = []
    @Published private(set) var progress: [String: Progress] = [:]

    private let session = URLSession.shared

    init() {
        favorites = LocalStore.load(Set<String>.self, key: LocalStore.Key.favorites) ?? []
        recents = LocalStore.load([RecentItem].self, key: LocalStore.Key.recents) ?? []
        progress = LocalStore.load([String: Progress].self, key: LocalStore.Key.progress) ?? [:]
    }

    // MARK: - Türetilmiş gruplar (UI için)
    var live: [Channel] { channels.filter { $0.kind == .live } }
    var movies: [Channel] { channels.filter { $0.kind == .vod } }
    var seriesChannels: [Channel] { channels.filter { $0.kind == .series } }
    var favoriteChannels: [Channel] { channels.filter { favorites.contains($0.url.absoluteString) } }
    func channel(forURL url: String) -> Channel? { channels.first { $0.url.absoluteString == url } }
    var recentChannels: [Channel] { recents.compactMap { channel(forURL: $0.url) } }

    var groups: [String: [Channel]] {
        Dictionary(grouping: channels, by: \.group)
    }

    /// Açılışta kayıtlı Xtream kimlik bilgisiyle otomatik geri yükleme.
    func restoreLastSession() async {
        if let creds = KeychainStore.load() { await loadXtream(creds) }
    }

    // MARK: - Favoriler / son izlenenler / ilerleme
    func isFavorite(_ ch: Channel) -> Bool { favorites.contains(ch.url.absoluteString) }

    func toggleFavorite(_ ch: Channel) {
        let key = ch.url.absoluteString
        if favorites.contains(key) { favorites.remove(key) } else { favorites.insert(key) }
        LocalStore.save(favorites, key: LocalStore.Key.favorites)
    }

    func addRecent(_ ch: Channel) {
        let item = RecentItem(name: ch.name, group: ch.group,
                              logo: ch.logo?.absoluteString, url: ch.url.absoluteString, watchedAt: .now)
        recents.removeAll { $0.url == item.url }
        recents.insert(item, at: 0)
        if recents.count > 30 { recents.removeLast(recents.count - 30) }
        LocalStore.save(recents, key: LocalStore.Key.recents)
    }

    func saveProgress(url: String, position: Double, duration: Double) {
        guard duration > 30 else { return }
        progress[url] = Progress(positionSec: position, durationSec: duration, updatedAt: .now)
        LocalStore.save(progress, key: LocalStore.Key.progress)
    }
    func resumePosition(for url: String) -> Double { progress[url]?.resumePosition ?? 0 }

    // MARK: - M3U (URL)
    func loadM3U(from url: URL) async {
        isLoading = true; errorMessage = nil
        defer { isLoading = false }
        do {
            let (data, resp) = try await session.data(from: url)
            guard let http = resp as? HTTPURLResponse, 200..<300 ~= http.statusCode,
                  let text = String(data: data, encoding: .utf8) else {
                errorMessage = "Playlist alınamadı — bağlantıyı kontrol edin."; return
            }
            let result = M3UParser.parse(text)
            guard !result.channels.isEmpty else {
                errorMessage = "M3U içinde kanal bulunamadı."; return
            }
            channels = result.channels
            if let epgURL = result.epgURL { await loadEPG(from: epgURL) }
        } catch {
            errorMessage = "Ağ hatası: \(error.localizedDescription)"
        }
    }

    // MARK: - M3U (dosya metni)
    func loadM3U(text: String) {
        let result = M3UParser.parse(text)
        guard !result.channels.isEmpty else { errorMessage = "M3U içinde kanal bulunamadı."; return }
        channels = result.channels
    }

    // MARK: - Xtream
    private(set) var xtreamClient: XtreamClient?

    func loadXtream(_ creds: XtreamCredentials) async {
        isLoading = true; errorMessage = nil
        defer { isLoading = false }
        let client = XtreamClient(creds: creds, session: session)
        xtreamClient = client
        do {
            let info = try await client.authenticate()
            guard info.isActive else { errorMessage = "Abonelik aktif değil."; return }

            // Canlı + VOD kanallarını paralel çek (dizi listesi lazy — açılınca get_series_info).
            async let live = client.allLiveChannels()
            async let vod = try? client.allVODChannels()   // bazı portallarda VOD yok → opsiyonel
            var all = try await live
            if let v = await vod { all += v }

            guard !all.isEmpty else { errorMessage = "Sunucuda kanal bulunamadı."; return }
            channels = all
            KeychainStore.save(creds)            // başarılı giriş → kimlik bilgisini şifreli sakla

            // Dizi listesi (bölümler lazy — detayda get_series_info ile çekilir).
            if let sList = try? await client.seriesList() {
                let cats = (try? await client.seriesCategories()) ?? []
                let catMap = Dictionary(cats.map { ($0.categoryId, $0.categoryName) }, uniquingKeysWith: { a, _ in a })
                series = sList.map {
                    SeriesRef(id: $0.seriesId.value, name: $0.name,
                              cover: $0.cover.flatMap { URL(string: $0) },
                              genre: $0.genre, group: catMap[$0.categoryId ?? ""] ?? "Diziler")
                }
            }
            await loadEPG(from: client.xmltvURL)
        } catch {
            errorMessage = "Xtream girişi başarısız — sunucu/kullanıcı/şifreyi kontrol edin."
        }
    }

    /// Kaydedilmiş kaynağı ve kimlik bilgisini temizle (çıkış).
    func signOut() {
        KeychainStore.clear()
        channels = []; epg = nil; xtreamClient = nil
    }

    /// Bir dizinin bölümlerini getirir (oynatıcı için). PWA'daki manuel akışın yerine geçer.
    func loadSeries(seriesId: Int, name: String) async -> Series? {
        guard let client = xtreamClient else { return nil }
        return try? await client.fullSeries(seriesId: seriesId, name: name)
    }

    // MARK: - EPG
    func loadEPG(from url: URL) async {
        do {
            let (data, _) = try await session.data(from: url)
            let entries = XMLTVParser.parse(data)
            if !entries.isEmpty { epg = EPGIndex(entries: entries) }
        } catch { /* EPG opsiyonel — sessiz geç */ }
    }
}
