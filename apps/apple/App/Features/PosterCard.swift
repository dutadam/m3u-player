import SwiftUI
import Core
import Design

/// Beğen / beğenme kontrolü — öneri algoritmasını besler (içerik anahtarı: url veya series_<id>).
struct LikeDislikeButtons: View {
    @EnvironmentObject private var library: LibraryStore
    let key: String
    var compact: Bool = false

    var body: some View {
        HStack(spacing: compact ? 6 : 10) {
            btn(library.isLiked(key) ? "hand.thumbsup.fill" : "hand.thumbsup",
                on: library.isLiked(key), color: Color.sgAccent) { library.toggleLike(key) }
            btn(library.isDisliked(key) ? "hand.thumbsdown.fill" : "hand.thumbsdown",
                on: library.isDisliked(key), color: Color.sgWarn) { library.toggleDislike(key) }
        }
    }

    private func btn(_ system: String, on: Bool, color: Color, _ action: @escaping () -> Void) -> some View {
        let s: CGFloat = compact ? 36 : 40
        return Button(action: action) {
            Image(systemName: system).font(.system(size: compact ? 14 : 16, weight: .semibold))
                .foregroundStyle(on ? color : Color.sgDim)
                .frame(width: s, height: s)
                .background(compact ? Color.black.opacity(0.4) : Color.sgSurface, in: Circle())
        }.buttonStyle(.plain)
    }
}

/// Basma hissi güçlü buton stili — dokununca hafifçe küçülür + koyulaşır.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .brightness(configuration.isPressed ? -0.04 : 0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Film/dizi poster kartı — 2:3 doygun görsel, izlendi rozeti + ilerleme çubuğu.
/// `width` nil ise hücreyi doldurur (adaptive grid); sabitse yatay ray için.
struct PosterCard: View {
    let title: String
    let poster: URL?
    var subtitle: String? = nil
    var watched: Bool = false
    var progress: Double = 0          // 0..1 (devam eden)
    var continueBadge: Bool = false   // "Devam" göstergesi (dizi)
    var width: CGFloat? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            posterImage
                .aspectRatio(2.0/3.0, contentMode: .fit)
                .frame(width: width)
                .frame(maxWidth: width == nil ? .infinity : nil)

            Text(title).font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.sgText).lineLimit(1)
                .frame(width: width, alignment: .leading)
                .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
            if let subtitle {
                Text(subtitle).font(.system(size: 10)).foregroundStyle(Color.sgMute)
                    .lineLimit(1).frame(width: width, alignment: .leading)
                    .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
            }
        }
    }

    private var posterImage: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 12)
                .fill(LinearGradient(colors: [Color(hex: 0x26304A), Color(hex: 0x141824)],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
            AsyncImage(url: poster) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Text(String(title.prefix(2)).uppercased())
                    .font(.system(size: 20, weight: .heavy)).foregroundStyle(.white.opacity(0.85))
            }

            if watched {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 18)).foregroundStyle(.white)
                    .background(Circle().fill(Color.sgAccent).padding(2))
                    .shadow(radius: 3).padding(6)
            } else if continueBadge {
                Text("DEVAM").font(.system(size: 8, weight: .heavy)).foregroundStyle(.white)
                    .padding(.horizontal, 5).padding(.vertical, 2)
                    .background(Color.sgAccent, in: Capsule()).padding(6)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(alignment: .bottom) {
            if progress > 0.02 && !watched {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Rectangle().fill(.black.opacity(0.5))
                        Rectangle().fill(Color.sgAccent).frame(width: g.size.width * progress)
                    }
                }
                .frame(height: 3)
            }
        }
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(.white.opacity(0.06), lineWidth: 1))
    }
}
