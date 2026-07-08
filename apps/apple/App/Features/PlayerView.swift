import SwiftUI
import AVKit
import AVFoundation
import Core
import Design

/// Oynatıcı — kontrolsüz video katmanı + özel overlay. AVPlayer (HLS/MP4) + VLCKit fallback.
/// Canlıda kanal ↑/↓, EPG "şimdi", AirPlay; VOD/dizide resume + ilerleme kaydı.
struct PlayerView: View {
    @EnvironmentObject private var library: LibraryStore
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

    // Dizi bölüm kuyruğu (otomatik sonraki bölüm için)
    let queue: [Channel]
    @State private var queueIndex: Int

    init(channel: Channel, queue: [Channel] = [], queueIndex: Int = 0) {
        _current = State(initialValue: channel)
        self.queue = queue
        _queueIndex = State(initialValue: queueIndex)
    }

    private var hasNext: Bool { !queue.isEmpty && queueIndex + 1 < queue.count }

    private var isLive: Bool { current.kind == .live }
    private var nowTitle: String? { library.epg?.nowPlaying(for: current)?.title }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Group {
                if activeEngine == .avPlayer {
                    PlayerLayerView(player: player, gravity: fillMode ? .resizeAspectFill : .resizeAspect)
                } else if let u = vlcURL {
                    VLCPlayerView(controller: vlc, url: u)
                }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture { toggleControls() }

            if isBuffering && !showError {
                ProgressView().tint(.white).scaleEffect(1.4)
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
        .onAppear { library.addRecent(current); start(); startTicker() }
        .onDisappear { saveProgress(); ticker?.invalidate(); player.pause(); vlc.stop() }
        .sheet(isPresented: $showTracks) { tracksSheet }
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

            // Orta play/pause
            Button { togglePlay() } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 40)).foregroundStyle(.white)
                    .frame(width: 76, height: 76)
                    .background(.white.opacity(0.14), in: Circle())
            }.buttonStyle(.plain)

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
                    if isLive {
                        iconButton("backward.fill") { step(-1) }
                        iconButton("forward.fill") { step(1) }
                    } else {
                        iconButton("gobackward.10") { skip(-10) }
                        iconButton("goforward.10") { skip(10) }
                    }
                    // Doldur/sığdır — yalnız AVPlayer içeriğinde (VLC kendi katmanında oynar)
                    if activeEngine == .avPlayer {
                        iconButton(fillMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right") {
                            fillMode.toggle(); showControls()
                        }
                    }
                    iconButton("captions.bubble") { vlc.refreshTracks(); showTracks = true; showControls() }
                    #if os(iOS)
                    AirPlayButton().frame(width: 40, height: 40)
                    #endif
                }
            }
            .padding()
            .background(LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom))
        }
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

    private func playCurrent() {
        guard index < candidates.count else { showError = true; isBuffering = false; return }
        let c = candidates[index]
        activeEngine = VLCPlayerView.isAvailable ? c.engine : .avPlayer
        if activeEngine == .vlcKit {
            vlcURL = c.url; isBuffering = false; isPlaying = true
            return
        }
        let item = AVPlayerItem(url: c.url)
        player.replaceCurrentItem(with: item)
        let resume = library.resumePosition(for: current.url.absoluteString)
        if !isLive, resume > 0 { player.seek(to: CMTime(seconds: resume, preferredTimescale: 1)) }
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
        // Bölüm bitişi → otomatik sonraki bölüm (dizi kuyruğu)
        if !isLive, durationSeconds > 1, currentSeconds >= durationSeconds - 1 {
            if !endReached { endReached = true; if hasNext { playNext() } }
        }
        if activeEngine == .vlcKit {
            isBuffering = false
            isPlaying = vlc.isPlaying
            if vlc.audioTracks.isEmpty && vlc.isPlaying { vlc.refreshTracks() }   // track'ler oynama başlayınca gelir
            return
        }
        // Kaynak başarısız oldu veya 12 sn'de oynamadıysa hemen sıradaki adaya geç.
        if player.currentItem?.status == .failed { advance(); return }
        let s = player.timeControlStatus
        isPlaying = (s == .playing)
        isBuffering = (s != .playing)
        if s == .playing { if !isLive { saveProgress() } }
        else if Date().timeIntervalSince(attemptStart) > 12 { advance() }
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
            }
            .navigationTitle("Ses & Altyazı")
            .toolbar { Button("Bitti") { showTracks = false } }
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
