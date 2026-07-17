import SwiftUI
import Core
import Design

/// Kategori-bazlı gözatma — kategori çipleri + filtreli grid. Binlerce kanallı gerçek
/// IPTV listeleri için ölçeklenir (düz tek-grid yerine). Canlı ve VOD için ortak kullanılır.
struct CategoryBrowseView: View {
    @EnvironmentObject private var library: LibraryStore
    let title: String
    let channels: [Channel]
    var poster: Bool = false                      // true → 2:3 poster kartları (Filmler)
    @State private var category: String?          // nil = Tümü (canlı)
    @State private var genre: String?             // nil = Tüm türler (film)
    @State private var query = ""
    @State private var selected: Channel?

    private var cols: [GridItem] {
        [GridItem(.adaptive(minimum: poster ? 112 : 118), spacing: 11)]
    }

    // Gizlenen kategoriler hariç
    private var visible: [Channel] { channels.filter { !library.isCategoryHidden($0.group) } }
    private var categories: [String] {
        Array(Set(visible.map(\.group))).sorted()
    }
    /// Film modunda mevcut türler (metadata/çıkarım).
    private var genreChips: [String] {
        var s = Set<String>()
        for ch in visible { s.formUnion(library.genres(for: ch)) }
        return s.sorted()
    }
    private var filtered: [Channel] {
        var list = visible
        if poster {
            if let genre { list = list.filter { library.genres(for: $0).contains(genre) } }
        } else if let category {
            list = list.filter { $0.group == category }
        }
        if !query.isEmpty { list = list.filter { $0.name.localizedCaseInsensitiveContains(query) } }
        return list
    }

    var body: some View {
        VStack(spacing: 0) {
            // Filtre çipleri — film modunda TÜR, canlı modunda KATEGORİ
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if poster {
                        chip("Tüm Türler", on: genre == nil) { genre = nil }
                        ForEach(genreChips, id: \.self) { g in chip(g, on: genre == g) { genre = g } }
                    } else {
                        chip("Tümü", on: category == nil) { category = nil }
                        ForEach(categories, id: \.self) { c in
                            chip(c, on: category == c) { category = c }
                                .contextMenu {
                                    Button(role: .destructive) { library.toggleCategoryHidden(c) } label: {
                                        Label("Kategoriyi Gizle", systemImage: "eye.slash")
                                    }
                                }
                        }
                    }
                }
                .padding(.horizontal, SGMetric.gutter).padding(.vertical, 10)
            }
            Divider().overlay(Color.sgLineSoft)

            // Grid / boş durum
            if filtered.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "tray").font(.largeTitle).foregroundStyle(Color.sgMute)
                    Text(channels.isEmpty ? "Bu bölümde içerik yok" : "Sonuç bulunamadı")
                        .font(.subheadline).foregroundStyle(Color.sgDim)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: cols, spacing: 16) {
                        ForEach(filtered.prefix(400)) { ch in
                            if ch.kind == .vod {
                                // Filmler → sinematik detay ekranı (poster/özet/TMDB)
                                NavigationLink(value: ch) { card(ch) }
                                    .buttonStyle(PressableStyle())
                            } else {
                                Button { selected = ch } label: { card(ch) }
                                    .buttonStyle(PressableStyle())
                            }
                        }
                    }
                    .padding(SGMetric.gutter)
                }
            }
        }
        .background(Color.sgGround)
        .navigationTitle(title)
        #if !os(tvOS)
        .searchable(text: $query, prompt: "\(title) içinde ara")
        #endif
        .navigationDestination(for: Channel.self) { MovieDetailView(channel: $0) }
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
    }

    @ViewBuilder private func card(_ ch: Channel) -> some View {
        if poster {
            PosterCard(title: ch.name, poster: ch.logo,
                       watched: library.isWatched(ch.url.absoluteString),
                       progress: library.watchFraction(for: ch.url.absoluteString))
        } else {
            ChannelCard(channel: ch)
        }
    }

    private func chip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(on ? Color.white : Color.sgDim)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(on ? Color.sgAccent : Color.sgSurface, in: Capsule())
                .lineLimit(1)
        }.buttonStyle(.plain)
    }
}
