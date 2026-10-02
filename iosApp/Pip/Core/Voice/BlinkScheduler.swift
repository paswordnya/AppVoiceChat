import Combine
import SwiftUI

/// Drives Pip's blinking on its own, independent of conversation state,
/// per PRD §4.4 — the detail that keeps the mascot feeling alive even at
/// rest. Runs continuously once `start()` is called; blinks are randomized
/// in interval, duration, and occasional doubling, and can be suppressed
/// mid-emphasis or forced on demand (barge-in reaction).
@MainActor
final class BlinkScheduler: ObservableObject {
    /// `1` = eyes fully open, `0` = fully shut.
    @Published private(set) var eyeOpen: CGFloat = 1

    /// Polled before each scheduled blink; when true, the blink is deferred
    /// rather than skipped, since real speech rarely blinks mid-emphasis.
    var isHighAmplitude: () -> Bool = { false }

    private var loopTask: Task<Void, Never>?

    func start() {
        guard loopTask == nil else { return }
        loopTask = Task { [weak self] in await self?.runLoop() }
    }

    func stop() {
        loopTask?.cancel()
        loopTask = nil
        eyeOpen = 1
    }

    /// Forces one quick blink right now — used on the `interrupted`
    /// transition ("eh, iya?" per PRD §4.4).
    func blinkNow() {
        Task { [weak self] in await self?.performBlink() }
    }

    private func runLoop() async {
        while !Task.isCancelled {
            let interval = Double.random(in: 2.5...6.0)
            try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
            guard !Task.isCancelled else { return }

            while isHighAmplitude() {
                try? await Task.sleep(nanoseconds: 60_000_000)
                guard !Task.isCancelled else { return }
            }

            await performBlink()

            if Double.random(in: 0...1) < 0.15 {
                let gap = Double.random(in: 0.05...0.3)
                try? await Task.sleep(nanoseconds: UInt64(gap * 1_000_000_000))
                guard !Task.isCancelled else { return }
                await performBlink()
            }
        }
    }

    private func performBlink() async {
        let duration = Double.random(in: 0.12...0.18)
        let closeDuration = duration * 0.4
        let openDuration = duration * 0.6

        withAnimation(.easeIn(duration: closeDuration)) { eyeOpen = 0 }
        try? await Task.sleep(nanoseconds: UInt64(closeDuration * 1_000_000_000))
        guard !Task.isCancelled else { return }

        withAnimation(.easeOut(duration: openDuration)) { eyeOpen = 1 }
        try? await Task.sleep(nanoseconds: UInt64(openDuration * 1_000_000_000))
    }
}
