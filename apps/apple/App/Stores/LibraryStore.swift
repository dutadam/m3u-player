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
    func loadXtream(_ creds: XtreamCredentials) async {
        isLoading = true; errorMessage = nil
        defer { isLoading = false }
        let client = XtreamClient(creds: creds, session: session)
        do {
            let info = try await client.authenticate()
            guard info.isActive else { errorMessage = "Abonelik aktif değil."; return }
            // NOT: canlı/VOD/dizi listelerinin çekilmesi bir sonraki adımda (yanıt modelleri) eklenecek.
            // Şimdilik m3u_plus üzerinden yüklenebilir:
            await loadM3U(from: client.apiURL(.accountInfo))  // placeholder — genişletilecek
            await loadEPG(from: client.xmltvURL)
        } catch {
            errorMessage = "Xtream girişi başarısız — sunucu/kullanıcı/şifreyi kontrol edin."
        }
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
