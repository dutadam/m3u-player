import Foundation
import Core
import UserNotifications

/// Kanal/playlist durumunu yöneten store. Core modülünü tüketir.
/// Faz 1 iskelet: yükleme + EPG indeksleme + türe göre bölütleme.
@MainActor
final class LibraryStore: ObservableObject {
    @Published var channels: [Channel] = []
    @Published var series: [SeriesRef] = []
    @Published var epg: EPGIndex?
    @Published var isLoading = false
    @Published var isRefreshing = false          // arka plan yenileme (bloklamaz)
    @Published private(set) var lastUpdated: Date?
    @Published var errorMessage: String?

    // Kullanıcı durumu (kalıcı)
    @Published private(set) var favorites: Set<String> = []
    @Published private(set) var recents: [RecentItem] = []
    @Published private(set) var progress: [String: WatchProgress] = [:]
    @Published private(set) var hiddenCategories: Set<String> = []
    @Published private(set) var seriesResume: [String: SeriesResume] = [:]
    @Published private(set) var reminders: [String: Reminder] = [:]
    @Published private(set) var likes: Set<String> = []
    @Published private(set) var dislikes: Set<String> = []

    // Çoklu kaynak (playlist)
    @Published private(set) var playlists: [PlaylistMeta] = []
    @Published private(set) var activePlaylistId: String?
    var activePlaylist: PlaylistMeta? { playlists.first { $0.id == activePlaylistId } }

    private var session = LibraryStore.makeSession()

    /// Özel User-Agent varsa onu ekleyen URLSession üretir (bazı IPTV panelleri UA ister).
    private static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.default
        if let h = AppSettings.uaHeaders { config.httpAdditionalHeaders = h }
        return URLSession(configuration: config)
    }

    private var cloudObserver: NSObjectProtocol?

    init() {
        favorites = LocalStore.load(Set<String>.self, key: LocalStore.Key.favorites) ?? []
        recents = LocalStore.load([RecentItem].self, key: LocalStore.Key.recents) ?? []
        progress = LocalStore.load([String: WatchProgress].self, key: LocalStore.Key.progress) ?? [:]
        seriesResume = LocalStore.load([String: SeriesResume].self, key: LocalStore.Key.seriesResume) ?? [:]
        reminders = LocalStore.load([String: Reminder].self, key: LocalStore.Key.reminders) ?? [:]
        likes = LocalStore.load(Set<String>.self, key: LocalStore.Key.likes) ?? []
        dislikes = LocalStore.load(Set<String>.self, key: LocalStore.Key.dislikes) ?? []
        hiddenCategories = LocalStore.load(Set<String>.self, key: "cheesino.hiddenCats") ?? []
        playlists = LocalStore.load([PlaylistMeta].self, key: LocalStore.Key.playlists) ?? []
        activePlaylistId = LocalStore.load(String.self, key: LocalStore.Key.activePlaylist)
        migrateLegacyIfNeeded()
        // Aktif kaynak varsa açılışta onboarding yerine yükleme ekranı göster (flaşı önle).
        if activePlaylist != nil { isLoading = true }
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
        if let cl = CloudStore.load(Set<String>.self, key: LocalStore.Key.likes) { likes.formUnion(cl) }
        if let cd = CloudStore.load(Set<String>.self, key: LocalStore.Key.dislikes) { dislikes.formUnion(cd) }
        likes.subtract(dislikes)   // beğenmeme önceliği
        LocalStore.save(likes, key: LocalStore.Key.likes)
        LocalStore.save(dislikes, key: LocalStore.Key.dislikes)
        CloudStore.save(likes, key: LocalStore.Key.likes)
        CloudStore.save(dislikes, key: LocalStore.Key.dislikes)
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

    // MARK: - Akıllı kategoriler (metadata'dan üretilen sistem kategorileri)
    /// Son Eklenenler — eklenme tarihine göre (metadata `added`).
    var recentlyAddedMovies: [Channel] {
        visibleMovies.filter { $0.added != nil }.sorted { ($0.added ?? .distantPast) > ($1.added ?? .distantPast) }
    }
    /// IMDb/Yüksek Puanlı — 7.5+ puan, puana göre.
    var topRatedMovies: [Channel] {
        visibleMovies.filter { ($0.rating ?? 0) >= 7.5 }.sorted { ($0.rating ?? 0) > ($1.rating ?? 0) }
    }
    /// Kült & Klasik — kategori adında "kült/klasik/classic" geçenler.
    var cultClassicMovies: [Channel] {
        visibleMovies.filter { c in
            let g = c.group.lowercased()
            return g.contains("kült") || g.contains("kult") || g.contains("klasik") || g.contains("classic")
        }
    }
    func isCategoryHidden(_ g: String) -> Bool { hiddenCategories.contains(g) }
    func toggleCategoryHidden(_ g: String) {
        if hiddenCategories.contains(g) { hiddenCategories.remove(g) } else { hiddenCategories.insert(g) }
        LocalStore.save(hiddenCategories, key: "cheesino.hiddenCats")
        CloudStore.save(hiddenCategories, key: "cheesino.hiddenCats")
    }

    var groups: [String: [Channel]] {
        Dictionary(grouping: channels, by: \.group)
    }

    /// Açılışta: aktif playlist'in cache'ini anında göster, sonra (ayar açıksa) arka planda yenile.
    /// Cache yoksa bloklayan tam yükleme yapılır.
    func restoreLastSession() async {
        guard let pl = activePlaylist else { isLoading = false; return }
        // Cache'i arka thread'de çöz (binlerce kanalın decode'u ana thread'i kilitlemesin).
        let snap = await Task.detached(priority: .userInitiated) { ContentCache.load(id: pl.id) }.value
        if let snap, !snap.channels.isEmpty {
            channels = snap.channels
            series = snap.series
            lastUpdated = snap.savedAt
            isLoading = false
            if AppSettings.autoRefresh { await reload(pl, background: true) }
        } else {
            await reload(pl, background: false)
        }
    }

    /// Manuel yenileme (pull-to-refresh / buton) — aktif playlist.
    func refresh() async {
        if let pl = activePlaylist { await reload(pl, background: true) }
    }

    /// Bir playlist'in kaynağından içeriği yükler (Xtream veya M3U).
    private func reload(_ pl: PlaylistMeta, background: Bool) async {
        switch pl.kind {
        case .xtream:
            if let creds = KeychainStore.load(id: pl.id) { await loadXtream(creds, id: pl.id, background: background) }
            else { errorMessage = "Kaynak kimlik bilgisi bulunamadı." }
        case .m3u:
            if let u = URL(string: pl.m3uURL ?? "") { await loadM3U(from: u, id: pl.id, background: background) }
        }
    }

    // MARK: - Playlist yönetimi
    private func persistPlaylists() {
        LocalStore.save(playlists, key: LocalStore.Key.playlists)
        if let a = activePlaylistId { LocalStore.save(a, key: LocalStore.Key.activePlaylist) }
    }
    private func setActive(_ id: String?) {
        activePlaylistId = id
        if let id { LocalStore.save(id, key: LocalStore.Key.activePlaylist) }
    }

    /// Yeni Xtream kaynağı ekle ve aktif yap.
    func addXtream(name: String, creds: XtreamCredentials) async {
        let id = UUID().uuidString
        let nm = name.trimmingCharacters(in: .whitespaces).isEmpty ? (creds.server.host ?? "Xtream") : name
        KeychainStore.save(creds, id: id)
        playlists.append(PlaylistMeta(id: id, name: nm, kind: .xtream, m3uURL: nil,
                                      server: creds.server.absoluteString, username: creds.username, createdAt: Date()))
        setActive(id); persistPlaylists()
        await loadXtream(creds, id: id, background: false)
    }

    /// Yeni M3U kaynağı ekle ve aktif yap.
    func addM3U(name: String, url: URL) async {
        let id = UUID().uuidString
        let nm = name.trimmingCharacters(in: .whitespaces).isEmpty ? (url.host ?? "M3U") : name
        playlists.append(PlaylistMeta(id: id, name: nm, kind: .m3u, m3uURL: url.absoluteString,
                                      server: nil, username: nil, createdAt: Date()))
        setActive(id); persistPlaylists()
        await loadM3U(from: url, id: id, background: false)
    }

    /// Başka bir kaydedilmiş kaynağa geç.
    func switchTo(_ id: String) async {
        guard id != activePlaylistId, playlists.contains(where: { $0.id == id }) else { return }
        setActive(id); persistPlaylists()
        channels = []; series = []; epg = nil; lastUpdated = nil; errorMessage = nil
        isLoading = true
        await restoreLastSession()
    }

    func renamePlaylist(_ id: String, name: String) {
        guard let i = playlists.firstIndex(where: { $0.id == id }) else { return }
        playlists[i].name = name
        persistPlaylists()
    }

    /// Bir kaynağı sil (aktifse sonrakine geç, yoksa onboarding).
    func removePlaylist(_ id: String) async {
        playlists.removeAll { $0.id == id }
        KeychainStore.clear(id: id)
        ContentCache.clear(id: id)
        LocalStore.save(playlists, key: LocalStore.Key.playlists)
        if activePlaylistId == id {
            if let next = playlists.first?.id { await switchTo(next) }
            else { setActive(nil); LocalStore.save("", key: LocalStore.Key.activePlaylist)
                   channels = []; series = []; epg = nil; lastUpdated = nil }
        }
    }

    /// Eski tek-kaynak (Keychain "xtream") kaydını playlist modeline taşı.
    private func migrateLegacyIfNeeded() {
        guard playlists.isEmpty, let creds = KeychainStore.load() else { return }
        let id = UUID().uuidString
        playlists = [PlaylistMeta(id: id, name: creds.server.host ?? "Kaynağım", kind: .xtream, m3uURL: nil,
                                  server: creds.server.absoluteString, username: creds.username, createdAt: Date())]
        KeychainStore.save(creds, id: id)
        ContentCache.migrateLegacy(to: id)
        setActive(id); persistPlaylists()
        KeychainStore.clear()   // eski tekil kaydı temizle
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

    // MARK: - Beğeni / öneri (izleme geçmişi + tür/kategori afinitesi)
    func isLiked(_ key: String) -> Bool { likes.contains(key) }
    func isDisliked(_ key: String) -> Bool { dislikes.contains(key) }
    func toggleLike(_ key: String) {
        if likes.contains(key) { likes.remove(key) } else { likes.insert(key); dislikes.remove(key) }
        persistPrefs()
    }
    func toggleDislike(_ key: String) {
        if dislikes.contains(key) { dislikes.remove(key) } else { dislikes.insert(key); likes.remove(key) }
        persistPrefs()
    }
    private func persistPrefs() {
        LocalStore.save(likes, key: LocalStore.Key.likes)
        LocalStore.save(dislikes, key: LocalStore.Key.dislikes)
        CloudStore.save(likes, key: LocalStore.Key.likes)
        CloudStore.save(dislikes, key: LocalStore.Key.dislikes)
    }

    /// Kategori/tür afinitesi — izleme geçmişi + beğeniler (öneri sinyali).
    func categoryAffinity() -> [String: Double] {
        var score: [String: Double] = [:]
        for r in recents { if let ch = channel(forURL: r.url) { score[ch.group, default: 0] += 1 } }
        for (url, p) in progress where p.fraction > 0.1 {
            if let ch = channel(forURL: url) { score[ch.group, default: 0] += min(1.5, p.fraction * 1.5) }
        }
        for key in likes { if let ch = channel(forURL: key) { score[ch.group, default: 0] += 3 } }
        for key in dislikes { if let ch = channel(forURL: key) { score[ch.group, default: 0] -= 3 } }
        for key in likes where key.hasPrefix("series_") {
            if let id = Int(key.replacingOccurrences(of: "series_", with: "")),
               let s = series.first(where: { $0.id == id }) { score[s.group, default: 0] += 3 }
        }
        return score
    }

    /// "Sana Özel" film önerileri — kategori afinitesine göre; izlenmiş/beğenilmeyen hariç.
    var recommendedMovies: [Channel] {
        let aff = categoryAffinity()
        guard aff.values.contains(where: { $0 > 0 }) else { return [] }
        return visibleMovies
            .filter { !isDisliked($0.url.absoluteString) && !isWatched($0.url.absoluteString) }
            .compactMap { ch -> (Channel, Double)? in let s = aff[ch.group] ?? 0; return s > 0 ? (ch, s) : nil }
            .sorted { $0.1 > $1.1 }
            .prefix(30).map { $0.0 }
    }

    // MARK: - Dizi devam etme (seriesId → son bölüm)
    func markSeries(_ r: SeriesResume) {
        seriesResume[r.seriesId] = r
        LocalStore.save(seriesResume, key: LocalStore.Key.seriesResume)
        CloudStore.save(seriesResume, key: LocalStore.Key.seriesResume)
    }
    func seriesResume(for id: String) -> SeriesResume? { seriesResume[id] }

    // MARK: - M3U (URL)
    func loadM3U(from url: URL, id: String, background: Bool = false) async {
        if background { isRefreshing = true } else { isLoading = true }
        errorMessage = nil
        defer { if background { isRefreshing = false } else { isLoading = false } }
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
            series = []
            lastUpdated = Date()
            ContentCache.save(id: id, channels: result.channels, series: [])
            if let epgURL = result.epgURL { await loadEPG(from: epgURL) }
        } catch {
            errorMessage = "Ağ hatası: \(error.localizedDescription)"
        }
    }

    // MARK: - M3U (dosya metni) — yenilenemez, önbellekten kalıcı
    func addM3UFile(name: String, text: String) {
        let result = M3UParser.parse(text)
        guard !result.channels.isEmpty else { errorMessage = "M3U içinde kanal bulunamadı."; return }
        let id = UUID().uuidString
        let nm = name.trimmingCharacters(in: .whitespaces).isEmpty ? "M3U Dosyası" : name
        playlists.append(PlaylistMeta(id: id, name: nm, kind: .m3u, m3uURL: nil, server: nil, username: nil, createdAt: Date()))
        setActive(id); persistPlaylists()
        channels = result.channels; series = []; lastUpdated = Date()
        ContentCache.save(id: id, channels: result.channels, series: [])
        if let epgURL = result.epgURL { Task { await loadEPG(from: epgURL) } }
    }

    // MARK: - Xtream
    private(set) var xtreamClient: XtreamClient?

    /// background=true → mevcut içerik ekranda kalır, yalnız isRefreshing yanar; başarıda değiştirilir.
    func loadXtream(_ creds: XtreamCredentials, id: String, background: Bool = false) async {
        if background { isRefreshing = true } else { isLoading = true }
        errorMessage = nil
        defer { if background { isRefreshing = false } else { isLoading = false } }
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

            // Dizi listesi (bölümler lazy — detayda get_series_info ile çekilir).
            var refs: [SeriesRef] = []
            if let sList = try? await client.seriesList() {
                let cats = (try? await client.seriesCategories()) ?? []
                let catMap = Dictionary(cats.map { ($0.categoryId, $0.categoryName) }, uniquingKeysWith: { a, _ in a })
                refs = sList.map {
                    SeriesRef(id: $0.seriesId.value, name: $0.name,
                              cover: $0.cover.flatMap { URL(string: $0) },
                              genre: $0.genre, group: catMap[$0.categoryId ?? ""] ?? "Diziler")
                }
                series = refs
            }
            lastUpdated = Date()
            ContentCache.save(id: id, channels: all, series: refs)   // sonraki açılış için anlık görüntü
            await loadEPG(from: client.xmltvURL)
        } catch {
            errorMessage = "Xtream girişi başarısız — sunucu/kullanıcı/şifreyi kontrol edin."
        }
    }

    /// Tüm kaynakları ve kimlik bilgilerini temizle (tam sıfırlama).
    func signOut() {
        for pl in playlists { KeychainStore.clear(id: pl.id); ContentCache.clear(id: pl.id) }
        KeychainStore.clear()   // eski tekil kayıt
        playlists = []; setActive(nil)
        LocalStore.save([PlaylistMeta](), key: LocalStore.Key.playlists)
        LocalStore.save("", key: LocalStore.Key.activePlaylist)
        channels = []; series = []; epg = nil; xtreamClient = nil; lastUpdated = nil
    }

    // MARK: - Ayarlar: User-Agent + önbellek
    var userAgent: String { AppSettings.userAgent }

    /// Özel User-Agent'ı kaydeder, session'ı yeniden kurar ve aktif kaynağı yeniden yükler.
    func applyUserAgent(_ ua: String) async {
        AppSettings.userAgent = ua
        session = LibraryStore.makeSession()
        if let pl = activePlaylist { await reload(pl, background: true) }
    }

    /// Görsel/HTTP önbelleğini + aktif kaynağın içerik anlık görüntüsünü temizler.
    func clearCache() {
        URLCache.shared.removeAllCachedResponses()
        if let id = activePlaylistId { ContentCache.clear(id: id) }
    }

    // MARK: - Program hatırlatıcıları (yerel bildirim)
    private func reminderId(_ ch: Channel, _ p: EpgEntry) -> String {
        "\(ch.id)_\(Int(p.start.timeIntervalSince1970))"
    }
    func isReminderSet(_ ch: Channel, _ p: EpgEntry) -> Bool {
        reminders[reminderId(ch, p)] != nil
    }

    /// Program için hatırlatıcı ekler/kaldırır. Başlamadan ~2 dk önce yerel bildirim.
    func toggleReminder(_ ch: Channel, _ p: EpgEntry) async {
        let id = reminderId(ch, p)
        if reminders[id] != nil {
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
            reminders[id] = nil
            persistReminders()
            return
        }
        // İzin iste
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        guard granted else { errorMessage = "Bildirim izni verilmedi."; return }

        let fire = p.start.addingTimeInterval(-120)          // 2 dk önce
        guard fire > Date() else { errorMessage = "Program başlamış — hatırlatıcı kurulamaz."; return }

        let content = UNMutableNotificationContent()
        content.title = p.title
        content.body = "\(ch.name) · başlıyor"
        content.sound = .default
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))

        reminders[id] = Reminder(id: id, channelName: ch.name, programTitle: p.title, fireDate: fire, start: p.start)
        persistReminders()
    }

    /// Süresi geçmiş hatırlatıcıları temizle (başlangıcı geçmiş olanlar).
    func pruneReminders() {
        let now = Date()
        let expired = reminders.filter { $0.value.start < now }.map(\.key)
        guard !expired.isEmpty else { return }
        expired.forEach { reminders[$0] = nil }
        persistReminders()
    }

    func clearReminders() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: Array(reminders.keys))
        reminders.removeAll()
        persistReminders()
    }

    private func persistReminders() {
        LocalStore.save(reminders, key: LocalStore.Key.reminders)
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
