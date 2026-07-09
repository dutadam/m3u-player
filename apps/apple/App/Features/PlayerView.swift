import SwiftUI
import AVKit
import AVFoundation
import Core
import Design

/// Oynatıcı — kontrolsüz video katmanı + özel overlay. AVPlayer (HLS/MP4) + VLCKit fallback.
/// Canlıda kanal ↑/↓, EPG "şimdi", AirPlay; VOD/dizide resume + ilerleme kaydı.
struct PlayerView: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var mini: MiniPlayerStore
    @Environment(\.dismiss) private var dismiss

    @State private var current: Channel
    @State private var player = AVPlayer()
    @State private var candidates: [StreamResolver.Candidate] = []
    @State private var index = 0
    @State private var activeEngine: StreamResolver.Engine = .avPlayer
    @State private var vlcURL: URL?

    @State private var controlsVisible = true
    @State private var isPlaying = true
    @State private var isBuffering = true
    @State private var showError = false
    @State private var ticker: Timer?
    @State private var hideTask: DispatchWorkItem?
    @State private var attemptStart = Date()
    @StateObject private var vlc = VLCController()
    @State private var showTracks = false
    @State private var scrubValue: Double = 0
    @State private var isScrubbing = false
    @State private var fillMode = false
    @State private var endReached = false
    @State private var askResume = false
    @State private var resumeSeconds: Double = 0
    @State private var wantResumeTo: Double?
    // Stabilite: otomatik yeniden bağlanma + donma watchdog
    @State private var didPlay = false
    @State private var reconnecting = false
    @State private var reconnectAttempts = 0
    @State private var lastAdvance = Date()
    @State private var lastSec: Double = -1
    @State private var userPaused = false
    @State private var subDelay: Double = 0        // altyazı senkron (ms)
    @State private var audioDelay: Double = 0       // ses senkron (ms)
    @StateObject private var pip = PiPController()

    // Dizi bölüm kuyruğu (otomatik sonraki bölüm için)
    let queue: [Channel]
    @State private var queueIndex: Int

    init(channel: Channel, queue: [Channel] = [], queueIndex: Int = 0) {
        _current = State(initialValue: channel)
        self.queue = queue
        _queueIndex = State(initialValue: queueIndex)
    }

    private var hasNext: Bool { !queue.isEmpty && queueIndex + 1 < queue.count }
    private var hasPrev: Bool { !queue.isEmpty && queueIndex > 0 }
    private var isSeries: Bool { current.kind == .series && !queue.isEmpty }

    private var isLive: Bool { current.kind == .live }
    private var nowTitle: String? { library.epg?.nowPlaying(for: current)?.title }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Group {
                if activeEngine == .avPlayer {
                    PlayerLayerView(player: player, gravity: fillMode ? .resizeAspectFill : .resizeAspect) { layer in
                        pip.setup(with: layer)
                    }
                } else if let u = vlcURL {
                    VLCPlayerView(controller: vlc, url: u)
                }
            }
            .ignoresSafeArea()

            // Şeffaf dokunma katmanı — VLC'nin UIView'ı dokunmayı yuttuğu için oynatıcının
            // üstünde her zaman aktif bir katman; overlay'i açıp kapatır.
            Color.black.opacity(controlsVisible ? 0.25 : 0.001)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { toggleControls() }

            if isBuffering && !showError && !reconnecting {
                ProgressView().tint(.white).scaleEffect(1.4)
            }

            if reconnecting && !showError {
                VStack(spacing: 10) {
                    ProgressView().tint(.white).scaleEffect(1.2)
                    Text("Yeniden bağlanılıyor… (\(reconnectAttempts))")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
                }
                .padding(18).background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 14))
            }

            if showError { errorOverlay }

            if controlsVisible { controls.transition(.opacity) }

            // "Sonraki Bölüm" — son 40 sn, kuyrukta sıradaki varsa
            if !isLive, hasNext, durationSeconds > 40, currentSeconds >= durationSeconds - 40 {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Button { playNext() } label: {
                            Label("Sonraki Bölüm", systemImage: "forward.end.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(Color.sgAccent, in: Capsule()).foregroundStyle(.white)
                                .shadow(color: Color.sgAccent.opacity(0.4), radius: 10)
                        }.padding(.trailing, 20).padding(.bottom, 110)
                    }
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: controlsVisible)
        .onAppear { AudioSessionManager.activatePlayback(); library.addRecent(current); start(); startTicker() }
        .onDisappear { saveProgress(); ticker?.invalidate(); pip.teardown(); player.pause(); vlc.stop() }
        .sheet(isPresented: $showTracks) { tracksSheet }
        .confirmationDialog("Kaldığın yerden devam edilsin mi?", isPresented: $askResume, titleVisibility: .visible) {
            Button("Devam Et · \(timeStr(resumeSeconds))") { wantResumeTo = resumeSeconds }
            Button("Baştan Başlat") { wantResumeTo = nil }
            Button("İptal", role: .cancel) { }
        }
    }

    // MARK: - Kontrol overlay
    private var controls: some View {
        VStack {
            // Üst
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(current.name).font(.headline).bold().foregroundStyle(.white).lineLimit(1)
                    Text(nowTitle ?? current.group).font(.caption).foregroundStyle(Color.sgDim).lineLimit(1)
                }
                Spacer()
                iconButton(library.isFavorite(current) ? "heart.fill" : "heart",
                           tint: library.isFavorite(current) ? Color.sgLive : .white) {
                    library.toggleFavorite(current); showControls()
                }
                iconButton("xmark") { dismiss() }
            }
            .padding()
            .background(LinearGradient(colors: [.black.opacity(0.6), .clear], startPoint: .top, endPoint: .bottom))

            Spacer()

            // Orta taşıma satırı — geri · oynat/duraklat · ileri (dizi: önceki/sonraki bölüm)
            HStack(spacing: 24) {
                if isSeries {
                    transportButton("backward.end.fill", size: 21, enabled: hasPrev) { playPrev() }
                }
                // Canlı: önceki kanal · VOD/Dizi: 10 sn geri
                transportButton(isLive ? "backward.fill" : "gobackward.10", size: 25) {
                    isLive ? step(-1) : skip(-10)
                }
                Button { togglePlay() } label: {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 38)).foregroundStyle(.white)
                        .frame(width: 76, height: 76)
                        .background(.white.opacity(0.16), in: Circle())
                }.buttonStyle(.plain)
                transportButton(isLive ? "forward.fill" : "goforward.10", size: 25) {
                    isLive ? step(1) : skip(10)
                }
                if isSeries {
                    transportButton("forward.end.fill", size: 21, enabled: hasNext) { playNext() }
                }
            }

            Spacer()

            // Alt
            VStack(spacing: 10) {
                // Seek çubuğu — yalnız VOD/dizi
                if !isLive {
                    HStack(spacing: 9) {
                        Text(timeStr(currentSeconds)).font(.caption2).monospacedDigit().foregroundStyle(.white)
                        Slider(value: $scrubValue, in: 0...1) { editing in
                            isScrubbing = editing
                            if !editing { seek(toFraction: scrubValue) }
                        }.tint(Color.sgAccent)
                        Text(timeStr(durationSeconds)).font(.caption2).monospacedDigit().foregroundStyle(.white)
                    }
                }
                HStack(spacing: 16) {
                    if isLive { LivePill("CANLI") }
                    Spacer()
                    // Doldur/sığdır — yalnız AVPlayer içeriğinde (VLC kendi katmanında oynar)
                    if activeEngine == .avPlayer {
                        iconButton(fillMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right") {
                            fillMode.toggle(); showControls()
                        }
                    }
                    iconButton("captions.bubble") { vlc.refreshTracks(); showTracks = true; showControls() }
                    // PiP — yalnız AVPlayer içeriğinde ve cihaz destekliyorsa
                    if activeEngine == .avPlayer && pip.isSupported {
                        iconButton(pip.isActive ? "pip.exit" : "pip.enter", tint: pip.isPossible ? .white : Color.sgMute) {
                            pip.toggle(); showControls()
                        }
                    }
                    // VLC içeriği → sistem PiP yok; uygulama-içi mini pencereye devret
                    if activeEngine == .vlcKit {
                        iconButton("pip.enter") {
                            mini.present(channel: current, url: vlcURL ?? current.url, seekTo: vlc.position)
                            dismiss()
                        }
                    }
                    #if os(iOS)
                    AirPlayButton().frame(width: 40, height: 40)
                    #endif
                }
            }
            .padding()
            .background(LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom))
        }
    }

    /// Ortadaki taşıma butonu (yarı saydam, devre-dışı destekli).
    private func transportButton(_ name: String, size: CGFloat, enabled: Bool = true,
                                 _ action: @escaping () -> Void) -> some View {
        Button(action: { if enabled { action() } }) {
            Image(systemName: name).font(.system(size: size, weight: .semibold))
                .foregroundStyle(enabled ? .white : .white.opacity(0.3))
                .frame(width: 52, height: 52)
                .background(.white.opacity(enabled ? 0.10 : 0.03), in: Circle())
        }.buttonStyle(.plain).disabled(!enabled)
    }

    private var needsVLC: Bool {
        !VLCPlayerView.isAvailable && candidates.contains { $0.engine == .vlcKit }
    }

    private var errorOverlay: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle").font(.largeTitle).foregroundStyle(Color.sgWarn)
            Text(needsVLC ? "VLCKit gerekli" : "Yayına ulaşılamadı").font(.headline).foregroundStyle(.white)
            Text(needsVLC
                 ? "Bu içerik MKV/AVI/TS formatında — Apple oynatıcı desteklemiyor. Xcode'da MobileVLCKit paketini ekleyin."
                 : "Kaynak geçersiz veya sunucu yanıt vermiyor.")
                .font(.caption).foregroundStyle(Color.sgDim).multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button("Yeniden Dene") { showError = false; start() }
                    .padding(.horizontal, 16).padding(.vertical, 9)
                    .background(Color.sgAccent, in: Capsule()).foregroundStyle(.white)
                Button("Kapat") { dismiss() }
                    .padding(.horizontal, 16).padding(.vertical, 9)
                    .background(Color.sgSurface, in: Capsule()).foregroundStyle(.white)
            }.font(.system(size: 14, weight: .semibold)).padding(.top, 4)
        }
        .padding(24).background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
    }

    private func iconButton(_ name: String, tint: Color = .white, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name).font(.system(size: 16, weight: .semibold)).foregroundStyle(tint)
                .frame(width: 40, height: 40).background(.black.opacity(0.4), in: Circle())
        }.buttonStyle(.plain)
    }

    // MARK: - Oynatma
    private func start() {
        candidates = StreamResolver.candidates(for: current.url)
        index = 0
        isBuffering = true
        endReached = false
        wantResumeTo = nil
        didPlay = false
        reconnecting = false
        reconnectAttempts = 0
        lastAdvance = Date()
        lastSec = -1
        userPaused = false
        subDelay = 0; audioDelay = 0
        vlc.setSubtitleDelay(ms: 0); vlc.setAudioDelay(ms: 0)
        // VOD/dizi'de kayıtlı ilerleme varsa "Baştan / Devam Et" sor.
        let r = library.resumePosition(for: current.url.absoluteString)
        if !isLive, r > 5 { resumeSeconds = r; askResume = true }
        playCurrent()
    }

    /// Sıradaki bölüme geç (dizi kuyruğu).
    private func playNext() {
        guard queueIndex + 1 < queue.count else { return }
        saveProgress()
        queueIndex += 1
        current = queue[queueIndex]
        library.addRecent(current)
        start()
    }

    /// Önceki bölüme geç (dizi kuyruğu).
    private func playPrev() {
        guard queueIndex > 0 else { return }
        saveProgress()
        queueIndex -= 1
        current = queue[queueIndex]
        library.addRecent(current)
        start()
    }

    private func playCurrent() {
        guard index < candidates.count else { showError = true; isBuffering = false; return }
        let c = candidates[index]
        activeEngine = VLCPlayerView.isAvailable ? c.engine : .avPlayer
        if activeEngine == .vlcKit {
            vlcURL = c.url; isBuffering = false; isPlaying = true
            return
        }
        // Özel User-Agent varsa AVURLAsset başlığıyla ver.
        let asset: AVURLAsset
        if let h = AppSettings.uaHeaders {
            asset = AVURLAsset(url: c.url, options: ["AVURLAssetHTTPHeaderFieldsKey": h])
        } else {
            asset = AVURLAsset(url: c.url)
        }
        let item = AVPlayerItem(asset: asset)
        player.replaceCurrentItem(with: item)
        attemptStart = Date()
        player.play(); isPlaying = true
        showControls()
    }

    /// Sıradaki kaynağa geç; tükendiyse hata göster.
    private func advance() {
        index += 1
        if index < candidates.count { playCurrent() }
        else { showError = true; isBuffering = false }
    }

    private func togglePlay() {
        if activeEngine == .vlcKit {
            vlc.togglePlay(); isPlaying = vlc.isPlaying
        } else {
            if player.timeControlStatus == .playing { player.pause(); isPlaying = false }
            else { player.play(); isPlaying = true }
        }
        userPaused = !isPlaying
        lastAdvance = Date()      // devam ederken watchdog'u sıfırla
        showControls()
    }

    // MARK: - Birleşik seek (aktif motor)
    private var durationSeconds: Double {
        activeEngine == .vlcKit ? vlc.lengthSeconds : (player.currentItem?.duration.seconds ?? 0)
    }
    private var currentSeconds: Double {
        activeEngine == .vlcKit ? vlc.timeSeconds : player.currentTime().seconds
    }
    private var fractionProgress: Double {
        if activeEngine == .vlcKit { return vlc.position }
        let d = player.currentItem?.duration.seconds ?? 0
        return d > 0 ? player.currentTime().seconds / d : 0
    }
    private func seek(toFraction f: Double) {
        if activeEngine == .vlcKit { vlc.seek(toFraction: f) }
        else if let d = player.currentItem?.duration.seconds, d > 0, d.isFinite {
            player.seek(to: CMTime(seconds: d * f, preferredTimescale: 600))
        }
    }
    private func skip(_ delta: Double) {
        let d = durationSeconds
        guard d > 0 else { return }
        seek(toFraction: max(0, min(d, currentSeconds + delta)) / d)
        showControls()
    }
    private func timeStr(_ s: Double) -> String {
        guard s.isFinite, s >= 0 else { return "0:00" }
        let t = Int(s), h = t / 3600, m = (t % 3600) / 60, sec = t % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%d:%02d", m, sec)
    }

    private func step(_ d: Int) {
        let list = library.live
        guard let i = list.firstIndex(where: { $0.url == current.url }), !list.isEmpty else { return }
        saveProgress()
        current = list[((i + d) % list.count + list.count) % list.count]
        library.addRecent(current)
        start()
    }

    // MARK: - Kontrol görünürlüğü
    private func toggleControls() { controlsVisible ? hideControls() : showControls() }
    private func showControls() {
        controlsVisible = true
        hideTask?.cancel()
        let t = DispatchWorkItem {
            if activeEngine == .vlcKit || player.timeControlStatus == .playing { controlsVisible = false }
        }
        hideTask = t
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: t)
    }
    private func hideControls() { hideTask?.cancel(); controlsVisible = false }

    // MARK: - Durum & ilerleme
    private func startTicker() {
        ticker = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            Task { @MainActor in updateStatus() }
        }
    }
    private func updateStatus() {
        if !isScrubbing { scrubValue = fractionProgress }
        // "Devam Et" seçildiyse süre hazır olunca kaldığı yere atla (her iki motor).
        if let target = wantResumeTo, durationSeconds > 1 {
            seek(toFraction: min(0.999, target / durationSeconds)); wantResumeTo = nil
        }
        // İlerleme takibi (watchdog): oynatma zamanı ilerliyorsa "akıyor"; ilerleme yoksa donma.
        let cur = currentSeconds
        if cur > lastSec + 0.2 {
            lastSec = cur; lastAdvance = Date()
            if cur > 0.5 {                      // gerçekten oynadı (ilk aday-geçişini bozma)
                didPlay = true
                if reconnecting { reconnecting = false; reconnectAttempts = 0 }
            }
        }
        // Bölüm bitişi → otomatik sonraki bölüm (dizi kuyruğu)
        if !isLive, durationSeconds > 1, currentSeconds >= durationSeconds - 1 {
            if !endReached { endReached = true; if hasNext { playNext() } }
        }
        let frozen = didPlay && !reconnecting && !userPaused && Date().timeIntervalSince(lastAdvance) > 10

        if activeEngine == .vlcKit {
            isBuffering = reconnecting || (!vlc.isPlaying && !didPlay)
            isPlaying = vlc.isPlaying
            if vlc.audioTracks.isEmpty && vlc.isPlaying { vlc.refreshTracks() }   // track'ler oynama başlayınca gelir
            if isLive && frozen { reconnect() }
            return
        }
        // Akış oynadıktan sonra kopan/donan canlı yayında yeniden bağlan; ilk bağlantıda aday-geçişi yap.
        if player.currentItem?.status == .failed {
            (didPlay && isLive) ? reconnect() : advance(); return
        }
        let s = player.timeControlStatus
        isPlaying = (s == .playing)
        isBuffering = (s != .playing) || reconnecting
        if s == .playing { if !isLive { saveProgress() } }
        if isLive && frozen { reconnect() }
        else if !didPlay && s != .playing && Date().timeIntervalSince(attemptStart) > 12 { advance() }
    }

    /// Canlı yayın koptu/dondu → aynı kaynağa artan gecikmeyle yeniden bağlan (maks 6 deneme).
    private func reconnect() {
        guard isLive else { advance(); return }
        reconnectAttempts += 1
        if reconnectAttempts > 6 { reconnecting = false; showError = true; isBuffering = false; return }
        reconnecting = true
        lastAdvance = Date()          // tekrar tetiklemeyi önle
        let delay = min(Double(reconnectAttempts), 5)
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            guard reconnecting else { return }
            lastSec = -1
            if activeEngine == .vlcKit, let u = vlcURL {
                vlc.reload(url: u)     // aynı URL → media'yı sıfırla ve tekrar oynat
            } else {
                index = 0
                playCurrent()
            }
        }
    }
    private func saveProgress() {
        guard !isLive, let item = player.currentItem, item.duration.isNumeric else { return }
        let pos = player.currentTime().seconds, dur = item.duration.seconds
        if pos.isFinite, dur.isFinite {
            library.saveProgress(url: current.url.absoluteString, position: pos, duration: dur)
        }
    }

    // MARK: - Ses & Altyazı seçimi
    private struct TrackItem: Identifiable {
        let id: String; let name: String; let selected: Bool; let apply: () -> Void
    }

    private var tracksSheet: some View {
        let audio = audioItems(), subs = subtitleItems()
        return NavigationStack {
            List {
                Section("Ses") {
                    if audio.isEmpty { Text("Tek ses parçası").foregroundStyle(Color.sgMute) }
                    ForEach(audio) { trackRow($0) }
                }
                Section("Altyazı") {
                    if subs.isEmpty { Text("Altyazı yok").foregroundStyle(Color.sgMute) }
                    ForEach(subs) { trackRow($0) }
                }
                // Senkron gecikmeleri — yalnız VLC motorunda (AVPlayer desteklemez)
                if activeEngine == .vlcKit {
                    Section("Senkron") {
                        delayRow("Altyazı gecikmesi", value: $subDelay) { vlc.setSubtitleDelay(ms: $0) }
                        delayRow("Ses gecikmesi", value: $audioDelay) { vlc.setAudioDelay(ms: $0) }
                    }
                }
            }
            .navigationTitle("Ses & Altyazı")
            .toolbar { Button("Bitti") { showTracks = false } }
        }
    }

    /// Senkron gecikme satırı — ±5 sn, 50 ms adım, sıfırlama.
    private func delayRow(_ title: String, value: Binding<Double>, apply: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title).foregroundStyle(Color.sgText)
                Spacer()
                Text(String(format: "%+.0f ms", value.wrappedValue))
                    .font(.caption).monospacedDigit().foregroundStyle(Color.sgAccent2)
                Button { value.wrappedValue = 0; apply(0) } label: {
                    Image(systemName: "arrow.counterclockwise")
                }.buttonStyle(.plain).foregroundStyle(Color.sgMute)
            }
            Slider(value: value, in: -5000...5000, step: 50) { editing in
                if !editing { apply(value.wrappedValue) }
            }.tint(Color.sgAccent)
        }
    }

    private func trackRow(_ t: TrackItem) -> some View {
        Button { t.apply(); showTracks = false } label: {
            HStack {
                Text(t.name).foregroundStyle(Color.sgText)
                Spacer()
                if t.selected { Image(systemName: "checkmark").foregroundStyle(Color.sgAccent) }
            }
        }
    }

    private func audioItems() -> [TrackItem] {
        if activeEngine == .vlcKit {
            return vlc.audioTracks.map { t in
                TrackItem(id: "a\(t.id)", name: t.name, selected: vlc.currentAudio == t.id) { vlc.setAudio(t.id) }
            }
        }
        guard let item = player.currentItem,
              let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: .audible) else { return [] }
        let cur = item.currentMediaSelection.selectedMediaOption(in: group)
        return group.options.map { opt in
            TrackItem(id: opt.displayName, name: opt.displayName,
                      selected: cur.map { opt.isEqual($0) } ?? false) { item.select(opt, in: group) }
        }
    }

    private func subtitleItems() -> [TrackItem] {
        if activeEngine == .vlcKit {
            // VLC listesi genelde "Disable" içerir.
            return vlc.subtitleTracks.map { t in
                TrackItem(id: "s\(t.id)", name: t.name, selected: vlc.currentSubtitle == t.id) { vlc.setSubtitle(t.id) }
            }
        }
        guard let item = player.currentItem,
              let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: .legible) else { return [] }
        let cur = item.currentMediaSelection.selectedMediaOption(in: group)
        var items = [TrackItem(id: "off", name: "Kapalı", selected: cur == nil) { item.select(nil, in: group) }]
        items += group.options.map { opt in
            TrackItem(id: opt.displayName, name: opt.displayName,
                      selected: cur.map { opt.isEqual($0) } ?? false) { item.select(opt, in: group) }
        }
        return items
    }
}

// AirPlay yönlendirme butonu (iOS).
#if os(iOS)
struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let v = AVRoutePickerView()
        v.tintColor = .white
        v.activeTintColor = UIColor(Color.sgAccent)
        return v
    }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
#endif
