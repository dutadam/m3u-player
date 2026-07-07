import SwiftUI

// VLCKit oynatıcı sarmalayıcı — MKV/AVI/exotik codec fallback (StreamResolver.Engine.vlcKit).
// MobileVLCKit paketi eklenmeden proje derlensin diye `#if canImport` ile korunur.
// Xcode: File → Add Packages → https://github.com/videolan/vlckit (veya CocoaPods "MobileVLCKit").

#if canImport(MobileVLCKit) && (os(iOS) || os(tvOS))
import MobileVLCKit

struct VLCPlayerView: UIViewRepresentable {
    let url: URL
    static var isAvailable: Bool { true }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let player = VLCMediaPlayer()
        player.drawable = view
        player.media = VLCMedia(url: url)
        player.play()
        context.coordinator.player = player
        context.coordinator.url = url
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard context.coordinator.url != url else { return }
        context.coordinator.url = url
        context.coordinator.player?.media = VLCMedia(url: url)
        context.coordinator.player?.play()
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.player?.stop()
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var player: VLCMediaPlayer?; var url: URL? }
}

#else

/// MobileVLCKit yokken yer-tutucu. Paket eklenince gerçek VLC oynatıcı derlenir.
struct VLCPlayerView: View {
    let url: URL
    static var isAvailable: Bool { false }
    var body: some View {
        Color.black.overlay(
            Text("Bu format VLCKit gerektirir.\nXcode'da MobileVLCKit paketini ekleyin.")
                .multilineTextAlignment(.center)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding()
        )
    }
}

#endif
