import SwiftUI
import Core
import Design

/// Ana ekran — sinematik hero + yatay raylar. Tasarım: docs/design/ui-preview.html §01.
struct HomeView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if let hero = library.live.first { HeroCard(channel: hero) { selected = hero } }

                    // "Devam Et" — son izlenenler (en üstte)
                    if !library.recentChannels.isEmpty {
                        ChannelRail(title: "Devam Et", channels: library.recentChannels) { selected = $0 }
                    }
                    // "Favoriler"
                    if !library.favoriteChannels.isEmpty {
                        ChannelRail(title: "Favoriler", channels: library.favoriteChannels) { selected = $0 }
                    }

                    // "Diziler" — poster rayı, detaya gider
                    if !library.series.isEmpty { SeriesRailHome(series: Array(library.series.prefix(20))) }

                    // "Canlı · Spor" rayı — grup adında spor geçenler
                    let sports = library.live.filter { $0.group.localizedCaseInsensitiveContains("spor") }
                    if !sports.isEmpty { ChannelRail(title: "Canlı · Spor", channels: sports) { selected = $0 } }

                    // Diğer gruplar
                    ForEach(library.groups.keys.sorted().prefix(6), id: \.self) { group in
                        ChannelRail(title: group, channels: library.groups[group] ?? []) { selected = $0 }
                    }
                }
                .padding(.vertical, 12)
            }
            .background(Color.sgGround)
            .navigationTitle("İzle")
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
        }
    }
}

/// Sinematik hero kartı.
struct HeroCard: View {
    let channel: Channel
    var onPlay: () -> Void

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [Color(hex: 0x20304F), Color(hex: 0x3A2340), Color(hex: 0x101521)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            LinearGradient(colors: [.clear, .sgGround.opacity(0.96)], startPoint: .center, endPoint: .bottom)

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    LivePill()
                    if let q = channel.quality { QualityBadge(q.rawValue) }
                }
                Text(channel.name).font(.system(size: 22, weight: .heavy)).foregroundStyle(.white)
                Text(channel.group).font(.subheadline).foregroundStyle(Color.sgDim)
                Button(action: onPlay) {
                    Label("İzle", systemImage: "play.fill")
                        .font(.system(size: 14, weight: .bold))
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(.white, in: RoundedRectangle(cornerRadius: 10))
                        .foregroundStyle(Color.sgGround)
                }
                .buttonStyle(.plain)
                .padding(.top, 6)
            }
            .padding(16)
        }
        .frame(height: 230)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, SGMetric.gutter)
    }
}

/// Yatay dizi rayı — poster kartları, detaya gider.
struct SeriesRailHome: View {
    let series: [SeriesRef]
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Diziler").font(.headline).foregroundStyle(Color.sgText)
                .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(series) { s in
                        NavigationLink { SeriesDetailView(ref: s) } label: {
                            SeriesPoster(ref: s).frame(width: 110)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Yatay kanal rayı.
struct ChannelRail: View {
    let title: String
    let channels: [Channel]
    var onTap: (Channel) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title).font(.headline).foregroundStyle(Color.sgText)
                .padding(.horizontal, SGMetric.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(channels.prefix(20)) { ch in
                        ChannelCard(channel: ch).onTapGesture { onTap(ch) }
                    }
                }
                .padding(.horizontal, SGMetric.gutter)
            }
        }
    }
}

/// Kanal kartı (logo + isim + kalite).
struct ChannelCard: View {
    let channel: Channel
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 11)
                    .fill(LinearGradient(colors: [Color(hex: 0x26304A), Color(hex: 0x171B28)],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                AsyncImage(url: channel.logo) { img in
                    img.resizable().scaledToFit().padding(10)
                } placeholder: {
                    Text(String(channel.name.prefix(2)).uppercased())
                        .font(.system(size: 15, weight: .heavy)).foregroundStyle(.white.opacity(0.9))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if let q = channel.quality {
                    QualityBadge(q.rawValue).padding(6)
                }
            }
            .frame(width: 118, height: 70)
            Text(channel.name).font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.sgText).lineLimit(1).frame(width: 118, alignment: .leading)
        }
    }
}
