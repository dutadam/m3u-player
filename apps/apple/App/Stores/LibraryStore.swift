import Foundation
import Core

/// Kanal/playlist durumunu yöneten store. Core modülünü tüketir.
/// Faz 1 iskelet: yükleme + EPG indeksleme + türe göre bölütleme.
@MainActor
final class LibraryStore: ObservableObject {
    @Published var channels: [Channel] = []
    @Published var epg: EPGIndex?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let session = URLSession.shared

    // MARK: - Türetilmiş gruplar (UI için)
    var live: [Channel] { channels.filter { $0.kind == .live } }
    var movies: [Channel] { channels.filter { $0.kind == .vod } }
    var seriesChannels: [Channel] { channels.filter { $0.kind == .series } }

    var groups: [String: [Channel]] {
        Dictionary(grouping: channels, by: \.group)
    }

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
            await loadEPG(from: client.xmltvURL)
        } catch {
            errorMessage = "Xtream girişi başarısız — sunucu/kullanıcı/şifreyi kontrol edin."
        }
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
