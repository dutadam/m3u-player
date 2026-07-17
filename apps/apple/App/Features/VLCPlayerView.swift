import SwiftUI

// VLCKit oynatıcı + ses/altyazı track kontrolü. MKV/AVI/TS/exotik codec fallback.
// MobileVLCKit paketi eklenmeden proje derlensin diye `#if canImport` ile korunur.
// project.yml'ye eklendi: tylerjonesio/vlckit-spm → MobileVLCKit.

/// Ses/altyazı track seçeneği (her iki motor için ortak model).
struct TrackOption: Identifiable, Hashable {
    let id: Int
    let name: String
}

#if canImport(MobileVLCKit) && os(iOS)
import MobileVLCKit

/// VLCMediaPlayer'ı saran controller — track listeleri + seçim.
final class VLCController: ObservableObject {
    static var isAvailable: Bool { true }
    let player = VLCMediaPlayer()
    @Published var audioTracks: [TrackOption] = []
    @Published var subtitleTracks: [TrackOption] = []

    func play(url: URL) {
        if player.media == nil || player.media?.url != url {
            player.media = Self.makeMedia(url)
        }
        player.play()
    }
    func pause() { player.pause() }
    func stop() { player.stop() }
    /// Aynı URL'yi zorla yeniden yükle (canlı yayın koptuğunda yeniden bağlanma).
    func reload(url: URL) {
        player.stop()
        player.media = Self.makeMedia(url)
        player.play()
    }

    /// Özel User-Agent + altyazı stil option'larını VLCMedia'ya ekle.
    private static func makeMedia(_ url: URL) -> VLCMedia {
        let media = VLCMedia(url: url)
        let ua = AppSettings.userAgent.trimmingCharacters(in: .whitespaces)
        if !ua.isEmpty { media.addOption(":http-user-agent=\(ua)") }
        // Altyazı stili (freetype). rel-fontsize küçüldükçe yazı büyür.
        let relSize = [24, 18, 14, 10][min(3, max(0, AppSettings.subtitleSize))]
        media.addOption(":freetype-rel-fontsize=\(relSize)")
        media.addOption(":freetype-color=\(AppSettings.subtitleColor)")
        media.addOption(":freetype-outline-thickness=4")
        if AppSettings.subtitleBackground {
            media.addOption(":freetype-background-opacity=160")
            media.addOption(":freetype-background-color=0")
        } else {
            media.addOption(":freetype-background-opacity=0")
        }
        return media
    }
    var isPlaying: Bool { player.isPlaying }

    func refreshTracks() {
        let aIdx = player.audioTrackIndexes as? [NSNumber] ?? []
        let aNames = player.audioTrackNames as? [String] ?? []
        audioTracks = zip(aIdx, aNames).map { TrackOption(id: $0.intValue, name: $1) }
        let sIdx = player.videoSubTitlesIndexes as? [NSNumber] ?? []
        let sNames = player.videoSubTitlesNames as? [String] ?? []
        subtitleTracks = zip(sIdx, sNames).map { TrackOption(id: $0.intValue, name: $1) }
    }
    var currentAudio: Int { Int(player.currentAudioTrackIndex) }
    var currentSubtitle: Int { Int(player.currentVideoSubTitleIndex) }
    func setAudio(_ id: Int) { player.currentAudioTrackIndex = Int32(id) }
    func setSubtitle(_ id: Int) { player.currentVideoSubTitleIndex = Int32(id) }

    // Senkron gecikmeleri (VLC µs cinsinden tutar; UI ms kullanır)
    func setSubtitleDelay(ms: Double) { player.currentVideoSubTitleDelay = Int(ms * 1000) }
    func setAudioDelay(ms: Double) { player.currentAudioPlaybackDelay = Int(ms * 1000) }

    /// Altyazı stili değişince: aynı medyayı yeni option'larla konumdan yeniden yükle.
    func applySubtitleStyle() {
        guard let url = player.media?.url else { return }
        let t = player.time
        let sub = player.currentVideoSubTitleIndex
        player.media = Self.makeMedia(url)
        player.play()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
            self?.player.time = t
            self?.player.currentVideoSubTitleIndex = sub
        }
    }

    // Seek / süre (VOD)
    var position: Double { Double(player.position) }                     // 0..1
    var lengthSeconds: Double { Double(player.media?.length.intValue ?? 0) / 1000 }
    var timeSeconds: Double { Double(player.time.intValue) / 1000 }
    func seek(toFraction f: Double) { player.position = Float(max(0, min(1, f))) }
    func togglePlay() { if player.isPlaying { player.pause() } else { player.play() } }
}

struct VLCPlayerView: UIViewRepresentable {
    @ObservedObject var controller: VLCController
    let url: URL
    static var isAvailable: Bool { true }

    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var url: URL? }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        controller.player.drawable = view
        controller.play(url: url)
        context.coordinator.url = url
        return view
    }
    func updateUIView(_ uiView: UIView, context: Context) {
        if context.coordinator.url != url {         // kanal değişti → yeniden oynat
            context.coordinator.url = url
            controller.play(url: url)
        }
    }
}

#else

/// MobileVLCKit yokken no-op controller + yer-tutucu.
final class VLCController: ObservableObject {
    static var isAvailable: Bool { false }
    @Published var audioTracks: [TrackOption] = []
    @Published var subtitleTracks: [TrackOption] = []
    func play(url: URL) {}
    func pause() {}
    func stop() {}
    func reload(url: URL) {}
    var isPlaying: Bool { false }
    func refreshTracks() {}
    var currentAudio: Int { -1 }
    var currentSubtitle: Int { -1 }
    func setAudio(_ id: Int) {}
    func setSubtitle(_ id: Int) {}
    func setSubtitleDelay(ms: Double) {}
    func setAudioDelay(ms: Double) {}
    func applySubtitleStyle() {}
    var position: Double { 0 }
    var lengthSeconds: Double { 0 }
    var timeSeconds: Double { 0 }
    func seek(toFraction f: Double) {}
    func togglePlay() {}
}

struct VLCPlayerView: View {
    @ObservedObject var controller: VLCController
    let url: URL
    static var isAvailable: Bool { false }
    var body: some View {
        Color.black.overlay(
            Text("Bu format VLCKit gerektirir.\nXcode'da MobileVLCKit paketini ekleyin.")
                .multilineTextAlignment(.center).font(.footnote).foregroundStyle(.secondary).padding()
        )
    }
}

#endif
