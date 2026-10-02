import SwiftUI

/// A single "happy" arc — a shallow smile-shaped curve. The moc draws eyes
/// and mouth the same way (a `border-bottom` whose radius exceeds the box
/// height), which renders as a soft downward curve; this is the native
/// equivalent as a strokeable `Shape`.
private struct HappyArcShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX, y: rect.minY),
            control: CGPoint(x: rect.midX, y: rect.maxY)
        )
        return path
    }
}

/// Pip's idle mascot face (two closed/happy eyes + a smile), sized relative
/// to whatever blob it's drawn over. Reference proportions come from the
/// moc's 150pt hero blob (`eyeLeftIdle`, `eyeRightIdle`, `mouthIdle`).
struct PipBlobFace: View {
    var color: Color = PipTheme.ink

    /// How "open" the mouth should be right now, `0...1` — driven by the
    /// playing TTS audio's level so the mascot looks like it's talking
    /// along with whatever's coming out of the speaker. `0` (default) keeps
    /// the idle closed-smile look.
    var talkLevel: CGFloat = 0

    /// `1` = eyes open, `0` = mid-blink. Pass this when a `BlinkScheduler`
    /// (via `PipAvatarViewModel`) is already driving blinking, so there's a
    /// single source of truth for the animation. Leave `nil` for call sites
    /// that just want a self-contained idle blink (e.g. `MainView`'s static
    /// blob) with no avatar state machine wired up.
    var eyeOpen: CGFloat?

    private let referenceSize: CGFloat = 150
    private let eyeSize = CGSize(width: 12, height: 7)
    private let eyeInsetFromEdge: CGFloat = 48
    private let eyeTopOffset: CGFloat = 60
    private let mouthSize = CGSize(width: 18, height: 9)
    private let mouthBottomOffset: CGFloat = 50

    /// Fallback open/close amount used only when `eyeOpen` isn't supplied.
    @State private var fallbackEyeOpen: CGFloat = 1

    var body: some View {
        GeometryReader { proxy in
            let scale = min(proxy.size.width, proxy.size.height) / referenceSize
            let lineWidth = max(1.5, 2.5 * scale)
            let clampedTalk = min(max(talkLevel, 0), 1)
            let openness = min(max(eyeOpen ?? fallbackEyeOpen, 0), 1)
            let blinkSquash = 1 - openness

            ZStack {
                HappyArcShape()
                    .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .frame(width: eyeSize.width * scale, height: eyeSize.height * scale)
                    .scaleEffect(x: 1, y: 1 - blinkSquash * 0.92, anchor: .center)
                    .position(
                        x: eyeInsetFromEdge * scale + (eyeSize.width * scale) / 2,
                        y: eyeTopOffset * scale
                    )

                HappyArcShape()
                    .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .frame(width: eyeSize.width * scale, height: eyeSize.height * scale)
                    .scaleEffect(x: 1, y: 1 - blinkSquash * 0.92, anchor: .center)
                    .position(
                        x: proxy.size.width - eyeInsetFromEdge * scale - (eyeSize.width * scale) / 2,
                        y: eyeTopOffset * scale
                    )

                HappyArcShape()
                    .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .frame(width: mouthSize.width * scale, height: mouthSize.height * scale)
                    .scaleEffect(x: 1 - clampedTalk * 0.12, y: 1 + clampedTalk * 2.1, anchor: .top)
                    .animation(.easeOut(duration: 0.09), value: clampedTalk)
                    .position(
                        x: proxy.size.width / 2,
                        y: proxy.size.height - mouthBottomOffset * scale
                    )
            }
        }
        .task {
            guard eyeOpen == nil else { return }
            await runFallbackBlinkLoop()
        }
    }

    /// Self-contained blink loop for call sites that don't drive `eyeOpen`
    /// externally — same natural cadence as `BlinkScheduler`, just simpler
    /// since there's no speech amplitude to coordinate with.
    private func runFallbackBlinkLoop() async {
        while !Task.isCancelled {
            let delay = Double.random(in: 2.5...6.0)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }

            let duration = Double.random(in: 0.12...0.18)
            let closeDuration = duration * 0.4
            let openDuration = duration * 0.6

            withAnimation(.easeIn(duration: closeDuration)) { fallbackEyeOpen = 0 }
            try? await Task.sleep(nanoseconds: UInt64(closeDuration * 1_000_000_000))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: openDuration)) { fallbackEyeOpen = 1 }
        }
    }
}

#Preview {
    ZStack {
        BlobShape().fill(PipTheme.accent)
        PipBlobFace()
    }
    .frame(width: 150, height: 150)
    .padding(40)
}
