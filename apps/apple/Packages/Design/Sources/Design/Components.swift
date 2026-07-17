import SwiftUI

// Yeniden kullanılabilir "Signal" bileşenleri. Görsel kaynak: docs/design/ui-preview.html.

/// Nabız atan LIVE etiketi (semantik kırmızı — aksandan ayrı).
public struct LivePill: View {
    public var text: String
    @State private var pulse = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    public init(_ text: String = "LIVE") { self.text = text }

    public var body: some View {
        HStack(spacing: 5) {
            Circle().fill(.white).frame(width: 6, height: 6).opacity(pulse ? 0.35 : 1)
            Text(text).font(.system(size: 10, weight: .heavy)).kerning(0.6)
        }
        .padding(.vertical, 3).padding(.leading, 6).padding(.trailing, 8)
        .foregroundStyle(.white)
        .background(Color.sgLive, in: RoundedRectangle(cornerRadius: 6))
        .shadow(color: .sgLive.opacity(0.5), radius: 8)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

/// Kalite rozeti (4K altın / FHD mavi / HD gri).
public struct QualityBadge: View {
    public var text: String
    public init(_ text: String) { self.text = text }

    private var bg: Color {
        switch text.uppercased() {
        case "4K", "UHD": return .sgGold          // premium → Güneş Sarısı
        case "FHD": return Color(hex: 0xC8D2E6)
        default: return Color(hex: 0x9AA6BE)
        }
    }
    public var body: some View {
        Text(text.uppercased())
            .font(.system(size: 9, weight: .heavy))
            .foregroundStyle(Color.sgGround)
            .padding(.horizontal, 5).padding(.vertical, 2)
            .background(bg, in: RoundedRectangle(cornerRadius: 4))
    }
}

/// İzleme ilerleme çubuğu (teal).
public struct ProgressBarLine: View {
    public var fraction: Double
    public init(fraction: Double) { self.fraction = max(0, min(1, fraction)) }
    public var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.16))
                Capsule().fill(Color.sgProgress).frame(width: geo.size.width * fraction)
            }
        }
        .frame(height: 3)
    }
}

#if os(tvOS)
/// tvOS odak halkası — focus engine deseni (scale + azure glow).
public struct FocusGlow: ViewModifier {
    var isFocused: Bool
    public func body(content: Content) -> some View {
        content
            .scaleEffect(isFocused ? 1.12 : 1)
            .overlay(RoundedRectangle(cornerRadius: SGMetric.radiusSm)
                .strokeBorder(Color.sgAccent, lineWidth: isFocused ? 3 : 0))
            .shadow(color: isFocused ? .sgAccent.opacity(0.55) : .clear, radius: 24)
            .animation(.easeOut(duration: 0.2), value: isFocused)
    }
}
public extension View {
    func focusGlow(_ focused: Bool) -> some View { modifier(FocusGlow(isFocused: focused)) }
}
#endif
