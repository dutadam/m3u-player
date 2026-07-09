import SwiftUI
import AVFoundation
import Core
import Design

/// Çoklu ekran — 2×1 / 2×2 / 2×3 responsive grid (mobil · iPad · Mac · TV, maks 6 yayın).
/// Her slota istenen kanal atanır; dokunulan hücrenin sesi aktif olur. Bir slot tam ekrana
/// büyütülüp geri küçültülebilir. Düzen + kanal atamaları kaydedilir (sonraki oturumda hızlı açılış).
struct MultiView: View {
    @EnvironmentObject private var library: LibraryStore

    /// Slot düzeni. Ham değer sütun×satır etiketidir; `count` toplam hücre (maks 6).
    enum Layout: String, CaseIterable, Identifiable {
        case duo = "2×1", quad = "2×2", six = "2×3"
        var id: String { rawValue }
        var count: Int { switch self { case .duo: 2; case .quad: 4; case .six: 6 } }
        /// Genişliğe göre sütun sayısı (responsive).
        func columns(wide: Bool) -> Int {
            switch self {
            case .duo:  return wide ? 2 : 1
            case .quad: return 2
            case .six:  return wide ? 3 : 2
            }
        }
    }

    private static let poolSize = 6
    private static let saveKey = "cheesino.multiview"

    @State private var layout: Layout = .quad
    @State private var slots: [Channel?] = Array(repeating: nil, count: MultiView.poolSize)
    @State private var players: [AVPlayer] = (0..<MultiView.poolSize).map { _ in AVPlayer() }
    @State private var activeIndex = 0
    @State private var pickerFor: Int?
    @State private var fullscreenSlot: Int?
    @State private var didRestore = false

    // MARK: - Kalıcı yapılandırma
    private struct Config: Codable { var layout: String; var slotIds: [String?] }

    var body: some View {
        Group {
            if let fs = fullscreenSlot { fullscreenView(fs) }
            else { gridView }
        }
        .background(Color.sgGround)
        .navigationTitle("Çoklu Ekran")
        .toolbar(fullscreenSlot == nil ? .automatic : .hidden, for: .navigationBar)
        .toolbar {
            if fullscreenSlot == nil {
                ToolbarItem(placement: .principal) { layoutPicker }
            }
        }
        .onAppear { AudioSessionManager.activatePlayback(); restoreOrSeed() }
        .onDisappear { players.forEach { $0.pause() } }
        .sheet(item: Binding(get: { pickerFor.map { IndexBox(id: $0) } },
                             set: { pickerFor = $0?.id })) { box in
            MultiChannelPicker(slot: box.id) { assign($0, to: box.id); pickerFor = nil }
        }
    }

    private var layoutPicker: some View {
        Picker("Düzen", selection: Binding(get: { layout }, set: { changeLayout($0) })) {
            ForEach(Layout.allCases) { l in Text(l.rawValue).tag(l) }
        }
        .pickerStyle(.segmented)
        .frame(maxWidth: 240)
    }

    // MARK: - Grid
    private var gridView: some View {
        GeometryReader { geo in
            let wide = geo.size.width > 700
            let cols = Array(repeating: GridItem(.flexible(), spacing: 8),
                             count: layout.columns(wide: wide))
            ScrollView {
                LazyVGrid(columns: cols, spacing: 8) {
                    ForEach(0..<layout.count, id: \.self) { i in cell(i) }
                }
                .padding(8)
                .frame(minHeight: geo.size.height, alignment: .center)   // dikey ortala
            }
        }
    }

    private func cell(_ i: Int) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14).fill(Color.sgElevated)
            if slots[i] != nil {
                PlayerLayerView(player: players[i])
            } else {
                VStack(spacing: 4) {
                    Image(systemName: "plus.circle").font(.title2)
                    Text("Kanal ekle").font(.system(size: 10, weight: .semibold))
                }.foregroundStyle(Color.sgMute)
            }
            VStack {
                HStack {
                    if let ch = slots[i] {
                        Text(ch.name).font(.system(size: 10, weight: .bold)).lineLimit(1)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 3)
                            .background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 6))
                    }
                    Spacer()
                    if slots[i] != nil {
                        // Tam ekran büyüt
                        Button { enterFullscreen(i) } label: {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                .padding(5).background(.black.opacity(0.55), in: Circle())
                        }.buttonStyle(.plain)
                        Image(systemName: i == activeIndex ? "speaker.wave.2.fill" : "speaker.slash.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(i == activeIndex ? Color.sgAccent : .white.opacity(0.6))
                            .padding(5).background(.black.opacity(0.55), in: Circle())
                    }
                }
                Spacer()
            }
            .padding(7)
        }
        .aspectRatio(16/9, contentMode: .fit)
        .overlay(RoundedRectangle(cornerRadius: 14)
            .strokeBorder(i == activeIndex && slots[i] != nil ? Color.sgAccent : .clear, lineWidth: 2))
        .contentShape(Rectangle())
        .onTapGesture { slots[i] == nil ? (pickerFor = i) : setActive(i) }
        .contextMenu {
            if slots[i] != nil {
                Button { enterFullscreen(i) } label: { Label("Tam Ekran", systemImage: "arrow.up.left.and.arrow.down.right") }
            }
            Button { pickerFor = i } label: { Label("Kanal Değiştir", systemImage: "arrow.left.arrow.right") }
            if slots[i] != nil {
                Button(role: .destructive) { clear(i) } label: { Label("Kaldır", systemImage: "xmark") }
            }
        }
    }

    // MARK: - Tam ekran
    private func fullscreenView(_ slot: Int) -> some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            PlayerLayerView(player: players[slot], gravity: .resizeAspect).ignoresSafeArea()
            HStack {
                Text(slots[slot]?.name ?? "").font(.headline).bold().foregroundStyle(.white).lineLimit(1)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(.black.opacity(0.5), in: Capsule())
                Spacer()
                Button { exitFullscreen() } label: {
                    Label("Küçült", systemImage: "arrow.down.right.and.arrow.up.left")
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.black.opacity(0.5), in: Capsule())
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 8)
        }
    }

    private func enterFullscreen(_ i: Int) {
        activeIndex = i
        withAnimation(.easeInOut(duration: 0.2)) { fullscreenSlot = i }
        syncPlayback()
    }

    private func exitFullscreen() {
        withAnimation(.easeInOut(duration: 0.2)) { fullscreenSlot = nil }
        syncPlayback()
    }

    /// Tek doğruluk kaynağı: yalnız görünür + dolu slotlar oynar; gerisi durur. Tam ekranda
    /// sadece o slot oynar. Aktif slotun sesi açık, diğerleri sessiz.
    private func syncPlayback() {
        for i in 0..<players.count {
            let visible = fullscreenSlot == nil ? (i < layout.count) : (i == fullscreenSlot)
            if visible, slots[i] != nil {
                players[i].isMuted = (i != activeIndex)
                players[i].play()
            } else {
                players[i].pause()
            }
        }
    }

    // MARK: - Mantık
    private func restoreOrSeed() {
        guard !didRestore else { return }
        didRestore = true
        if let cfg = LocalStore.load(Config.self, key: Self.saveKey),
           let l = Layout(rawValue: cfg.layout) {
            layout = l
            for (i, id) in cfg.slotIds.prefix(Self.poolSize).enumerated() {
                if let id, let ch = library.channels.first(where: { $0.id == id }) { assign(ch, to: i, persist: false) }
            }
            if slots.contains(where: { $0 != nil }) {
                activeIndex = min(firstFilled ?? 0, layout.count - 1)
                syncPlayback()
                return
            }
        }
        // İlk kez / kayıt yok → görünür canlıdan doldur
        for (i, ch) in library.visibleLive.prefix(layout.count).enumerated() { assign(ch, to: i, persist: false) }
        setActive(0)
        persist()
    }

    private var firstFilled: Int? { (0..<Self.poolSize).first { slots[$0] != nil } }

    private func changeLayout(_ l: Layout) {
        layout = l
        if activeIndex >= l.count { activeIndex = firstFilled.map { min($0, l.count - 1) } ?? 0 }
        syncPlayback()
        persist()
    }

    private func assign(_ ch: Channel, to slot: Int, persist doPersist: Bool = true) {
        slots[slot] = ch
        let url = StreamResolver.candidates(for: ch.url).first?.url ?? ch.url
        players[slot].replaceCurrentItem(with: AVPlayerItem(url: url))
        syncPlayback()
        if doPersist { persist() }
    }

    private func clear(_ slot: Int) {
        players[slot].replaceCurrentItem(with: nil)
        slots[slot] = nil
        syncPlayback()
        persist()
    }

    private func setActive(_ i: Int) {
        activeIndex = i
        syncPlayback()
    }

    private func persist() {
        let cfg = Config(layout: layout.rawValue, slotIds: (0..<Self.poolSize).map { slots[$0]?.id })
        LocalStore.save(cfg, key: Self.saveKey)
    }
}

/// Aranabilir kanal seçici — çoklu ekran slotu için (tüm görünür canlı kanallar).
private struct MultiChannelPicker: View {
    @EnvironmentObject private var library: LibraryStore
    let slot: Int
    let onPick: (Channel) -> Void
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    private var results: [Channel] {
        query.isEmpty ? library.visibleLive
                      : library.visibleLive.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        NavigationStack {
            List(results.prefix(300).map { $0 }) { ch in
                Button { onPick(ch) } label: {
                    HStack(spacing: 10) {
                        Text(ch.name).foregroundStyle(Color.sgText).lineLimit(1)
                        Spacer()
                        Text(ch.group).font(.caption).foregroundStyle(Color.sgMute).lineLimit(1)
                    }
                }.listRowBackground(Color.sgGround)
            }
            .listStyle(.plain).background(Color.sgGround)
            .navigationTitle("Hücre \(slot + 1) — Kanal")
            #if !os(tvOS)
            .searchable(text: $query, prompt: "Kanal ara")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } } }
            #endif
        }
    }
}

/// sheet(item:) için hafif sarmalayıcı (Int Identifiable değil).
private struct IndexBox: Identifiable { let id: Int }
