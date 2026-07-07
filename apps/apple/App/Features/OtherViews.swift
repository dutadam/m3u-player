import SwiftUI
import Core
import Design

/// Canlı kanallar (kategori grid/list). Faz 1 iskelet.
struct LiveView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?
    private let cols = [GridItem(.adaptive(minimum: 118), spacing: 11)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: cols, spacing: 16) {
                    ForEach(library.live) { ch in
                        ChannelCard(channel: ch).onTapGesture { selected = ch }
                    }
                }.padding(SGMetric.gutter)
            }
            .background(Color.sgGround)
            .navigationTitle("Canlı")
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
        }
    }
}

/// Arama. Faz 1 iskelet — tüm kanallarda isim araması.
struct SearchView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var query = ""
    @State private var selected: Channel?

    private var results: [Channel] {
        guard !query.isEmpty else { return [] }
        return library.channels.filter { $0.name.localizedCaseInsensitiveContains(query) }.prefix(60).map { $0 }
    }

    var body: some View {
        NavigationStack {
            List(results) { ch in
                Button { selected = ch } label: {
                    VStack(alignment: .leading) {
                        Text(ch.name).foregroundStyle(.sgText)
                        Text(ch.group).font(.caption).foregroundStyle(.sgMute)
                    }
                }.listRowBackground(Color.sgGround)
            }
            .listStyle(.plain)
            .background(Color.sgGround)
            .navigationTitle("Ara")
            .searchable(text: $query, prompt: "Kanal, film, dizi ara")
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
        }
    }
}

/// Kitaplık — favoriler / son izlenenler / çoklu ekran girişi. Faz 1 iskelet.
struct LibraryView: View {
    var body: some View {
        NavigationStack {
            List {
                NavigationLink { SeriesListView() } label: { Label("Diziler", systemImage: "play.tv.fill") }
                NavigationLink { MultiView() } label: { Label("Çoklu Ekran", systemImage: "square.grid.2x2.fill") }
                Label("Favoriler", systemImage: "heart.fill")
                Label("Son İzlenenler", systemImage: "clock.arrow.circlepath")
                Label("Ayarlar", systemImage: "gearshape.fill")
            }
            .listStyle(.plain)
            .navigationTitle("Kitaplık")
        }
    }
}

/// Çoklu ekran (2–4 yayın). Tasarım: docs/design/ui-preview.html §Çoklu Ekran. Faz 2 tam entegrasyon.
struct MultiView: View {
    @EnvironmentObject private var library: LibraryStore
    private let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: cols, spacing: 8) {
            ForEach(library.live.prefix(4)) { ch in
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 14).fill(Color.sgElevated).aspectRatio(16/9, contentMode: .fit)
                    Text(ch.name).font(.system(size: 10, weight: .bold))
                        .padding(4).background(.black.opacity(0.5), in: RoundedRectangle(cornerRadius: 6))
                        .padding(7)
                }
            }
        }
        .padding(8)
        .navigationTitle("Çoklu Ekran")
    }
}
