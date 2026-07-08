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

    init(channel: Channel) { _current = State(initialValue: channel) }

    private var isLive: Bool { current.kind == .live }
    private var nowTitle: String? { library.epg?.nowPlaying(for: current)?.title }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Group {
                if activeEngine == .avPlayer { PlayerLayerView(player: player) }
                else if let u = vlcURL { VLCPlayerView(controller: vlc, url: u) }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture { toggleControls() }

            if isBuffering && !showError {
                ProgressView().tint(.white).scaleEffect(1.4)
            }

            if showError { errorOverlay }

            if controlsVisible { controls.transition(.opacity) }
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
            HStack(spacing: 16) {
                if isLive { LivePill(current.kind == .live ? "CANLI" : "LIVE") }
                Spacer()
                if isLive {
                    iconButton("backward.fill") { step(-1) }
                    iconButton("forward.fill") { step(1) }
                }
                iconButton("captions.bubble") { vlc.refreshTracks(); showTracks = true; showControls() }
                #if os(iOS)
                AirPlayButton().frame(width: 40, height: 40)
                #endif
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
        playCurrent()
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
        if player.timeControlStatus == .playing { player.pause(); isPlaying = false }
        else { player.play(); isPlaying = true }
        showControls()
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
        if activeEngine == .vlcKit {
            isBuffering = false
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
