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
    @Published private(set) var progress: [String: WatchProgress] = [:]
    @Published private(set) var hiddenCategories: Set<String> = []
    @Published private(set) var seriesResume: [String: SeriesResume] = [:]

    private let session = URLSession.shared

    private var cloudObserver: NSObjectProtocol?

    init() {
        favorites = LocalStore.load(Set<String>.self, key: LocalStore.Key.favorites) ?? []
        recents = LocalStore.load([RecentItem].self, key: LocalStore.Key.recents) ?? []
        progress = LocalStore.load([String: WatchProgress].self, key: LocalStore.Key.progress) ?? [:]
        seriesResume = LocalStore.load([String: SeriesResume].self, key: LocalStore.Key.seriesResume) ?? [:]
        hiddenCategories = LocalStore.load(Set<String>.self, key: "cheesino.hiddenCats") ?? []
        // iCloud: başka cihazdan gelen durumu birleştir + değişiklikleri dinle.
        mergeFromCloud()
        cloudObserver = CloudStore.startObserving { [weak self] in
            Task { @MainActor in self?.mergeFromCloud() }
        }
    }

    deinit { if let o = cloudObserver { NotificationCenter.default.removeObserver(o) } }

    /// iCloud'daki durumu yerelle birleştirir (favori=birleşim, ilerleme/son izlenen=en yeni kazanır),
    /// sonucu hem yerele hem iCloud'a geri yazar (birleşim tüm cihazlara yayılsın).
    private func mergeFromCloud() {
        guard CloudStore.isAvailable else { return }
        if let cf = CloudStore.load(Set<String>.self, key: LocalStore.Key.favorites) {
            favorites.formUnion(cf)
        }
        if let cr = CloudStore.load([RecentItem].self, key: LocalStore.Key.recents) {
            var byURL = Dictionary(recents.map { ($0.url, $0) }, uniquingKeysWith: { a, b in a.watchedAt >= b.watchedAt ? a : b })
            for r in cr { if let e = byURL[r.url] { byURL[r.url] = e.watchedAt >= r.watchedAt ? e : r } else { byURL[r.url] = r } }
            recents = byURL.values.sorted { $0.watchedAt > $1.watchedAt }.prefix(30).map { $0 }
        }
        if let cp = CloudStore.load([String: WatchProgress].self, key: LocalStore.Key.progress) {
            for (url, p) in cp {
                if let e = progress[url] { if p.updatedAt > e.updatedAt { progress[url] = p } }
                else { progress[url] = p }
            }
        }
        // Birleşmiş sonucu geri yaz (yerel + bulut).
        LocalStore.save(favorites, key: LocalStore.Key.favorites)
        LocalStore.save(recents, key: LocalStore.Key.recents)
        LocalStore.save(progress, key: LocalStore.Key.progress)
        CloudStore.save(favorites, key: LocalStore.Key.favorites)
        CloudStore.save(recents, key: LocalStore.Key.recents)
        CloudStore.save(progress, key: LocalStore.Key.progress)
    }

    // MARK: - Türetilmiş gruplar (UI için)
    var live: [Channel] { channels.filter { $0.kind == .live } }
    var movies: [Channel] { channels.filter { $0.kind == .vod } }
    var seriesChannels: [Channel] { channels.filter { $0.kind == .series } }
    var favoriteChannels: [Channel] { channels.filter { favorites.contains($0.url.absoluteString) } }
    func channel(forURL url: String) -> Channel? { channels.first { $0.url.absoluteString == url } }
    var recentChannels: [Channel] { recents.compactMap { channel(forURL: $0.url) } }

    // MARK: - Kategori gizleme (Xtream'den gelenler dahil)
    var allCategories: [String] { Set(channels.map(\.group) + series.map(\.group)).sorted() }
    var visibleLive: [Channel] { live.filter { !hiddenCategories.contains($0.group) } }
    var visibleMovies: [Channel] { movies.filter { !hiddenCategories.contains($0.group) } }
    func isCategoryHidden(_ g: String) -> Bool { hiddenCategories.contains(g) }
    func toggleCategoryHidden(_ g: String) {
        if hiddenCategories.contains(g) { hiddenCategories.remove(g) } else { hiddenCategories.insert(g) }
        LocalStore.save(hiddenCategories, key: "cheesino.hiddenCats")
        CloudStore.save(hiddenCategories, key: "cheesino.hiddenCats")
    }

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
        CloudStore.save(favorites, key: LocalStore.Key.favorites)
    }

    func addRecent(_ ch: Channel) {
        let item = RecentItem(name: ch.name, group: ch.group,
                              logo: ch.logo?.absoluteString, url: ch.url.absoluteString, watchedAt: .now)
        recents.removeAll { $0.url == item.url }
        recents.insert(item, at: 0)
        if recents.count > 30 { recents.removeLast(recents.count - 30) }
        LocalStore.save(recents, key: LocalStore.Key.recents)
        CloudStore.save(recents, key: LocalStore.Key.recents)
    }

    func clearFavorites() {
        favorites.removeAll()
        LocalStore.save(favorites, key: LocalStore.Key.favorites)
        CloudStore.save(favorites, key: LocalStore.Key.favorites)
    }

    func clearRecents() {
        recents.removeAll()
        LocalStore.save(recents, key: LocalStore.Key.recents)
        CloudStore.save(recents, key: LocalStore.Key.recents)
    }

    /// Kullanıcı-tanımlı EPG (XMLTV) kaynağını yükler ve kalıcı yapar.
    func setManualEPG(_ urlString: String) async {
        guard let url = URL(string: urlString) else { return }
        LocalStore.save(urlString, key: "cheesino.epgURL")
        await loadEPG(from: url)
    }
    var manualEPGURL: String { LocalStore.load(String.self, key: "cheesino.epgURL") ?? "" }

    func saveProgress(url: String, position: Double, duration: Double) {
        guard duration > 30 else { return }
        progress[url] = WatchProgress(positionSec: position, durationSec: duration, updatedAt: .now)
        LocalStore.save(progress, key: LocalStore.Key.progress)
        CloudStore.save(progress, key: LocalStore.Key.progress)
    }
    func resumePosition(for url: String) -> Double { progress[url]?.resumePosition ?? 0 }

    /// İzleme ilerlemesi 0..1 (kart rozeti/çubuğu için).
    func watchFraction(for url: String) -> Double { progress[url]?.fraction ?? 0 }
    /// %92+ izlendiyse "izlendi".
    func isWatched(_ url: String) -> Bool { (progress[url]?.fraction ?? 0) >= 0.92 }
    /// Devam edilebilir (başlamış ama bitmemiş).
    func inProgress(_ url: String) -> Bool { let f = progress[url]?.fraction ?? 0; return f > 0.02 && f < 0.92 }

    // MARK: - Dizi devam etme (seriesId → son bölüm)
    func markSeries(_ r: SeriesResume) {
        seriesResume[r.seriesId] = r
        LocalStore.save(seriesResume, key: LocalStore.Key.seriesResume)
        CloudStore.save(seriesResume, key: LocalStore.Key.seriesResume)
    }
    func seriesResume(for id: String) -> SeriesResume? { seriesResume[id] }

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

    /// Geçmiş bir EPG programı için catchup/timeshift kanalı üretir (spec §3).
    /// Xtream stream_id kanal id'sinden ("live_123") ayıklanır; program geçmişte değilse nil.
    func catchupChannel(for ch: Channel, program p: EpgEntry) -> Channel? {
        guard let client = xtreamClient, p.start < Date(),
              let sid = Int(ch.id.replacingOccurrences(of: "live_", with: "")) else { return nil }
        let dur = max(1, Int(p.stop.timeIntervalSince(p.start) / 60))
        let url = client.timeshiftURL(streamId: sid, durationMin: dur, start: p.start)
        return Channel(id: "ts_\(sid)_\(Int(p.start.timeIntervalSince1970))",
                       name: "\(ch.name) · \(p.title)", logo: ch.logo,
                       group: "Catchup", url: url, kind: .vod)
    }

    /// Bir dizinin bölümlerini getirir (oynatıcı için). PWA'daki manuel akışın yerine geçer.
    func loadSeries(seriesId: Int, name: String) async -> Series? {
        guard let client = xtreamClient else { return nil }
        return try? await client.fullSeries(seriesId: seriesId, name: name)
    }

    /// Film detayı (get_vod_info). vodId, Channel.id "vod_<id>" içinden çıkarılır.
    func loadMovieDetail(vodId: Int) async -> MovieDetail? {
        guard let client = xtreamClient else { return nil }
        return try? await client.movieDetail(vodId: vodId)
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
