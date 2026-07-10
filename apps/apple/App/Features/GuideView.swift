import SwiftUI
import Core
import Design

/// EPG rehberi — sanallaştırılmış kanal listesi (şimdi/sıradaki program). Binlerce kanallı
/// gerçek listeler için ölçeklenir. Kanala dokun → oynat; catchup destekli kanalda saat
/// ikonu → geçmiş programlar (timeshift).
struct GuideView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?
    @State private var programsFor: Channel?
    @State private var query = ""

    private var channels: [Channel] {
        let base = library.visibleLive
        guard !query.isEmpty else { return base }
        return base.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    /// Kategoriye göre bölümlenmiş (sıra korunur).
    private var sections: [(name: String, channels: [Channel])] {
        var order: [String] = []; var map: [String: [Channel]] = [:]
        for ch in channels {
            if map[ch.group] == nil { order.append(ch.group) }
            map[ch.group, default: []].append(ch)
        }
        return order.map { ($0, map[$0] ?? []) }
    }

    // NavigationStack'sız — çağıran (Canlı sekmesi) push eder.
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                if channels.isEmpty {
                    Text("Kanal bulunamadı").font(.subheadline)
                        .foregroundStyle(Color.sgDim).padding(.top, 40)
                } else {
                    ForEach(sections, id: \.name) { section in
                        Section {
                            ForEach(section.channels) { ch in
                                GuideRow(channel: ch, onPlay: { selected = ch }, onPrograms: { programsFor = ch })
                                Divider().overlay(Color.sgLineSoft)
                            }
                        } header: {
                            HStack {
                                Text(section.name).font(.system(size: 13, weight: .heavy))
                                    .foregroundStyle(Color.sgAccent2)
                                Spacer()
                                Text("\(section.channels.count)").font(.caption).foregroundStyle(Color.sgMute)
                            }
                            .padding(.horizontal, SGMetric.gutter).padding(.vertical, 7)
                            .background(Color.sgGround)
                        }
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
        .sheet(item: $programsFor) { ChannelProgramsSheet(channel: $0) }
    }
}

/// Rehber satırı — logo + ad + şimdi/sıradaki program + ilerleme; catchup ikonu.
private struct GuideRow: View {
    @EnvironmentObject private var library: LibraryStore
    let channel: Channel
    var onPlay: () -> Void
    var onPrograms: () -> Void

    var body: some View {
        let entries = library.epg?.entries(for: channel) ?? []
        let now = entries.first { $0.isLiveNow }
        let next = entries.first { $0.start > Date() }
        return HStack(spacing: 8) {
            Button(action: onPlay) {
                HStack(spacing: 12) {
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
            }
            .buttonStyle(PressableStyle())

            if !entries.isEmpty {
                Button(action: onPrograms) {
                    Image(systemName: channel.supportsCatchup ? "clock.arrow.circlepath" : "list.bullet.rectangle")
                        .font(.system(size: 17, weight: .semibold)).foregroundStyle(Color.sgAccent2)
                        .frame(width: 40, height: 40)
                        .background(Color.sgSurface, in: Circle())
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, SGMetric.gutter).padding(.vertical, 9)
    }

    private func fraction(_ e: EpgEntry) -> Double {
        let total = e.stop.timeIntervalSince(e.start)
        guard total > 0 else { return 0 }
        return min(1, max(0, Date().timeIntervalSince(e.start) / total))
    }
}

/// Kanalın program listesi — geçmiş (catchup/timeshift), şimdi (canlı), gelecek (hatırlatıcı).
struct ChannelProgramsSheet: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    let channel: Channel
    @State private var selected: Channel?

    private var programs: [EpgEntry] {
        (library.epg?.entries(for: channel) ?? []).sorted { $0.start > $1.start }   // en yeni üstte
    }

    var body: some View {
        NavigationStack {
            Group {
                if programs.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "clock.badge.questionmark").font(.largeTitle).foregroundStyle(Color.sgMute)
                        Text("Bu kanal için program bilgisi yok").font(.subheadline).foregroundStyle(Color.sgDim)
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(programs, id: \.start) { p in row(p) }
                        .listStyle(.plain).scrollContentBackgroundHiddenIfAvailable().background(Color.sgGround)
                }
            }
            .background(Color.sgGround)
            .navigationTitle(channel.name).navigationBarTitleInlineIfAvailable()
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } } }
        }
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
    }

    @ViewBuilder private func row(_ p: EpgEntry) -> some View {
        let now = Date()
        let isNow = p.start <= now && now < p.stop
        let isPast = p.stop <= now
        let isFuture = p.start > now
        let catchup = isPast ? library.catchupChannel(for: channel, program: p) : nil
        let playable = isNow ? channel : catchup

        HStack(spacing: 10) {
            Image(systemName: isNow ? "dot.radiowaves.left.and.right" : (isPast ? "arrow.uturn.backward" : "clock"))
                .font(.system(size: 13)).foregroundStyle(isNow ? Color.sgLive : (playable != nil ? Color.sgAccent2 : Color.sgMute))
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(p.title).font(.system(size: 14, weight: isNow ? .bold : .semibold))
                    .foregroundStyle(playable != nil || isFuture ? Color.sgText : Color.sgMute).lineLimit(1)
                Text("\(Self.hm.string(from: p.start)) – \(Self.hm.string(from: p.stop))" + (isNow ? " · CANLI" : ""))
                    .font(.system(size: 11)).monospacedDigit().foregroundStyle(Color.sgMute)
            }
            Spacer()
            if let playable {
                Button { selected = playable } label: {
                    Image(systemName: "play.circle.fill").font(.system(size: 22)).foregroundStyle(Color.sgAccent)
                }.buttonStyle(.plain)
            } else if isFuture {
                // Hatırlatıcı (yerel bildirim)
                Button {
                    Task { await library.toggleReminder(channel, p) }
                } label: {
                    Image(systemName: library.isReminderSet(channel, p) ? "bell.fill" : "bell")
                        .font(.system(size: 18)).foregroundStyle(library.isReminderSet(channel, p) ? Color.sgAccent : Color.sgMute)
                }.buttonStyle(.plain)
            }
        }
        .padding(.vertical, 3)
        .listRowBackground(Color.sgGround)
    }

    private static let hm: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "d MMM HH:mm"; f.locale = Locale(identifier: "tr_TR"); return f
    }()
}

private extension View {
    @ViewBuilder func scrollContentBackgroundHiddenIfAvailable() -> some View {
        if #available(iOS 16.0, *) { self.scrollContentBackground(.hidden) } else { self }
    }
    @ViewBuilder func navigationBarTitleInlineIfAvailable() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}
