import SwiftUI

// MARK: - "Signal" renk token'ları
// Kaynak: docs/design/ui-preview.html :root. Dark-committed sinematik palet.
public extension Color {
    // Zemin & nötr
    static let sgGround   = Color(hex: 0x0A0B0F)
    static let sgSurface  = Color(hex: 0x13151D)
    static let sgElevated = Color(hex: 0x1C1F29)
    static let sgRaised   = Color(hex: 0x242836)
    static let sgLine     = Color(hex: 0x262B38)
    static let sgLineSoft = Color(hex: 0x1B1F2A)
    // Metin
    static let sgText     = Color(hex: 0xEDEFF6)
    static let sgDim      = Color(hex: 0x9096AC)
    static let sgMute     = Color(hex: 0x565C72)
    // Aksan (etkileşim)
    static let sgAccent   = Color(hex: 0x4C86FF)
    static let sgAccent2  = Color(hex: 0x6FA0FF)
    // Semantik (aksandan ayrı)
    static let sgLive     = Color(hex: 0xFF3B4E)   // canlı
    static let sgGold     = Color(hex: 0xF5C542)   // 4K/premium
    static let sgTeal     = Color(hex: 0x34E0A1)   // ilerleme/başarı
    static let sgWarn     = Color(hex: 0xF5A623)   // uyarı

    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

// MARK: - Tipografi (SF Pro — uygulamanın native fontu)
public enum SGFont {
    public static let display  = Font.system(size: 30, weight: .heavy).width(.standard)      // hero
    public static let title    = Font.system(size: 22, weight: .bold)
    public static let headline = Font.system(size: 15, weight: .semibold)
    public static let body     = Font.system(size: 15, weight: .regular)
    public static let caption  = Font.system(size: 11, weight: .heavy)                        // etiket
    /// Saat/skor için tabular rakam.
    public static let mono     = Font.system(size: 13, weight: .semibold).monospacedDigit()
}

// MARK: - Ölçü token'ları
public enum SGMetric {
    public static let radiusLg: CGFloat = 26
    public static let radiusMd: CGFloat = 16
    public static let radiusSm: CGFloat = 11
    public static let gutter: CGFloat = 15
}
