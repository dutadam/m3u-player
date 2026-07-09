import AVFoundation
import AVKit

/// Resim-içinde-resim (PiP) kontrolcüsü — AVPlayer katmanına bağlanır.
/// `canStartPictureInPictureAutomaticallyFromInline` ile uygulamadan çıkılınca otomatik PiP'e geçer;
/// arka plan sesi ise AVAudioSession `.playback` + Info.plist UIBackgroundModes=audio ile sağlanır.
#if os(iOS)
final class PiPController: NSObject, ObservableObject, AVPictureInPictureControllerDelegate {
    @Published var isSupported = AVPictureInPictureController.isPictureInPictureSupported()
    @Published var isPossible = false
    @Published var isActive = false

    private var controller: AVPictureInPictureController?
    private var possibleObs: NSKeyValueObservation?

    /// Verilen AVPlayerLayer için PiP kontrolcüsünü (bir kez) kurar.
    func setup(with layer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        if let c = controller, c.playerLayer === layer { return }   // aynı katman → tekrar kurma
        guard let c = AVPictureInPictureController(playerLayer: layer) else { return }
        c.delegate = self
        c.canStartPictureInPictureAutomaticallyFromInline = true
        controller = c
        possibleObs = c.observe(\.isPictureInPicturePossible, options: [.initial, .new]) { [weak self] ctrl, _ in
            Task { @MainActor in self?.isPossible = ctrl.isPictureInPicturePossible }
        }
    }

    /// Kullanıcı butonu — PiP'i başlat/durdur.
    func toggle() {
        guard let c = controller else { return }
        if c.isPictureInPictureActive { c.stopPictureInPicture() }
        else if c.isPictureInPicturePossible { c.startPictureInPicture() }
    }

    func teardown() {
        possibleObs = nil
        controller = nil
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ c: AVPictureInPictureController) {
        Task { @MainActor in isActive = true }
    }
    func pictureInPictureControllerDidStopPictureInPicture(_ c: AVPictureInPictureController) {
        Task { @MainActor in isActive = false }
    }
}
#else
/// PiP olmayan platformlar için nötr stub (aynı arayüz).
final class PiPController: ObservableObject {
    @Published var isSupported = false
    @Published var isPossible = false
    @Published var isActive = false
    func setup(with layer: AVPlayerLayer) {}
    func toggle() {}
    func teardown() {}
}
#endif

/// Ses oturumu — arka plan/PiP sesi için `.playback`. Oynatma başlamadan çağrılmalı.
enum AudioSessionManager {
    static func activatePlayback() {
        #if os(iOS)
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .moviePlayback)
        try? s.setActive(true)
        #endif
    }
}
