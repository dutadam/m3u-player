import SwiftUI
import Core
import Design

/// Kategori yönetimi — Xtream/M3U'dan gelen kategorileri gizle/göster. Gizlenenler tüm listelerde
/// (Canlı/Filmler/Diziler/Ana Sayfa) saklanır. Tercih kalıcı + iCloud senkronludur.
struct CategoryManagerView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var query = ""

    private var cats: [String] {
        query.isEmpty ? library.allCategories
                      : library.allCategories.filter { $0.localizedCaseInsensitiveContains(query) }
    }
    private var hiddenCount: Int { library.hiddenCategories.count }

    var body: some View {
        List {
            Section {
                Text("Gizlenen kategoriler tüm listelerde saklanır. Kategori çipine uzun basarak da gizleyebilirsin.")
                    .font(.caption).foregroundStyle(Color.sgDim)
                if hiddenCount > 0 {
                    Button("Tümünü Göster (\(hiddenCount) gizli)") {
                        for c in Array(library.hiddenCategories) { library.toggleCategoryHidden(c) }
                    }.tint(Color.sgAccent)
                }
            }
            Section("Kategoriler (\(cats.count))") {
                ForEach(cats, id: \.self) { cat in
                    Button { library.toggleCategoryHidden(cat) } label: {
                        HStack {
                            Text(cat).foregroundStyle(library.isCategoryHidden(cat) ? Color.sgMute : Color.sgText)
                            Spacer()
                            Image(systemName: library.isCategoryHidden(cat) ? "eye.slash.fill" : "eye")
                                .foregroundStyle(library.isCategoryHidden(cat) ? Color.sgMute : Color.sgAccent)
                        }
                    }
                }
            }
        }
        .navigationTitle("Kategori Yönetimi")
        #if !os(tvOS)
        .searchable(text: $query, prompt: "Kategori ara")
        #endif
    }
}
