import Foundation

/// Real attack/release envelope follower for turning raw audio chunks into a
/// smooth `0...1` level suitable for driving animation (PRD §4.3): fast
/// attack so a syllable's hit registers immediately, slower release so it
/// doesn't flicker between streamed chunks.
///
/// Uses elapsed wall-clock time between calls (not a fixed per-call step),
/// so it stays correct regardless of how large or how often the incoming
/// PCM chunks actually are.
final class AudioLevelMeter {
    private let attackSeconds: Double
    private let releaseSeconds: Double
    private var lastTimestamp: CFAbsoluteTime?

    private(set) var level: Float = 0

    init(attackSeconds: Double = 0.05, releaseSeconds: Double = 0.175) {
        self.attackSeconds = attackSeconds
        self.releaseSeconds = releaseSeconds
    }

    /// Feeds one chunk's RMS amplitude through the envelope. `boost`
    /// compensates for speech RMS normally sitting well under 1.0 so the
    /// result actually spans a usable animation range.
    @discardableResult
    func process(rms: Float, boost: Float = 4.5) -> Float {
        let target = min(1, max(0, rms * boost))
        let now = CFAbsoluteTimeGetCurrent()
        let dt = lastTimestamp.map { now - $0 } ?? (1.0 / 50)
        lastTimestamp = now

        let tau = target > level ? attackSeconds : releaseSeconds
        let alpha = Float(1 - exp(-dt / max(tau, 0.001)))
        level += (target - level) * alpha
        return level
    }

    func reset() {
        level = 0
        lastTimestamp = nil
    }

    /// RMS of a mono `Int16` PCM buffer, normalized to `-1...1` before squaring.
    static func rms(ofInt16 samples: UnsafeBufferPointer<Int16>) -> Float {
        guard !samples.isEmpty else { return 0 }
        var sumOfSquares: Float = 0
        for sample in samples {
            let normalized = Float(sample) / 32768.0
            sumOfSquares += normalized * normalized
        }
        return (sumOfSquares / Float(samples.count)).squareRoot()
    }
}
