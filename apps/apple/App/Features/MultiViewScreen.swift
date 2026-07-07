import SwiftUI
import AVFoundation
import Core
import Design

/// Çoklu ekran — 4 canlı yayın aynı anda. Tasarım: docs/design/ui-preview.html §Çoklu Ekran.
/// Dokunulan hücrenin sesi aktif olur (diğerleri sessiz). Spor merkezi farklılaşması.
struct MultiView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var slots: [Channel?] = Array(repeating: nil, count: 4)
    @State private var players: [AVPlayer] = (0..<4).map { _ in AVPlayer() }
    @State private var activeIndex = 0
    @State private var pickerFor: Int?     // hangi hücreye kanal seçiliyor

    private let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

    var body: some View {
        VStack(spacing: 8) {
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(0..<4, id: \.self) { i in cell(i) }
            }
        }
        .padding(8)
        .background(Color.sgGround)
        .navigationTitle("Çoklu Ekran")
        .onAppear(perform: setupIfNeeded)
        .onDisappear { players.forEach { $0.pause() } }
        .sheet(item: Binding(get: { pickerFor.map { IndexBox(id: $0) } },
                             set: { pickerFor = $0?.id })) { box in
            channelPicker(for: box.id)
        }
    }

    // MARK: - Hücre
    private func cell(_ i: Int) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14).fill(Color.sgElevated)
            if slots[i] != nil {
                PlayerLayerView(player: players[i])
            } else {
                Image(systemName: "plus.circle").font(.title).foregroundStyle(Color.sgMute)
            }
            // üst şerit: kanal adı + ses durumu
            VStack {
                HStack {
                    if let ch = slots[i] {
                        Text(ch.name).font(.system(size: 10, weight: .bold)).lineLimit(1)
                            .padding(.horizontal, 6).padding(.vertical, 3)
                            .background(.black.opacity(0.5), in: RoundedRectangle(cornerRadius: 6))
                    }
                    Spacer()
                    Image(systemName: i == activeIndex ? "speaker.wave.2.fill" : "speaker.slash.fill")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(i == activeIndex ? Color.sgAccent : .white.opacity(0.6))
                        .padding(5).background(.black.opacity(0.5), in: Circle())
                }
                Spacer()
            }
            .padding(7)
        }
        .aspectRatio(16/9, contentMode: .fit)
        .overlay(RoundedRectangle(cornerRadius: 14)
            .strokeBorder(i == activeIndex ? Color.sgAccent : .clear, lineWidth: 2))
        .contentShape(Rectangle())
        .onTapGesture { setActive(i) }
        .onLongPressGesture { pickerFor = i }
    }

    // MARK: - Kanal seçici
    private func channelPicker(for slot: Int) -> some View {
        NavigationStack {
            List(library.live) { ch in
                Button { assign(ch, to: slot); pickerFor = nil } label: {
                    Text(ch.name).foregroundStyle(Color.sgText)
                }.listRowBackground(Color.sgGround)
            }
            .listStyle(.plain).background(Color.sgGround)
            .navigationTitle("Hücre \(slot + 1) — Kanal Seç")
        }
    }

    // MARK: - Mantık
    private func setupIfNeeded() {
        guard slots.allSatisfy({ $0 == nil }) else { return }
        for (i, ch) in library.live.prefix(4).enumerated() { assign(ch, to: i) }
        setActive(0)
    }

    private func assign(_ ch: Channel, to slot: Int) {
        slots[slot] = ch
        let url = StreamResolver.candidates(for: ch.url).first?.url ?? ch.url
        players[slot].replaceCurrentItem(with: AVPlayerItem(url: url))
        players[slot].isMuted = (slot != activeIndex)
        players[slot].play()
    }

    private func setActive(_ i: Int) {
        activeIndex = i
        for (idx, p) in players.enumerated() { p.isMuted = (idx != i) }
    }
}

/// sheet(item:) için hafif sarmalayıcı (Int Identifiable değil).
private struct IndexBox: Identifiable { let id: Int }
