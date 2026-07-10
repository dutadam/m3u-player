import SwiftUI
import Core
import Design

/// Favoriler listesi — dokun-oynat, kaydırarak favoriden çıkar.
struct FavoritesView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?

    var body: some View {
        Group {
            if library.favoriteChannels.isEmpty {
                emptyState("Henüz favori yok", "Bir yayını izlerken kalp simgesine dokun.")
            } else {
                List {
                    ForEach(library.favoriteChannels) { ch in
                        Button { selected = ch } label: { channelRow(ch) }
                            .listRowBackground(Color.sgGround)
                    }
                    .onDelete { idx in
                        idx.map { library.favoriteChannels[$0] }.forEach { library.toggleFavorite($0) }
                    }
                }
                .listStyle(.plain).background(Color.sgGround)
            }
        }
        .navigationTitle("Favoriler")
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
    }
}

/// Son izlenenler — RecentItem'dan doğrudan Channel üretip oynatır (kaynak değişse de çalışır).
struct RecentsView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?

    var body: some View {
        Group {
            if library.recents.isEmpty {
                emptyState("Son izlenen yok", "İzlediğin yayınlar burada görünür.")
            } else {
                List {
                    ForEach(library.recents) { item in
                        Button { selected = channel(from: item) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name).foregroundStyle(Color.sgText).lineLimit(1)
                                Text(item.group).font(.caption).foregroundStyle(Color.sgMute)
                            }
                        }.listRowBackground(Color.sgGround)
                    }
                }
                .listStyle(.plain).background(Color.sgGround)
                .toolbar { Button("Temizle") { library.clearRecents() }.tint(Color.sgAccent) }
            }
        }
        .navigationTitle("Son İzlenenler")
        .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
    }

    private func channel(from item: RecentItem) -> Channel {
        Channel(id: item.url, name: item.name, logo: item.logo.flatMap { URL(string: $0) },
                group: item.group, url: URL(string: item.url) ?? URL(string: "about:blank")!, kind: .live)
    }
}

/// Ayarlar — EPG kaynağı, kaynak/çıkış, depolama, hakkında.
struct SettingsView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var epgURL = ""
    @State private var userAgent = ""
    @State private var showSignOut = false
    @State private var cacheCleared = false
    @State private var autoRefresh = AppSettings.autoRefresh

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }

    var body: some View {
        Form {
            Section {
                Toggle("Açılışta otomatik yenile", isOn: $autoRefresh)
                    .onChange(of: autoRefresh) { AppSettings.autoRefresh = $0 }
                Button {
                    Task { await library.refresh() }
                } label: {
                    HStack {
                        Label("İçeriği Şimdi Yenile", systemImage: "arrow.clockwise")
                        if library.isRefreshing { Spacer(); ProgressView() }
                    }
                }.disabled(library.isRefreshing)
            } header: { Text("İçerik") } footer: {
                if let d = library.lastUpdated {
                    Text("Son güncelleme: \(d.formatted(date: .abbreviated, time: .shortened))")
                }
            }

            Section("EPG (TV Rehberi)") {
                TextField("XMLTV URL (opsiyonel)", text: $epgURL)
                    #if !os(tvOS)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    #endif
                Button("Rehberi Yükle") { Task { await library.setManualEPG(epgURL) } }
                    .disabled(epgURL.isEmpty)
            }

            Section {
                TextField("User-Agent (opsiyonel)", text: $userAgent)
                    #if !os(tvOS)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    #endif
                Button("User-Agent'ı Uygula") { Task { await library.applyUserAgent(userAgent) } }
                    .disabled(userAgent == library.userAgent)
            } header: { Text("Bağlantı") } footer: {
                Text("Bazı IPTV panelleri özel User-Agent ister (örn. VLC/…). Boş bırakırsan varsayılan kullanılır.")
            }

            Section("Kaynak") {
                NavigationLink { PlaylistsView() } label: {
                    Label("Kaynakları Yönet", systemImage: "square.stack.3d.up.fill")
                }
                Button(role: .destructive) { showSignOut = true } label: {
                    Label("Tüm Kaynakları Sıfırla", systemImage: "trash")
                }
            }

            if !library.reminders.isEmpty {
                Section("Hatırlatıcılar (\(library.reminders.count))") {
                    ForEach(library.reminders.values.sorted { $0.start < $1.start }) { r in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.programTitle).foregroundStyle(Color.sgText).lineLimit(1)
                            Text("\(r.channelName) · \(r.start.formatted(date: .abbreviated, time: .shortened))")
                                .font(.caption).foregroundStyle(Color.sgMute)
                        }
                    }
                    Button(role: .destructive) { library.clearReminders() } label: { Text("Tümünü Temizle") }
                }
            }

            Section("Depolama") {
                Button("Favorileri Temizle") { library.clearFavorites() }
                Button("Son İzlenenleri Temizle") { library.clearRecents() }
                Button(cacheCleared ? "Önbellek Temizlendi ✓" : "Önbelleği Temizle") {
                    library.clearCache(); cacheCleared = true
                }.disabled(cacheCleared)
            }

            Section("Hakkında") {
                LabeledContent("Sürüm", value: appVersion)
                LabeledContent("Gizlilik", value: "Veri toplanmaz")
                Text("cheesino nötr bir medya oynatıcıdır; içerik içermez. Kendi kaynağını getirirsin.")
                    .font(.caption).foregroundStyle(Color.sgDim)
            }
        }
        .navigationTitle("Ayarlar")
        .onAppear { epgURL = library.manualEPGURL; userAgent = library.userAgent; library.pruneReminders() }
        .confirmationDialog("Tüm kaynaklar ve kimlik bilgileri silinsin mi?",
                            isPresented: $showSignOut, titleVisibility: .visible) {
            Button("Tümünü Sıfırla", role: .destructive) { library.signOut() }
            Button("Vazgeç", role: .cancel) {}
        }
    }
}

/// Kaynaklar — kayıtlı playlist'ler; geçiş, ekle, yeniden adlandır, sil.
struct PlaylistsView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var showAdd = false
    @State private var renaming: PlaylistMeta?
    @State private var newName = ""

    var body: some View {
        List {
            Section {
                if library.playlists.isEmpty {
                    Text("Kayıtlı kaynak yok").foregroundStyle(Color.sgMute)
                }
                ForEach(library.playlists) { pl in
                    Button { Task { await library.switchTo(pl.id) } } label: {
                        HStack(spacing: 12) {
                            Image(systemName: pl.kind == .xtream ? "server.rack" : "list.bullet.rectangle")
                                .foregroundStyle(Color.sgAccent2).frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(pl.name).foregroundStyle(Color.sgText).lineLimit(1)
                                Text(pl.subtitle).font(.caption).foregroundStyle(Color.sgMute).lineLimit(1)
                            }
                            Spacer()
                            if pl.id == library.activePlaylistId {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.sgAccent)
                            }
                        }
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { Task { await library.removePlaylist(pl.id) } } label: {
                            Label("Sil", systemImage: "trash")
                        }
                        Button { renaming = pl; newName = pl.name } label: { Label("Ad", systemImage: "pencil") }.tint(.gray)
                    }
                }
            }
            Section {
                Button { showAdd = true } label: { Label("Kaynak Ekle", systemImage: "plus.circle.fill") }
            }
        }
        .navigationTitle("Kaynaklar")
        .onChange(of: library.playlists.count) { _ in showAdd = false }   // ekleme başarılı → kapat
        .sheet(isPresented: $showAdd) {
            NavigationStack {
                OnboardingView()
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Kapat") { showAdd = false } } }
            }
        }
        .alert("Yeniden Adlandır", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Ad", text: $newName)
            Button("Kaydet") { if let r = renaming { library.renamePlaylist(r.id, name: newName) }; renaming = nil }
            Button("Vazgeç", role: .cancel) { renaming = nil }
        }
    }
}

// MARK: - Ortak yardımcılar
private func channelRow(_ ch: Channel) -> some View {
    HStack(spacing: 12) {
        Text(String(ch.name.prefix(2)).uppercased())
            .font(.system(size: 11, weight: .heavy))
            .frame(width: 30, height: 30)
            .background(Color.sgElevated, in: RoundedRectangle(cornerRadius: 7))
        VStack(alignment: .leading, spacing: 2) {
            Text(ch.name).foregroundStyle(Color.sgText).lineLimit(1)
            Text(ch.group).font(.caption).foregroundStyle(Color.sgMute)
        }
        Spacer()
    }
}

@ViewBuilder
private func emptyState(_ title: String, _ subtitle: String) -> some View {
    VStack(spacing: 8) {
        Image(systemName: "tray").font(.largeTitle).foregroundStyle(Color.sgMute)
        Text(title).font(.headline).foregroundStyle(Color.sgText)
        Text(subtitle).font(.caption).foregroundStyle(Color.sgDim).multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.sgGround)
}
