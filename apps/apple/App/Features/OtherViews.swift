import SwiftUI
import Core
import Design

/// Canlı kanallar — kategori-bazlı gözatma (binlerce kanal için ölçeklenir).
struct LiveView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        NavigationStack {
            CategoryBrowseView(title: "Canlı", channels: library.live)
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
                        Text(ch.name).foregroundStyle(Color.sgText)
                        Text(ch.group).font(.caption).foregroundStyle(Color.sgMute)
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

/// Kitaplık — favoriler / son izlenenler / filmler / diziler / çoklu ekran / ayarlar.
struct LibraryView: View {
    @EnvironmentObject private var library: LibraryStore
    var body: some View {
        NavigationStack {
            List {
                NavigationLink { FavoritesView() } label: { Label("Favoriler", systemImage: "heart.fill") }
                NavigationLink { RecentsView() } label: { Label("Son İzlenenler", systemImage: "clock.arrow.circlepath") }
                if !library.movies.isEmpty {
                    NavigationLink { CategoryBrowseView(title: "Filmler", channels: library.movies) } label: {
                        Label("Filmler", systemImage: "film.fill")
                    }
                }
                NavigationLink { SeriesListView() } label: { Label("Diziler", systemImage: "play.tv.fill") }
                NavigationLink { MultiView() } label: { Label("Çoklu Ekran", systemImage: "square.grid.2x2.fill") }
                NavigationLink { SettingsView() } label: { Label("Ayarlar", systemImage: "gearshape.fill") }
            }
            .listStyle(.plain)
            .navigationTitle("Kitaplık")
        }
    }
}

// MultiView → MultiViewScreen.swift (gerçek 4-yayın oynatma)
