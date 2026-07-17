import SwiftUI
import AVFoundation

// Kontrolsüz, temiz AVPlayer katmanı (VideoPlayer'ın chrome'u olmadan) — çoklu ekran hücreleri için.

#if os(iOS) || os(tvOS)
import UIKit

final class PlayerContainerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspectFill
    var onLayerReady: ((AVPlayerLayer) -> Void)? = nil   // PiP kurulumu için katman referansı
    func makeUIView(context: Context) -> PlayerContainerView {
        let v = PlayerContainerView()
        v.playerLayer.player = player
        v.playerLayer.videoGravity = gravity
        v.backgroundColor = .black
        onLayerReady?(v.playerLayer)
        return v
    }
    func updateUIView(_ uiView: PlayerContainerView, context: Context) {
        if uiView.playerLayer.player !== player { uiView.playerLayer.player = player }
        uiView.playerLayer.videoGravity = gravity
    }
}

#else
struct PlayerLayerView: View {
    let player: AVPlayer
    var gravity: Int = 0
    var onLayerReady: ((AVPlayerLayer) -> Void)? = nil
    var body: some View { Color.black }
}
#endif
