import SwiftUI

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

/// Brand tokens shared across screens, mirrored from the moc's design tokens.
enum PipTheme {
    /// Default brand accent (the moc's `brandColor` default, "Blue").
    static let accent = Color(hex: 0x3B82F6)
    /// `color-mix(in srgb, accent 60%, black)` — the splash gradient's dark stop.
    static let accentDeep = Color(hex: 0x234E94)
    /// Mascot face / heading ink color.
    static let ink = Color(hex: 0x2B2320)
    /// App background cream.
    static let cream = Color(hex: 0xFFFCF8)
    /// Default buddy ("Pip") mascot gradient stops.
    static let buddyFrom = Color(hex: 0xFF9466)
    static let buddyTo = Color(hex: 0xFF5F82)
}

extension Font {
    /// Stand-in for the moc's "Fredoka" display font (rounded SF is the
    /// closest system-available match without bundling a custom font).
    static func pipDisplay(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }

    /// Stand-in for the moc's "Inter" body font.
    static func pipBody(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .default)
    }
}
