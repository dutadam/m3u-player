import SwiftUI
import AVKit
import Core
import Design

/// Oynatıcı görünümü. Faz 1 iskelet: AVPlayer + StreamResolver fallback zinciri.
/// VLCKit fallback (MKV/AVI) bir sonraki adımda entegre edilir (MobileVLCKit paketi).
struct PlayerView: View {
    let channel: Channel
    @Environment(\.dismiss) private var dismiss
    @State private var player = AVPlayer()
    @State private var candidates: [StreamResolver.Candidate] = []
    @State private var index = 0
    @State private var showError = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VideoPlayer(player: player)
                .ignoresSafeArea()

            VStack {
                HStack {
                    VStack(alignment: .leading) {
                        Text(channel.name).font(.headline).foregroundStyle(.white)
                        Text(channel.group).font(.caption).foregroundStyle(.sgDim)
                    }
                    Spacer()
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
        .onAppear(perform: start)
        .onDisappear { player.pause() }
    }

    private func start() {
        candidates = StreamResolver.candidates(for: channel.url)
        index = 0
        playCurrent()
    }

    /// Sıradaki adayı dener; AVPlayer için uygun olmayan (VLCKit) kaynakları şimdilik atlar.
    private func playCurrent() {
        guard index < candidates.count else { showError = true; return }
        let c = candidates[index]
        // NOT: c.engine == .vlcKit olan kaynaklar VLCKit entegrasyonuyla oynatılacak.
        let item = AVPlayerItem(url: c.url)
        player.replaceCurrentItem(with: item)
        player.play()
        // Watchdog: ~12 sn içinde oynamazsa sıradaki kaynağa geç (spec §5).
        DispatchQueue.main.asyncAfter(deadline: .now() + 12) {
            if player.timeControlStatus != .playing {
                index += 1
                playCurrent()
            }
        }
    }
}
