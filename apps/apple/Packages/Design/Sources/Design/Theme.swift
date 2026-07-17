import SwiftUI

// MARK: - Cheesino renk token'ları
// Marka paleti · 60-30-10: %60 Gece Yarısı Lacivirdi (zemin) · %30 Cheesino Turuncusu (yapısal/marka/LIVE)
// · %10 Güneş Sarısı (CTA / hover / logo şimşeği). Dark-committed, sinematik.
public extension Color {
    // %60 — Zemin & nötr (Gece Yarısı Lacivirdi)
    static let sgGround   = Color(hex: 0x0B0F19)   // en derin
    static let sgSurface  = Color(hex: 0x0F172A)   // midnight navy
    static let sgElevated = Color(hex: 0x172033)
    static let sgRaised   = Color(hex: 0x1E2A42)
    static let sgLine     = Color(hex: 0x24304A)
    static let sgLineSoft = Color(hex: 0x172033)
    // Metin (Saf Beyaz / Dijital Gri)
    static let sgText     = Color(hex: 0xF8FAFC)
    static let sgDim      = Color(hex: 0x94A0B8)
    static let sgMute     = Color(hex: 0x5A6784)
    // %30 — Cheesino Turuncusu (marka / yapısal / LIVE)
    static let sgAccent   = Color(hex: 0xFF8A00)
    static let sgAccent2  = Color(hex: 0xFFA733)
    static let sgLive     = Color(hex: 0xFF8A00)   // canlı — marka turuncusu
    // %10 — Güneş Sarısı (CTA / hover / şimşek)
    static let sgCTA      = Color(hex: 0xFFD200)
    static let sgGold     = Color(hex: 0xFFD200)   // 4K/premium rozeti
    // Yardımcı semantik
    static let sgProgress = Color(hex: 0xFFB25A)   // ilerleme (açık amber — turuncu ailesi)
    static let sgWarn     = Color(hex: 0xF5A623)

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
    public static let display  = Font.system(size: 30, weight: .heavy)                       // hero
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
