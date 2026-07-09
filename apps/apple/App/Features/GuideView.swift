import SwiftUI
import Core
import Design

/// EPG rehberi — sanallaştırılmış kanal listesi (şimdi/sıradaki program). Binlerce kanallı
/// gerçek listeler için ölçeklenir; eski yatay-timeline grid tüm satırları aynı anda çizip
/// çöküyordu (msg too large). Kanala dokun → oynat.
struct GuideView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?
    @State private var query = ""

    private var channels: [Channel] {
        let base = library.visibleLive
        guard !query.isEmpty else { return base }
        return base.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    // NavigationStack'sız — çağıran (Canlı sekmesi) push eder.
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if channels.isEmpty {
                    Text("Kanal bulunamadı").font(.subheadline)
                        .foregroundStyle(Color.sgDim).padding(.top, 40)
                } else {
                    ForEach(channels) { ch in
                        Button { selected = ch } label: { GuideRow(channel: ch) }
                            .buttonStyle(PressableStyle())
                        Divider().overlay(Color.sgLineSoft)
                    }
                }
            }
            .padding(.top, 4)
        }
        .background(Color.sgGround)
        .navigationTitle("Rehber")
        #if !os(tvOS)
        .searchable(text: $query, prompt: "Kanal ara")
        #endif
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
    }
}

/// Rehber satırı — logo + ad + şimdi/sıradaki program + ilerleme.
private struct GuideRow: View {
    @EnvironmentObject private var library: LibraryStore
    let channel: Channel

    var body: some View {
        let entries = library.epg?.entries(for: channel) ?? []
        let now = entries.first { $0.isLiveNow }
        let next = entries.first { $0.start > Date() }
        return HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 9).fill(Color.sgElevated)
                AsyncImage(url: channel.logo) { $0.resizable().scaledToFit().padding(6) } placeholder: {
                    Text(String(channel.name.prefix(2)).uppercased())
                        .font(.system(size: 12, weight: .heavy)).foregroundStyle(.white.opacity(0.85))
                }
            }
            .frame(width: 54, height: 40).clipShape(RoundedRectangle(cornerRadius: 9))

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(channel.name).font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.sgText).lineLimit(1)
                    if let q = channel.quality { QualityBadge(q.rawValue) }
                }
                if let now {
                    Text(now.title).font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.sgAccent2).lineLimit(1)
                    ProgressBarLine(fraction: fraction(now)).frame(maxWidth: 240)
                    if let next {
                        Text("Sıradaki · \(next.title)").font(.system(size: 10))
                            .foregroundStyle(Color.sgMute).lineLimit(1)
                    }
                } else {
                    Text(channel.group).font(.system(size: 11)).foregroundStyle(Color.sgMute).lineLimit(1)
                }
            }
            Spacer(minLength: 4)
            Image(systemName: "play.circle.fill").font(.system(size: 22)).foregroundStyle(Color.sgAccent)
        }
        .padding(.horizontal, SGMetric.gutter).padding(.vertical, 9)
        .contentShape(Rectangle())
    }

    private func fraction(_ e: EpgEntry) -> Double {
        let total = e.stop.timeIntervalSince(e.start)
        guard total > 0 else { return 0 }
        return min(1, max(0, Date().timeIntervalSince(e.start) / total))
    }
}
