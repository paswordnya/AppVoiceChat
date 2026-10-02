import SwiftUI

/// A selectable mascot personality, matching the moc's `buddyDefs`. Each
/// buddy has its own blob gradient and its own pitch/rate tweak applied to
/// the single recorded `voice_app.mp3` clip, so switching buddies changes
/// both look and sound even though there's only one voice recording.
struct Buddy: Identifiable, Equatable {
    let id: String
    let name: String
    let colorFrom: Color
    let colorTo: Color
    /// Pitch shift in cents (100 cents = 1 semitone) applied to voice_app.mp3.
    let clipPitchCents: Float
    /// Playback rate applied to voice_app.mp3 (1.0 = unchanged).
    let clipRate: Float

    static func == (lhs: Buddy, rhs: Buddy) -> Bool { lhs.id == rhs.id }
}

extension Buddy {
    static let pip = Buddy(
        id: "pip", name: "Pip",
        colorFrom: Color(hex: 0xFF9466), colorTo: Color(hex: 0xFF5F82),
        clipPitchCents: 0, clipRate: 1.0
    )
    static let bloop = Buddy(
        id: "bloop", name: "Bloop",
        colorFrom: Color(hex: 0x54D6C8), colorTo: Color(hex: 0x3E8BFF),
        clipPitchCents: -300, clipRate: 0.94
    )
    static let sunny = Buddy(
        id: "sunny", name: "Sunny",
        colorFrom: Color(hex: 0xFFD24D), colorTo: Color(hex: 0xFF9F43),
        clipPitchCents: 350, clipRate: 1.1
    )
    static let coco = Buddy(
        id: "coco", name: "Coco",
        colorFrom: Color(hex: 0xC58BFF), colorTo: Color(hex: 0x7C6EF6),
        clipPitchCents: -120, clipRate: 0.98
    )

    static let all: [Buddy] = [.pip, .bloop, .sunny, .coco]
}
