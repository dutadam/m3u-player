import SwiftUI
import Core
import Design

/// Kategori-bazlı gözatma — kategori çipleri + filtreli grid. Binlerce kanallı gerçek
/// IPTV listeleri için ölçeklenir (düz tek-grid yerine). Canlı ve VOD için ortak kullanılır.
struct CategoryBrowseView: View {
    @EnvironmentObject private var library: LibraryStore
    let title: String
    let channels: [Channel]
    @State private var category: String?          // nil = Tümü
    @State private var query = ""
    @State private var selected: Channel?

    private let cols = [GridItem(.adaptive(minimum: 118), spacing: 11)]

    // Gizlenen kategoriler hariç
    private var visible: [Channel] { channels.filter { !library.isCategoryHidden($0.group) } }
    private var categories: [String] {
        Array(Set(visible.map(\.group))).sorted()
    }
    private var filtered: [Channel] {
        var list = category == nil ? visible : visible.filter { $0.group == category }
        if !query.isEmpty { list = list.filter { $0.name.localizedCaseInsensitiveContains(query) } }
        return list
    }

    var body: some View {
        VStack(spacing: 0) {
            // Kategori çipleri
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
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
                            ChannelCard(channel: ch).onTapGesture { selected = ch }
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
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
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
