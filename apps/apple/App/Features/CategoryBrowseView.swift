import SwiftUI
import Core
import Design

/// Kategori-bazlı gözatma — kategori çipleri + filtreli grid. Binlerce kanallı gerçek
/// IPTV listeleri için ölçeklenir (düz tek-grid yerine). Canlı ve VOD için ortak kullanılır.
struct CategoryBrowseView: View {
    let title: String
    let channels: [Channel]
    @State private var category: String?          // nil = Tümü
    @State private var query = ""
    @State private var selected: Channel?

    private let cols = [GridItem(.adaptive(minimum: 118), spacing: 11)]

    private var categories: [String] {
        Array(Set(channels.map(\.group))).sorted()
    }
    private var filtered: [Channel] {
        var list = category == nil ? channels : channels.filter { $0.group == category }
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
                    }
                }
                .padding(.horizontal, SGMetric.gutter).padding(.vertical, 10)
            }
            Divider().overlay(Color.sgLineSoft)

            // Grid
            ScrollView {
                LazyVGrid(columns: cols, spacing: 16) {
                    ForEach(filtered.prefix(400)) { ch in
                        ChannelCard(channel: ch).onTapGesture { selected = ch }
                    }
                }
                .padding(SGMetric.gutter)
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
