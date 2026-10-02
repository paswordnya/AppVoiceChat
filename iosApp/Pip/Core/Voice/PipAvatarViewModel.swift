import Combine
import SwiftUI

/// Combines the raw per-frame signals (TTS playback level, mic level,
/// conversation phase) into Pip's 5-state avatar model (PRD §4.1/§5.1),
/// plus the always-running blink layered on top regardless of state.
@MainActor
final class PipAvatarViewModel: ObservableObject {
    enum State: Equatable {
        case idle
        case listening
        case thinking
        case speaking
        case interrupted
    }

    @Published private(set) var state: State = .idle
    /// Smoothed `0...1` TTS playback level — already envelope-followed by
    /// `VoiceSocketClient`'s `AudioLevelMeter`, just re-typed for views here.
    @Published private(set) var speakingLevel: CGFloat = 0
    /// Smoothed `0...1` mic input level, for the listening-state glow.
    @Published private(set) var listeningLevel: CGFloat = 0
    /// `1` = eyes open, `0` = mid-blink.
    @Published private(set) var eyeOpen: CGFloat = 1

    private let blink = BlinkScheduler()
    private var cancellables = Set<AnyCancellable>()

    init(voice: VoiceSocketClient) {
        blink.isHighAmplitude = { [weak self] in (self?.speakingLevel ?? 0) > 0.55 }
        blink.$eyeOpen
            .receive(on: DispatchQueue.main)
            .assign(to: &$eyeOpen)

        voice.$audioLevel
            .receive(on: DispatchQueue.main)
            .assign(to: &$speakingLevel)

        voice.$micLevel
            .receive(on: DispatchQueue.main)
            .assign(to: &$listeningLevel)

        blink.start()
    }

    /// Called by the view whenever the conversation's phase changes.
    /// Forces an immediate blink on the barge-in transition ("eh, iya?").
    func setState(_ newState: State) {
        guard newState != state else { return }
        state = newState
        if newState == .interrupted {
            blink.blinkNow()
        }
    }

    /// Stops the blink loop. Call from the owning view's `onDisappear` —
    /// the scheduler's own async loop otherwise keeps it running
    /// indefinitely (its self-reference stays alive across `await`s until
    /// explicitly cancelled).
    func teardown() {
        blink.stop()
    }
}
