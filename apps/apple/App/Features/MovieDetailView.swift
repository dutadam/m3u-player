import SwiftUI
import Core
import Design

/// Film (VOD) detayı — backdrop hero + poster + özet + oyuncu/yönetmen + TMDB künyesi.
/// Xtream get_vod_info ile lazy yüklenir; grid'den doğrudan oynatma yerine sinematik detay.
struct MovieDetailView: View {
    let channel: Channel
    @EnvironmentObject private var library: LibraryStore
    @State private var detail: MovieDetail?
    @State private var loading = true
    @State private var playing = false

    /// "vod_1234" → 1234
    private var vodId: Int? {
        Int(channel.id.replacingOccurrences(of: "vod_", with: ""))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                hero
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        playButton
                        LikeDislikeButtons(key: channel.url.absoluteString)
                    }
                    metaChips
                    if loading {
                        ProgressView().tint(Color.sgAccent).frame(maxWidth: .infinity).padding(.top, 8)
                    }
                    if let plot = detail?.plot {
                        Text(plot).font(.callout).foregroundStyle(Color.sgText).lineSpacing(3)
                    }
                    creditsSection
                }
                .padding(.horizontal, SGMetric.gutter)
            }
            .padding(.bottom, 28)
        }
        .background(Color.sgGround)
        .navigationTitle(channel.name).navigationBarTitleDisplayModeInlineIfAvailable()
        .task {
            if let id = vodId { detail = await library.loadMovieDetail(vodId: id) }
            loading = false
        }
        .fullScreenCover(isPresented: $playing) { PlayerView(channel: channel) }
    }

    // MARK: - Bölümler

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            GeometryReader { geo in
                AsyncImage(url: detail?.backdrop ?? detail?.cover ?? channel.logo) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    LinearGradient(colors: [Color(hex: 0x20304F), Color(hex: 0x101521)],
                                   startPoint: .top, endPoint: .bottom)
                }
                .frame(width: geo.size.width, height: 220)
                .clipped()
            }
            .frame(height: 220)
            LinearGradient(colors: [.clear, .sgGround], startPoint: .center, endPoint: .bottom)
                .frame(height: 220)

            HStack(alignment: .bottom, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10).fill(Color.sgElevated)
                    AsyncImage(url: detail?.cover ?? channel.logo) { $0.resizable().scaledToFill() } placeholder: {
                        Image(systemName: "film").foregroundStyle(Color.sgMute)
                    }
                }
                .frame(width: 92, height: 132).clipShape(RoundedRectangle(cornerRadius: 10))
                .shadow(radius: 8, y: 4)
                VStack(alignment: .leading, spacing: 6) {
                    Text(channel.name).font(.system(size: 19, weight: .heavy)).foregroundStyle(.white).lineLimit(3)
                    if let g = detail?.genre ?? (channel.group.isEmpty ? nil : channel.group) {
                        Text(g).font(.caption).foregroundStyle(Color.sgAccent2).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, SGMetric.gutter)
            .padding(.bottom, 4)
        }
        .frame(height: 220)
    }

    private var playButton: some View {
        Button { playing = true } label: {
            Label("İzle", systemImage: "play.fill")
                .font(.system(size: 15, weight: .bold))
                .frame(maxWidth: .infinity).padding(.vertical, 13)
                .background(Color.sgAccent, in: RoundedRectangle(cornerRadius: 12))
                .foregroundStyle(.white)
        }.buttonStyle(.plain)
    }

    @ViewBuilder private var metaChips: some View {
        let chips = [ratingText, detail?.releaseDate.map(yearOnly), detail?.duration].compactMap { $0 }
        if !chips.isEmpty {
            HStack(spacing: 8) {
                ForEach(chips, id: \.self) { c in
                    Text(c)
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.sgDim)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Color.sgSurface, in: Capsule())
                }
            }
        }
    }

    @ViewBuilder private var creditsSection: some View {
        if let cast = detail?.cast {
            credit("Oyuncular", cast)
        }
        if let dir = detail?.director {
            credit("Yönetmen", dir)
        }
    }

    private func credit(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.caption).foregroundStyle(Color.sgMute)
            Text(value).font(.footnote).foregroundStyle(Color.sgText).lineLimit(3)
        }
    }

    private var ratingText: String? {
        guard let r = detail?.rating, let v = Double(r), v > 0 else { return nil }
        return String(format: "★ %.1f", v)
    }

    private func yearOnly(_ s: String) -> String {
        // "2021-05-14" → "2021"; boş/yıl-değilse olduğu gibi.
        String(s.prefix(4))
    }
}

// tvOS'ta olmayan inline title modifier'ını güvenli uygula.
private extension View {
    @ViewBuilder func navigationBarTitleDisplayModeInlineIfAvailable() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}
