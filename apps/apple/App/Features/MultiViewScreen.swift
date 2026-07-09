import SwiftUI
import AVFoundation
import Core
import Design

/// Çoklu ekran — 2×1 / 2×2 / 2×6 responsive grid (mobil · iPad · Mac · TV).
/// Her slota istenen kanal atanır; dokunulan hücrenin sesi aktif olur (diğerleri sessiz).
/// Spor merkezi farklılaşması. Not: AVPlayer motoru (HLS/MP4); TS-only içerik için tek-oynatıcı önerilir.
struct MultiView: View {
    @EnvironmentObject private var library: LibraryStore

    /// Slot düzeni. Ham değer sütun×satır etiketidir; `count` toplam hücre.
    enum Layout: String, CaseIterable, Identifiable {
        case duo = "2×1", quad = "2×2", mega = "2×6"
        var id: String { rawValue }
        var count: Int { switch self { case .duo: 2; case .quad: 4; case .mega: 12 } }
        var icon: String {
            switch self {
            case .duo: "rectangle.split.2x1"
            case .quad: "square.grid.2x2"
            case .mega: "square.grid.3x3"
            }
        }
        /// Genişliğe göre sütun sayısı (responsive).
        func columns(wide: Bool) -> Int {
            switch self {
            case .duo:  return wide ? 2 : 1
            case .quad: return 2
            case .mega: return wide ? 4 : 2
            }
        }
    }

    private static let poolSize = 12
    @State private var layout: Layout = .quad
    @State private var slots: [Channel?] = Array(repeating: nil, count: MultiView.poolSize)
    @State private var players: [AVPlayer] = (0..<MultiView.poolSize).map { _ in AVPlayer() }
    @State private var activeIndex = 0
    @State private var pickerFor: Int?

    var body: some View {
        GeometryReader { geo in
            let wide = geo.size.width > 700
            let cols = Array(repeating: GridItem(.flexible(), spacing: 8),
                             count: layout.columns(wide: wide))
            ScrollView {
                LazyVGrid(columns: cols, spacing: 8) {
                    ForEach(0..<layout.count, id: \.self) { i in cell(i) }
                }
                .padding(8)
            }
        }
        .background(Color.sgGround)
        .navigationTitle("Çoklu Ekran")
        .toolbar {
            ToolbarItem(placement: .principal) { layoutPicker }
        }
        .onAppear { AudioSessionManager.activatePlayback(); setupIfNeeded() }
        .onDisappear { players.forEach { $0.pause() } }
        .sheet(item: Binding(get: { pickerFor.map { IndexBox(id: $0) } },
                             set: { pickerFor = $0?.id })) { box in
            MultiChannelPicker(slot: box.id) { assign($0, to: box.id); pickerFor = nil }
        }
    }

    private var layoutPicker: some View {
        Picker("Düzen", selection: Binding(get: { layout }, set: { changeLayout($0) })) {
            ForEach(Layout.allCases) { l in
                Text(l.rawValue).tag(l)
            }
        }
        .pickerStyle(.segmented)
        .frame(maxWidth: 240)
    }

    // MARK: - Hücre
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
            Button { pickerFor = i } label: { Label("Kanal Değiştir", systemImage: "arrow.left.arrow.right") }
            if slots[i] != nil {
                Button(role: .destructive) { clear(i) } label: { Label("Kaldır", systemImage: "xmark") }
            }
        }
    }

    // MARK: - Mantık
    private func setupIfNeeded() {
        guard slots.allSatisfy({ $0 == nil }) else { return }
        for (i, ch) in library.visibleLive.prefix(layout.count).enumerated() { assign(ch, to: i) }
        setActive(0)
    }

    private func changeLayout(_ l: Layout) {
        layout = l
        if activeIndex >= l.count { activeIndex = 0 }
        // Görünür olmayan slotları duraklat (bant genişliği), görünür + dolu olanları oynat.
        for i in 0..<players.count {
            if i >= l.count { players[i].pause() }
            else if slots[i] != nil { players[i].isMuted = (i != activeIndex); players[i].play() }
        }
    }

    private func assign(_ ch: Channel, to slot: Int) {
        slots[slot] = ch
        let url = StreamResolver.candidates(for: ch.url).first?.url ?? ch.url
        players[slot].replaceCurrentItem(with: AVPlayerItem(url: url))
        players[slot].isMuted = (slot != activeIndex)
        players[slot].play()
    }

    private func clear(_ slot: Int) {
        players[slot].pause()
        players[slot].replaceCurrentItem(with: nil)
        slots[slot] = nil
    }

    private func setActive(_ i: Int) {
        activeIndex = i
        for (idx, p) in players.enumerated() { p.isMuted = (idx != i) }
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
