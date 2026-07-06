import SwiftUI
import Core
import Design

/// EPG rehberi (zaman çizelgeli). Tasarım: docs/design/ui-preview.html §Otomatik EPG.
/// Faz 1 iskelet: kanal başına şimdi/sıradaki. Tam zaman-çizelgeli grid bir sonraki adımda.
struct GuideView: View {
    @EnvironmentObject private var library: LibraryStore

    var body: some View {
        NavigationStack {
            List(library.live) { ch in
                HStack(spacing: 12) {
                    Text(String(ch.name.prefix(2)).uppercased())
                        .font(.system(size: 11, weight: .heavy))
                        .frame(width: 30, height: 30)
                        .background(Color.sgElevated, in: RoundedRectangle(cornerRadius: 7))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ch.name).font(.system(size: 14, weight: .semibold))
                        if let now = library.epg?.nowPlaying(for: ch) {
                            Text("Şimdi: \(now.title)").font(.caption).foregroundStyle(.sgAccent2)
                        }
                        if let next = library.epg?.upNext(for: ch) {
                            Text("Sıradaki: \(next.title)").font(.caption2).foregroundStyle(.sgMute)
                        }
                    }
                    Spacer()
                }
                .listRowBackground(Color.sgGround)
            }
            .listStyle(.plain)
            .background(Color.sgGround)
            .navigationTitle("Rehber")
        }
    }
}
