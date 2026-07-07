import SwiftUI
import AVKit
import AVFoundation
import Core
import Design

/// Oynatıcı görünümü. AVPlayer (HLS/MP4) + VLCKit (MKV/AVI/exotik) fallback, StreamResolver zinciriyle.
/// VLCKit yalnız MobileVLCKit paketi eklendiğinde aktif olur (#if canImport); yoksa AVPlayer kullanılır.
struct PlayerView: View {
    let channel: Channel
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var player = AVPlayer()
    @State private var candidates: [StreamResolver.Candidate] = []
    @State private var index = 0
    @State private var showError = false
    @State private var progressTimer: Timer?
    @State private var activeEngine: StreamResolver.Engine = .avPlayer
    @State private var vlcURL: URL?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Group {
                if activeEngine == .avPlayer {
                    VideoPlayer(player: player)
                } else if let u = vlcURL {
                    VLCPlayerView(url: u)
                }
            }
            .ignoresSafeArea()

            VStack {
                HStack {
                    VStack(alignment: .leading) {
                        Text(channel.name).font(.headline).foregroundStyle(.white)
                        Text(channel.group).font(.caption).foregroundStyle(.sgDim)
                    }
                    Spacer()
                    Button { library.toggleFavorite(channel) } label: {
                        Image(systemName: library.isFavorite(channel) ? "heart.fill" : "heart").padding(10)
                            .background(.black.opacity(0.4), in: Circle())
                            .foregroundStyle(library.isFavorite(channel) ? Color.sgLive : .white)
                    }
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").padding(10)
                            .background(.black.opacity(0.4), in: Circle()).foregroundStyle(.white)
                    }
                }
                .padding()
                Spacer()
                if showError {
                    Text("Yayına ulaşılamadı. Kaynak geçersiz veya sunucu yanıt vermiyor.")
                        .font(.footnote).foregroundStyle(.sgDim)
                        .padding().background(.black.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
                        .padding()
                }
            }
        }
        .onAppear { library.addRecent(channel); start(); startProgressTracking() }
        .onDisappear { saveProgress(); progressTimer?.invalidate(); player.pause() }
    }

    private func start() {
        candidates = StreamResolver.candidates(for: channel.url)
        index = 0
        playCurrent()
    }

    /// Sıradaki adayı dener. Motor ipucuna göre AVPlayer veya VLCKit'e yönlendirir.
    private func playCurrent() {
        guard index < candidates.count else { showError = true; return }
        let c = candidates[index]
        activeEngine = VLCPlayerView.isAvailable ? c.engine : .avPlayer   // paket yoksa AV'ye düş

        if activeEngine == .vlcKit {
            vlcURL = c.url            // VLCPlayerView oynatır (kendi hata/yeniden-bağlanma yönetimi)
            return
        }

        let item = AVPlayerItem(url: c.url)
        player.replaceCurrentItem(with: item)
        // VOD/dizi ise kaldığı yerden devam (canlıda anlamsız).
        let resume = library.resumePosition(for: channel.url.absoluteString)
        if channel.kind != .live, resume > 0 {
            player.seek(to: CMTime(seconds: resume, preferredTimescale: 1))
        }
        player.play()
        // Watchdog: ~12 sn içinde oynamazsa sıradaki kaynağa geç (spec §5).
        DispatchQueue.main.asyncAfter(deadline: .now() + 12) {
            if activeEngine == .avPlayer, player.timeControlStatus != .playing {
                index += 1
                playCurrent()
            }
        }
    }

    // MARK: - İlerleme takibi (devam et)
    private func startProgressTracking() {
        guard channel.kind != .live else { return }   // yalnız VOD/dizi
        progressTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { _ in
            Task { @MainActor in saveProgress() }
        }
    }

    private func saveProgress() {
        guard channel.kind != .live,
              let item = player.currentItem, item.duration.isNumeric else { return }
        let pos = player.currentTime().seconds
        let dur = item.duration.seconds
        if pos.isFinite, dur.isFinite {
            library.saveProgress(url: channel.url.absoluteString, position: pos, duration: dur)
        }
    }
}
