import SwiftUI
import Core
import Design

/// Uygulama-içi mini oynatıcı deposu (kök seviyede). Apple PiP yalnız AVPlayer'ı desteklediği için
/// VLC (MKV/AVI/TS) içeriğinde sistem PiP kullanılamaz; bunun yerine sürüklenebilir bir uygulama-içi
/// mini pencere sunarız. Tam ekran oynatıcıdan devralır (aynı konumdan sürer).
@MainActor
final class MiniPlayerStore: ObservableObject {
    @Published var channel: Channel?
    @Published var url: URL?
    let vlc = VLCController()

    var isActive: Bool { channel != nil }

    /// Tam ekran oynatıcıdan devral: aynı URL'yi mini pencerede sürdür.
    func present(channel: Channel, url: URL, seekTo fraction: Double) {
        self.channel = channel
        self.url = url
        vlc.play(url: url)
        if fraction > 0.001 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
                self?.vlc.seek(toFraction: fraction)
            }
        }
    }

    func close() {
        vlc.stop()
        channel = nil
        url = nil
    }
}

/// Sürüklenebilir yüzen mini pencere. Dokun → tam ekrana genişlet; ✕ → kapat.
struct MiniPlayerWindow: View {
    @EnvironmentObject private var mini: MiniPlayerStore
    @EnvironmentObject private var library: LibraryStore
    var onExpand: (Channel) -> Void

    @State private var offset: CGSize = .zero
    @GestureState private var dragLive: CGSize = .zero

    private let w: CGFloat = 214

    var body: some View {
        if let url = mini.url, let ch = mini.channel {
            let h = w * 9 / 16
            ZStack(alignment: .topTrailing) {
                VLCPlayerView(controller: mini.vlc, url: url)
                    .frame(width: w, height: h)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.sgLineSoft, lineWidth: 1))
                    .shadow(color: .black.opacity(0.5), radius: 12, y: 6)
                // Şeffaf dokunma katmanı — VLC UIView dokunmayı yuttuğu için üstte ayrı katman.
                Color.white.opacity(0.001)
                    .frame(width: w, height: h)
                    .contentShape(Rectangle())
                    .onTapGesture { saveProgress(); onExpand(ch) }

                // Kanal adı (alt şerit)
                VStack {
                    Spacer()
                    Text(ch.name).font(.system(size: 10, weight: .semibold)).lineLimit(1)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(LinearGradient(colors: [.clear, .black.opacity(0.65)],
                                                   startPoint: .top, endPoint: .bottom))
                }
                .frame(width: w, height: h)
                .allowsHitTesting(false)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // Kapat
                Button { saveProgress(); mini.close() } label: {
                    Image(systemName: "xmark").font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white).padding(6)
                        .background(.black.opacity(0.6), in: Circle())
                }
                .buttonStyle(.plain)
                .padding(5)
            }
            .frame(width: w, height: h)
            .offset(x: offset.width + dragLive.width, y: offset.height + dragLive.height)
            .gesture(
                DragGesture()
                    .updating($dragLive) { v, s, _ in s = v.translation }
                    .onEnded { v in offset.width += v.translation.width; offset.height += v.translation.height }
            )
            .padding(.trailing, 12).padding(.bottom, 92)   // tab bar üstünde başlasın
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .transition(.scale(scale: 0.85).combined(with: .opacity))
        }
    }

    /// VOD/dizi ise ilerlemeyi kaydet (genişletince/kapatınca kaldığı yerden sürebilsin).
    private func saveProgress() {
        guard let ch = mini.channel, ch.kind != .live else { return }
        let dur = mini.vlc.lengthSeconds, pos = mini.vlc.timeSeconds
        if dur > 0, pos > 0 {
            library.saveProgress(url: ch.url.absoluteString, position: pos, duration: dur)
        }
    }
}
