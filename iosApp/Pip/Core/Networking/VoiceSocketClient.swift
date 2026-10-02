import AVFAudio
// `AVCaptureDevice` (used only in the non-iOS mic-permission fallback below)
// lives in AVFoundation proper, not AVFAudio — everything else in this file
// (AVAudioEngine, AVAudioSession, etc.) is AVFAudio.
#if !os(iOS)
import AVFoundation
#endif
import Combine
import Foundation
import PipShared

/// Bridges the mic + speaker to the shared module's `VoiceSessionViewModel`
/// (PRD-KMP-Migration-v2.md §7, migrated per §19 Sprint 6 — was a raw
/// `URLSessionWebSocketTask` managing `/ws/voice/{session_id}` directly).
/// Preserves the exact public API this class had before the migration, so
/// `VoiceSessionView.swift`/`PipAvatarViewModel.swift` needed zero changes:
/// same `Event`/`ServerState`/`ListeningMode` types, same method signatures.
/// Audio I/O (`AVAudioEngine`) stays fully native (PRD §13's "Audio Engine
/// stays native on both platforms") — only the WS protocol/session logic
/// moved to Kotlin.
@MainActor
final class VoiceSocketClient: NSObject, ObservableObject {
    /// Flat shape `VoiceSessionView` maps into its own transcript-bubble
    /// rendering — same shape as `ChatSocketClient.HistoryMessage`,
    /// duplicated rather than shared since these two classes otherwise
    /// have no dependency on each other (mirrors that file's own docstring
    /// about preserving each bridge class's pre-migration public API).
    struct HistoryMessage {
        let isUser: Bool
        let text: String
        let createdAt: String
    }

    /// Mirrors the backend's `app/voice/listening_modes.py`. "ptt" turns
    /// off server-side VAD entirely — the button hold is the only signal.
    enum ListeningMode: String, CaseIterable {
        case responsive
        case patient
        case ptt

        /// Kotlin's `PipShared.ListeningMode` (SKIE-bridged enum) uses the
        /// same case names — see `Shared.ListeningMode.swift` in the
        /// shared module's generated sources.
        fileprivate var kotlinValue: PipShared.ListeningMode {
            switch self {
            case .responsive: return .responsive
            case .patient: return .patient
            case .ptt: return .ptt
            }
        }
    }

    enum Event {
        case transcript(text: String, isFinal: Bool)
        case reply(text: String, isFinal: Bool, requestId: String?)
        case interrupt
        case error(String)
        /// Fired once per turn, the moment the mic's own RMS crosses the
        /// voice-activity threshold — before any server round trip, so the
        /// UI can show an instant "I hear you" acknowledgement instead of
        /// a generic "listening" placeholder with no feedback that
        /// anything was actually picked up yet.
        case voiceDetected
        /// One-shot informational message, distinct from `error` — e.g.
        /// the backend degraded to its local Ollama fallback because every
        /// cloud LLM hit its limit (Mode B/text-chat only — Mode A's
        /// Gemini Live doesn't go through that router at all).
        case notice(String)
    }

    /// Mirrors the backend's `app/voice/state_machine.py` `VoiceState` —
    /// the session's authoritative state, driven by Mode A/B's actual turn
    /// logic server-side rather than reconstructed here from side signals.
    enum ServerState: String {
        case idle, listening, processing, thinking, speaking, interrupted, paused, cancelled, disconnected, error, reconnecting
        // Conversation Queue Management (Mode B only): `recording` is user
        // speech captured while Pip keeps talking; `waitingQueue`/
        // `queueProcessing`/`completed` are turn-boundary states around
        // draining queued items FIFO after Pip's current reply finishes.
        case recording
        case waitingQueue = "waiting_queue"
        case queueProcessing = "queue_processing"
        case completed
    }

    @Published private(set) var isActive = false
    /// Latest state pushed by the shared module's `VoiceSessionViewModel.state`.
    @Published private(set) var serverState: ServerState = .idle
    /// Length of the backend's Conversation Queue (Mode B only) — turns the
    /// user spoke while Pip was replying, held for FIFO processing once the
    /// current reply finishes. Pushed via a `QueueUpdate` event.
    @Published private(set) var queueLength: Int = 0
    /// Most recent WS ping/pong round-trip time — debug-only signal, not
    /// used for any gating.
    @Published private(set) var lastRttMs: Int64?
    @Published private(set) var isTalking = false
    /// True while assistant audio is actively being played back (TTS output
    /// scheduled/playing), so the UI can animate the mascot talking.
    @Published private(set) var isSpeaking = false
    /// Envelope-followed `0...1` loudness of the audio currently playing —
    /// drives the mouth-open/blob animation while `isSpeaking` (PRD §4.3).
    @Published private(set) var audioLevel: CGFloat = 0
    /// Envelope-followed `0...1` loudness of the user's own mic input,
    /// sampled from the same tap that streams audio to the backend — drives
    /// the listening-state glow reacting to the user's voice (PRD §4.2).
    @Published private(set) var micLevel: CGFloat = 0

    private let viewModel: VoiceSessionViewModel
    private var listeningMode: ListeningMode = .responsive
    private var onEvent: ((Event) -> Void)?
    private var stateTask: Task<Void, Never>?
    private var eventsTask: Task<Void, Never>?
    private var audioFramesTask: Task<Void, Never>?

    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private let playbackFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32, sampleRate: 24000, channels: 1, interleaved: false
    )!
    private let sendFormat = AVAudioFormat(
        commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true
    )!
    private var micConverter: AVAudioConverter?
    private var isGraphConfigured = false
    private let playbackLevelMeter = AudioLevelMeter()
    private let micLevelMeter = AudioLevelMeter()
    /// One-shot gate for `.voiceDetected` — reset alongside
    /// `awaitingFirstReplyAudio` at every point a fresh turn begins.
    private var voiceDetected = false

    // MARK: - Latency instrumentation (measurement only, no gating)

    /// Last time a mic chunk's raw RMS looked like actual speech, not
    /// silence/room tone — the closest proxy we have on-device for "the
    /// user just stopped talking" (T0), since turn-taking itself is
    /// decided server-side (Gemini's own VAD in Mode A, ours in Mode B).
    private var lastVoiceActivityAt: Date?
    private let voiceActivityRMS: Float = 0.02
    /// True from a fresh turn boundary (final transcript / interrupt)
    /// until the first byte of the assistant's reply audio arrives — lets
    /// `playAudio` log a one-shot round-trip number instead of one per
    /// streamed chunk.
    private var awaitingFirstReplyAudio = true
    /// `server_time ≈ client_time + clockOffsetMs` — estimated once per
    /// session from the ping/pong RTT (assumes roughly symmetric latency).
    private var clockOffsetMs: Int64 = 0
    private var pendingReplyTurnId: String?
    private var pendingReplyServerEpochMs: Int64?

    init(sessionId: String = AppSession.id) {
        // `sessionId` is kept as a parameter for call-site compatibility —
        // the shared module resolves the same UUID itself via its own
        // `SessionRepository`, backed by the identical `UserDefaults` key.
        viewModel = IOSAccessorsKt.getVoiceSessionViewModel()
        super.init()
        // Deliberately NOT attaching/connecting `playerNode` here — doing so
        // forces AVAudioEngine to instantiate its full node graph (including
        // `inputNode`) before the audio session is configured/activated,
        // which bakes in a stale, zero-channel input format that later
        // crashes `installTap` even after the session comes up. The graph
        // is built lazily in `prepareAudio()` instead, after activation.
    }

    /// Requests mic permission (STT now happens server-side, so unlike the
    /// old on-device path this no longer needs `SFSpeechRecognizer` auth).
    func requestMicPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            #if os(iOS)
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
            #else
            AVCaptureDevice.requestAccess(for: .audio) { granted in
                continuation.resume(returning: granted)
            }
            #endif
        }
    }

    /// Restores prior voice turns — same `GET /api/session/{id}/history` /
    /// same session_id as `ChatSocketClient.loadHistory()`, since
    /// mode_b_pipeline.py/voice_mode_a.py persist voice turns via the same
    /// `session_store.add_message` chat/voice share. Already scoped to the
    /// logged-in user via that endpoint's ownership check, so there's
    /// nothing further to filter client-side.
    func loadHistory() async -> [HistoryMessage] {
        _ = try? await viewModel.loadHistory()
        guard let conversation = viewModel.history.value as? Conversation else { return [] }
        return conversation.messages.map { message in
            HistoryMessage(isUser: message.role == .user, text: message.content, createdAt: message.createdAt)
        }
    }

    /// Sets the session's listening mode on the backend before connecting —
    /// Mode A's turn-detection sensitivity and Mode B's VAD threshold are
    /// both fixed at connection time, so switching modes means restarting
    /// the session (call `stop()` then `start(...)` again).
    func setListeningMode(_ mode: ListeningMode) async {
        _ = try? await viewModel.updateListeningMode(mode: mode.kotlinValue)
    }

    /// "a" (Gemini Live) or "b" (custom cascaded pipeline — mode_b_pipeline.py).
    /// Same fixed-at-connection-time caveat as setListeningMode: switching
    /// means stop() then start() again.
    func setVoiceMode(_ mode: String) async {
        _ = try? await viewModel.updateVoiceMode(mode: mode)
    }

    func start(listeningMode: ListeningMode = .responsive, onEvent: @escaping (Event) -> Void) {
        self.onEvent = onEvent
        self.listeningMode = listeningMode
        guard stateTask == nil else { return }

        viewModel.start()
        prepareAudio()
        isActive = true

        stateTask = Task { [weak self] in
            guard let self else { return }
            for await turnState in self.viewModel.state {
                self.handleStateChange(turnState)
            }
        }
        eventsTask = Task { [weak self] in
            guard let self else { return }
            for await event in self.viewModel.events {
                self.handle(event)
            }
        }
        audioFramesTask = Task { [weak self] in
            guard let self else { return }
            for await frame in self.viewModel.audioFrames {
                self.playAudio(frame.toData())
            }
        }
        sendLatencyPing()
    }

    func stop() {
        viewModel.stop()
        stateTask?.cancel()
        stateTask = nil
        eventsTask?.cancel()
        eventsTask = nil
        audioFramesTask?.cancel()
        audioFramesTask = nil

        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        #if os(iOS)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        #endif
        isActive = false
        serverState = .idle
        queueLength = 0
        voiceDetected = false
        pendingBufferCount = 0
        speakingResetTask?.cancel()
        isSpeaking = false
        audioLevel = 0
        playbackLevelMeter.reset()
        micLevel = 0
        micLevelMeter.reset()
    }

    /// Push-to-talk hold start (VC-3 fallback) — no-op outside `.ptt` mode.
    /// The mic tap is always installed once in `prepareAudio()`; ptt just
    /// gates whether captured audio actually gets sent, rather than
    /// adding/removing the tap live, which crashes
    /// `AVAudioEngine` if attempted while it's already running.
    func beginTalking() {
        guard listeningMode == .ptt, !isTalking else { return }
        isTalking = true
        voiceDetected = false
        viewModel.sendPushToTalkStart()
    }

    /// Push-to-talk hold release — no-op outside `.ptt` mode.
    func endTalking() {
        guard listeningMode == .ptt, isTalking else { return }
        isTalking = false
        viewModel.sendPushToTalkEnd()
    }

    /// Thumbs-up/down on a past reply — `requestId` comes from that reply's
    /// `Event.reply(requestId:)` (only present on the final chunk of a
    /// turn, see `mode_b_pipeline.py`). Backend accepts this via
    /// `ws_voice.py`'s `_client_frames`, ahead of both mode pipelines.
    func sendFeedback(requestId: String, positive: Bool) {
        Task { [weak self] in
            guard let self else { return }
            _ = try? await self.viewModel.sendFeedback(requestId: requestId, positive: positive)
        }
    }

    // MARK: - Shared-module event dispatch

    /// One-shot NTP-style probe so `[latency]` logs can correct for the
    /// two devices' clocks not agreeing — otherwise a cross-device delta
    /// like T2b(server)->T3(client) is meaningless.
    private func sendLatencyPing() {
        let clientSentAt = Int64(Date().timeIntervalSince1970 * 1000)
        viewModel.sendPing(clientSentAt: clientSentAt)
    }

    private func handleStateChange(_ turnState: VoiceTurnState) {
        guard let state = ServerState(rawValue: turnState.wireValue) else { return }
        // Belt-and-suspenders reset: covers a turn that never produces a
        // transcript at all (e.g. STT returned empty in Mode B), which the
        // "transcript"/"interrupt" resets below wouldn't otherwise catch
        // before the *next* turn starts.
        if state == .listening, serverState != .listening {
            voiceDetected = false
        }
        serverState = state
    }

    private func handle(_ event: VoiceServerEvent) {
        switch onEnum(of: event) {
        case .transcript(let e):
            if e.final {
                awaitingFirstReplyAudio = true
                voiceDetected = false
            }
            onEvent?(.transcript(text: e.text, isFinal: e.final))
        case .reply(let e):
            onEvent?(.reply(text: e.text, isFinal: e.final, requestId: e.requestId))
        case .queueUpdate(let e):
            queueLength = Int(e.queueLength)
        case .interrupt:
            stopPlayback()
            awaitingFirstReplyAudio = true
            voiceDetected = false
            onEvent?(.interrupt)
        case .error(let e):
            onEvent?(.error(e.message))
        case .notice(let e):
            onEvent?(.notice(e.text))
        case .state:
            break // handled by the `state` StateFlow loop in `start()` instead
        case .pong(let e):
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let rtt = now - e.clientSentAt
            clockOffsetMs = e.serverEpochMs - (e.clientSentAt + rtt / 2)
            lastRttMs = rtt
            print("[latency] WS ping RTT=\(rtt)ms, estimated client<->server clock offset=\(clockOffsetMs)ms")
        case .replyAudioStart(let e):
            // T2b anchor from the backend (PRD: measure T2b->T3 to isolate
            // network+client overhead from backend-side latency).
            pendingReplyTurnId = e.turnId
            pendingReplyServerEpochMs = e.serverEpochMs?.int64Value
        case .unknown:
            break
        }
    }

    // MARK: - Mic capture -> shared module

    /// Configures the session, installs the mic tap, *then* starts the
    /// engine — in that order. Starting the engine first and installing a
    /// recording tap afterward throws (`CreateRecordingTap`/
    /// `InstallTapOnNode`), since `AVAudioEngine` expects taps to exist
    /// before the graph is running.
    ///
    /// The tap itself is always installed, even in `.ptt` mode — it just
    /// checks `isTalking` before forwarding bytes. Adding/removing the tap
    /// live while the engine is already running hits the same crash, so
    /// gating what gets *sent* is the only safe way to pause capture
    /// mid-session.
    private func prepareAudio() {
        #if os(iOS)
        // Must configure + activate the session before touching the input
        // node — otherwise it reports a degenerate (0-channel) format and
        // `installTap` throws. `.voiceChat` mode also turns on iOS's
        // built-in echo cancellation, which barge-in relies on (PRD 11.1).
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            onEvent?(.error("Gagal mengaktifkan sesi audio."))
            return
        }
        #endif

        // Build the node graph now, for the first time, so `inputNode`
        // picks up the format from the session we just activated above —
        // see the comment in `init()`.
        if !isGraphConfigured {
            engine.attach(playerNode)
            engine.connect(playerNode, to: engine.mainMixerNode, format: playbackFormat)
            // Enables AVAudioEngine's own acoustic echo cancellation
            // (Voice-Processing I/O). `.voiceChat` on the AVAudioSession
            // above only sets a routing/gain *hint* — it does NOT by
            // itself make AVAudioEngine cancel played-back audio out of
            // what the input tap picks back up. Without this, the built-in
            // speaker (mic and speaker sharing one chassis, unlike AirPods
            // where they're physically separate) lets Pip's own reply
            // audio leak into the mic; the server's VAD mistakes that for
            // the user interrupting, firing a false barge-in after nearly
            // every word. AirPods masked this because the headset does its
            // own on-device echo cancellation regardless of what happens
            // here — non-fatal if the device/OS can't enable it, since
            // voice processing is a quality improvement, not a hard
            // requirement for the session to work at all.
            try? engine.inputNode.setVoiceProcessingEnabled(true)
            isGraphConfigured = true
        }

        let inputNode = engine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)
        guard inputFormat.channelCount > 0, inputFormat.sampleRate > 0 else {
            onEvent?(.error("Mikrofon tidak terdeteksi. Coba tutup dan buka lagi sesi suaranya."))
            return
        }
        micConverter = AVAudioConverter(from: inputFormat, to: sendFormat)

        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
            guard let self, let converter = self.micConverter else { return }
            let ratio = self.sendFormat.sampleRate / inputFormat.sampleRate
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 32
            guard let out = AVAudioPCMBuffer(pcmFormat: self.sendFormat, frameCapacity: capacity) else { return }

            var suppliedInput = false
            var conversionError: NSError?
            converter.convert(to: out, error: &conversionError) { _, outStatus in
                if suppliedInput {
                    outStatus.pointee = .noDataNow
                    return nil
                }
                suppliedInput = true
                outStatus.pointee = .haveData
                return buffer
            }
            guard conversionError == nil, let channelData = out.int16ChannelData, out.frameLength > 0 else { return }
            let sampleCount = Int(out.frameLength)
            let data = Data(bytes: channelData[0], count: sampleCount * MemoryLayout<Int16>.size)
            let rms = AudioLevelMeter.rms(ofInt16: UnsafeBufferPointer(start: channelData[0], count: sampleCount))

            Task { @MainActor [weak self] in
                guard let self else { return }
                self.micLevel = CGFloat(self.micLevelMeter.process(rms: rms, boost: 6))
                if rms >= self.voiceActivityRMS {
                    self.lastVoiceActivityAt = Date()
                }
                guard self.listeningMode != .ptt || self.isTalking else { return }
                if rms >= self.voiceActivityRMS, !self.voiceDetected {
                    self.voiceDetected = true
                    self.onEvent?(.voiceDetected)
                }
                _ = try? await self.viewModel.sendAudio(bytes: data.toKotlinByteArray())
            }
        }

        do {
            try engine.start()
        } catch {
            onEvent?(.error("Gagal mengaktifkan mikrofon."))
        }
        if !playerNode.isPlaying {
            playerNode.play()
        }
    }

    // MARK: - Playback

    /// How many scheduled buffers haven't finished playing yet — used to
    /// know when the assistant has actually gone quiet (streamed chunks
    /// arrive in bursts, so this needs to hit zero, not just "no new data
    /// for a moment") before flipping `isSpeaking` back off.
    private var pendingBufferCount = 0
    private var speakingResetTask: Task<Void, Never>?

    private func playAudio(_ data: Data) {
        if awaitingFirstReplyAudio {
            awaitingFirstReplyAudio = false
            let turnLabel = pendingReplyTurnId ?? "?"
            if let lastVoiceActivityAt {
                let roundTripMs = Int(Date().timeIntervalSince(lastVoiceActivityAt) * 1000)
                print("[latency] turn=\(turnLabel) T0->T3 round-trip (last voice activity -> first reply audio received) = \(roundTripMs)ms")
            }
            if let serverEpochMs = pendingReplyServerEpochMs {
                let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
                // Convert the server's T2b into this device's clock frame
                // before diffing — see `clockOffsetMs` / the pong handler.
                let t2bToT3 = nowEpochMs - serverEpochMs + clockOffsetMs
                print("[latency] turn=\(turnLabel) T2b->T3 (backend sent -> client received, clock-corrected) = \(t2bToT3)ms")
            }
            pendingReplyServerEpochMs = nil
        }

        let frameCount = data.count / MemoryLayout<Int16>.size
        guard frameCount > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: AVAudioFrameCount(frameCount))
        else { return }
        buffer.frameLength = AVAudioFrameCount(frameCount)

        data.withUnsafeBytes { (rawBuffer: UnsafeRawBufferPointer) in
            let samples = rawBuffer.bindMemory(to: Int16.self)
            let floatChannel = buffer.floatChannelData![0]
            for i in 0..<frameCount {
                floatChannel[i] = Float(samples[i]) / 32768.0
            }
            let rms = AudioLevelMeter.rms(ofInt16: samples)
            audioLevel = CGFloat(playbackLevelMeter.process(rms: rms))
        }

        speakingResetTask?.cancel()
        isSpeaking = true
        pendingBufferCount += 1

        playerNode.scheduleBuffer(buffer) { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                self.pendingBufferCount = max(0, self.pendingBufferCount - 1)
                if self.pendingBufferCount == 0 {
                    self.scheduleSpeakingReset()
                }
            }
        }
    }

    /// Small grace period after the last queued buffer finishes before
    /// declaring the assistant done talking, so a brief gap between
    /// streamed chunks doesn't make the mouth flicker shut mid-sentence.
    private func scheduleSpeakingReset() {
        speakingResetTask?.cancel()
        speakingResetTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 220_000_000)
            guard let self, !Task.isCancelled, self.pendingBufferCount == 0 else { return }
            self.isSpeaking = false
            self.audioLevel = 0
        }
    }

    /// Barge-in (VC-2): drop whatever's queued for playback immediately.
    /// Called both when the server detects it (VAD) and when the user
    /// taps the blob to manually cut the assistant off.
    func stopPlayback() {
        playerNode.stop()
        playerNode.play()
        pendingBufferCount = 0
        speakingResetTask?.cancel()
        isSpeaking = false
        audioLevel = 0
        playbackLevelMeter.reset()
    }
}

// MARK: - Kotlin <-> Swift byte array bridging

private extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}

private extension KotlinByteArray {
    func toData() -> Data {
        var data = Data(count: Int(size))
        for i in 0..<Int(size) {
            data[i] = UInt8(bitPattern: get(index: Int32(i)))
        }
        return data
    }
}
