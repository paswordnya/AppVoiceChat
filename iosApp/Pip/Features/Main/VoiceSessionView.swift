import SwiftUI

/// The full-screen voice session sheet from the moc (`voiceFullscreen`
/// overlay): presented when the home blob is tapped in Voice mode.
///
/// Full duplex, server-driven (VC-1..VC-6): the mic streams continuously
/// to `/ws/voice/{id}` over `VoiceSocketClient`, the backend's VAD decides
/// when the user's turn ends, the LLM reply comes back as streamed audio
/// (Gemini Live preset voice, or a cloned voice via the custom pipeline)
/// plus a text transcript/reply for on-screen display. Tapping the blob
/// manually barges in, same as speaking over it.
///
/// The avatar's animation (PRD-Pip-Avatar-Animation) is driven by
/// `PipAvatarViewModel`, mapped from this view's existing content-oriented
/// `Phase` plus a couple of transient signals (`voice.isActive`,
/// `voice.isSpeaking`, the `interrupted` pulse) — see `avatarState` below.
/// None of the transcript/reply/UI-card logic changes; only what the blob
/// looks like does.
struct VoiceSessionView: View {
    var onEnd: () -> Void

    private enum Phase {
        case listening
        case thinking
        case done
    }

    @ObservedObject private var buddyStore = BuddyStore.shared
    @StateObject private var voice: VoiceSocketClient
    @StateObject private var avatar: PipAvatarViewModel
    @State private var phase: Phase = .listening
    @State private var transcript = ""
    @State private var finalQuestion = ""
    @State private var reply = ""
    @State private var authorizationError: String?
    @State private var isBreathing = false
    @State private var listeningMode: VoiceSocketClient.ListeningMode = .responsive
    /// "a" (Gemini Live) / "b" (custom cascaded pipeline, mode_b_pipeline.py)
    /// — dev-facing toggle until there's a real Settings surface for this
    /// (PRD_TDD_pip_Voice_AI.md §20 tracks that gap).
    @State private var voiceMode: String = "a"
    @State private var isInterruptedPulse = false
    @State private var interruptedResetTask: Task<Void, Never>?
    @State private var notice: String?
    @State private var noticeResetTask: Task<Void, Never>?
    /// From the final chunk of the most recent reply (see
    /// `VoiceSocketClient.Event.reply(requestId:)`) — what a thumbs up/down
    /// tap attaches to. Reset on interrupt/tap-to-barge-in/new turn so
    /// feedback can't attach to a stale reply.
    @State private var lastRequestId: String?
    @State private var feedbackSent = false
    @State private var showHistory = false
    @State private var historyMessages: [VoiceSocketClient.HistoryMessage] = []
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(onEnd: @escaping () -> Void) {
        self.onEnd = onEnd
        let voiceClient = VoiceSocketClient()
        _voice = StateObject(wrappedValue: voiceClient)
        _avatar = StateObject(wrappedValue: PipAvatarViewModel(voice: voiceClient))
    }

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(PipTheme.ink.opacity(0.18))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            ZStack(alignment: .topTrailing) {
                Button(action: buddyStore.cycleNext) {
                    VStack(spacing: 4) {
                        HStack(spacing: 5) {
                            Text(buddyStore.selected.name)
                            Image(systemName: "arrow.2.circlepath")
                                .font(.system(size: 10, weight: .semibold))
                        }
                        .font(.pipDisplay(16, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)

                        Text(phaseLabel)
                            .font(.pipBody(12))
                            .foregroundStyle(PipTheme.ink.opacity(0.45))
                    }
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)

                HStack(spacing: 8) {
                    Button(action: { showHistory = true }) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(PipTheme.ink)
                            .frame(width: 36, height: 36)
                            .background(PipTheme.ink.opacity(0.06))
                            .clipShape(Circle())
                    }

                    Button(action: endSession) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(PipTheme.ink)
                            .frame(width: 36, height: 36)
                            .background(PipTheme.ink.opacity(0.06))
                            .clipShape(Circle())
                    }
                }
            }
            .padding(.top, 20)
            .padding(.horizontal, 18)

            voiceModePicker
                .padding(.top, 14)

//            listeningModePicker
//                .padding(.top, 14)

            if let notice {
                Text(notice)
                    .font(.pipBody(12))
                    .foregroundStyle(PipTheme.accent)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(PipTheme.accent.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 24)
                    .padding(.top, 10)
                    .transition(.opacity)
            }

            if voice.serverState == .paused {
                Text("Oke, aku diam dulu ya. Bilang \u{201c}lanjut\u{201d} kalau mau aku dengerin lagi.")
                    .font(.pipBody(12))
                    .foregroundStyle(PipTheme.ink.opacity(0.55))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(PipTheme.ink.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 24)
                    .padding(.top, 10)
                    .transition(.opacity)
            }

            if voice.queueLength > 0 {
                Text("\(voice.queueLength) menunggu")
                    .font(.pipDisplay(11, weight: .semibold))
                    .foregroundStyle(PipTheme.ink.opacity(0.6))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(PipTheme.ink.opacity(0.08))
                    .clipShape(Capsule())
                    .padding(.top, 8)
                    .transition(.opacity)
            }

            #if DEBUG
            if let rtt = voice.lastRttMs {
                Text("RTT \(rtt)ms")
                    .font(.system(size: 10, weight: .regular, design: .monospaced))
                    .foregroundStyle(PipTheme.ink.opacity(0.35))
                    .padding(.top, 4)
            }
            #endif

            Spacer(minLength: 0)

            VStack(spacing: 20) {
                if let authorizationError {
                    Text(authorizationError)
                        .font(.pipBody(14))
                        .foregroundStyle(PipTheme.ink.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 260)
                } else if phase == .listening {
                    Text(transcript.isEmpty ? "Mendengarkan…" : transcript)
                        .font(.pipBody(14))
                        .foregroundStyle(PipTheme.ink.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 260)
                } else {
                    Text(finalQuestion)
                        .font(.pipBody(14))
                        .foregroundStyle(PipTheme.ink.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 240)
                }

                if phase == .thinking {
                    thinkingDots
                }

                blob

                if phase == .done {
                    Text(reply)
                        .font(.pipBody(15, weight: .medium))
                        .foregroundStyle(PipTheme.ink)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 260)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(PipTheme.ink.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 18))

                    if lastRequestId != nil {
                        feedbackButtons
                    }
                } else if phase == .listening {
                    Text(listeningMode == .ptt ? "tahan blob untuk bicara" : "tap untuk menyela")
                        .font(.pipBody(14))
                        .foregroundStyle(PipTheme.ink.opacity(0.5))
                }
            }
            .padding(.horizontal, 24)

            Spacer(minLength: 0)

            Button("Akhiri obrolan", action: endSession)
                .font(.pipDisplay(15, weight: .semibold))
                .foregroundStyle(Color(hex: 0xD64545))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color(hex: 0xD64545, opacity: 0.08))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
        }
        .background(PipTheme.cream.ignoresSafeArea())
        .onAppear {
            isBreathing = true
            avatar.setState(avatarState)
            Task { await requestAccessAndListen() }
        }
        .onDisappear {
            voice.stop()
            avatar.teardown()
            interruptedResetTask?.cancel()
            noticeResetTask?.cancel()
        }
        .onChange(of: avatarState) { _, newValue in
            avatar.setState(newValue)
        }
        .sheet(isPresented: $showHistory) {
            VoiceHistorySheet(messages: historyMessages)
                .task { historyMessages = await voice.loadHistory() }
        }
    }

    // MARK: - Avatar state (PRD-Pip-Avatar-Animation)

    /// Maps the backend's authoritative `voice.serverState` (driven by
    /// Mode A/B's real turn logic — see `app/voice/state_machine.py`) onto
    /// the PRD's 5-state avatar model, instead of reconstructing state here
    /// from `phase`/`voice.isSpeaking` side signals that can drift out of
    /// sync with what the server actually thinks is happening.
    private var avatarState: PipAvatarViewModel.State {
        if authorizationError != nil || !voice.isActive {
            return .idle
        }
        // `isInterruptedPulse` still gets its own deliberate minimum-dwell
        // timer (PRD-Pip-Avatar-Animation §4.1: 0.3s) — the server's
        // interrupted -> listening transition can happen faster than that,
        // and without this the avatar would barely flash interrupted at all.
        if isInterruptedPulse {
            return .interrupted
        }
        switch voice.serverState {
        case .idle, .paused, .cancelled, .disconnected, .reconnecting, .error, .waitingQueue, .completed:
            // `.cancelled` (a false-trigger turn Mode B abandoned before any
            // reply — PRD §10.1) is transient and immediately followed by a
            // `listening` state from the server; nothing distinct to show
            // for the brief moment in between, same as `.idle`. `.paused`
            // gets its own banner/label below (see `pausedBanner`/
            // `phaseLabel`) without a distinct avatar animation state — the
            // 5-state avatar model (PRD-Pip-Avatar-Animation) stays as-is.
            // `.waitingQueue`/`.completed` (Conversation Queue Management,
            // Mode B only) are brief turn-boundary states the backend
            // passes through almost immediately, same treatment as
            // `.cancelled`.
            return .idle
        case .listening, .recording:
            // `.recording`: user speech captured while Pip keeps talking
            // (Conversation Queue Management) — Pip's reply audio is still
            // playing, driven independently by `avatar.speakingLevel`/
            // `voice.isTalking`, so bucketing here doesn't suppress that.
            return .listening
        case .processing, .thinking, .queueProcessing:
            return .thinking
        case .speaking:
            return .speaking
        case .interrupted:
            return .interrupted
        }
    }

    private var breathingTarget: CGFloat {
        guard !reduceMotion, isBreathing else { return 1 }
        switch avatarState {
        case .idle: return 1.03
        case .listening: return 1.11
        case .thinking: return 1.06
        case .speaking, .interrupted: return 1.02
        }
    }

    private var breathingDuration: Double {
        switch avatarState {
        case .idle: return 2.0
        case .listening: return 0.7
        case .thinking: return 0.5
        case .speaking, .interrupted: return 1.0
        }
    }

    /// Glow ring intensity behind the blob — also doubles as the Reduce
    /// Motion-friendly state indicator (PRD §5.2 point 5), since it's an
    /// opacity change rather than a scale/motion one.
    private var glowOpacity: Double {
        switch avatarState {
        case .idle: return 0.05
        case .listening: return 0.16 + Double(avatar.listeningLevel) * 0.35
        case .thinking: return 0.22
        case .speaking: return 0.14 + Double(avatar.speakingLevel) * 0.45
        case .interrupted: return 0.05
        }
    }

    private var blob: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [buddyStore.selected.colorTo.opacity(glowOpacity), .clear],
                        center: .center, startRadius: 8, endRadius: 95
                    )
                )
                .frame(width: 190, height: 190)
                .blur(radius: 8)
                .allowsHitTesting(false)
                .animation(.easeOut(duration: 0.2), value: glowOpacity)

            BlobShape()
                .fill(
                    LinearGradient(
                        colors: [buddyStore.selected.colorFrom, buddyStore.selected.colorTo],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: buddyStore.selected.colorTo.opacity(0.45), radius: 30, y: 22)
            PipBlobFace(talkLevel: reduceMotion ? 0 : avatar.speakingLevel, eyeOpen: avatar.eyeOpen)
        }
        .frame(width: 180, height: 180)
        .scaleEffect(breathingTarget)
        .animation(
            .easeInOut(duration: breathingDuration).repeatForever(autoreverses: true),
            value: isBreathing
        )
        .animation(.easeInOut(duration: 0.3), value: avatarState)
        .animation(.easeInOut(duration: 0.25), value: buddyStore.selected)
        .scaleEffect(reduceMotion ? 1 : 1 + 0.25 * avatar.speakingLevel)
        .animation(.linear(duration: 0.05), value: avatar.speakingLevel)
        .scaleEffect(!reduceMotion && avatarState == .interrupted ? 0.9 : 1)
        .animation(.spring(response: 0.18, dampingFraction: 0.7), value: avatarState)
        .scaleEffect(voice.isTalking ? 1.08 : 1)
        .animation(.easeOut(duration: 0.15), value: voice.isTalking)
        .modifier(BlobInteraction(listeningMode: listeningMode, onTap: tapBlob, onHoldChange: handleHold))
    }

    /// Responsive/patient: tap to barge in. ptt: press-and-hold to talk —
    /// release is the turn boundary, guaranteeing the assistant heard the
    /// whole thing (VC-3 fallback).
    private var listeningModePicker: some View {
        HStack(spacing: 2) {
            ForEach(VoiceSocketClient.ListeningMode.allCases, id: \.self) { mode in
                Button(action: { switchListeningMode(to: mode) }) {
                    Text(label(for: mode))
                        .font(.pipDisplay(11, weight: .semibold))
                        .foregroundStyle(listeningMode == mode ? PipTheme.accent : PipTheme.ink.opacity(0.5))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(listeningMode == mode ? Color.white : Color.clear)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(listeningMode == mode ? 0.1 : 0), radius: 3, y: 1)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(PipTheme.ink.opacity(0.06))
        .clipShape(Capsule())
    }

    private func label(for mode: VoiceSocketClient.ListeningMode) -> String {
        switch mode {
        case .responsive: "Responsif"
        case .patient: "Sabar"
        case .ptt: "Tahan bicara"
        }
    }

    /// Dev-facing Mode A/B switch — same tap-to-switch-and-reconnect
    /// pattern as listeningModePicker below.
    private var voiceModePicker: some View {
        HStack(spacing: 2) {
            ForEach(["a", "b"], id: \.self) { mode in
                Button(action: { switchVoiceMode(to: mode) }) {
                    Text(mode == "a" ? "Mode A" : "Mode B")
                        .font(.pipDisplay(11, weight: .semibold))
                        .foregroundStyle(voiceMode == mode ? PipTheme.accent : PipTheme.ink.opacity(0.5))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(voiceMode == mode ? Color.white : Color.clear)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(voiceMode == mode ? 0.1 : 0), radius: 3, y: 1)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(PipTheme.ink.opacity(0.06))
        .clipShape(Capsule())
    }

    private var feedbackButtons: some View {
        HStack(spacing: 16) {
            Button(action: { sendFeedback(positive: true) }) {
                Image(systemName: "hand.thumbsup.fill")
                    .font(.system(size: 15))
                    .foregroundStyle(PipTheme.ink.opacity(feedbackSent ? 0.25 : 0.55))
            }
            Button(action: { sendFeedback(positive: false) }) {
                Image(systemName: "hand.thumbsdown.fill")
                    .font(.system(size: 15))
                    .foregroundStyle(PipTheme.ink.opacity(feedbackSent ? 0.25 : 0.55))
            }
        }
        .buttonStyle(.plain)
        .disabled(feedbackSent)
    }

    private func sendFeedback(positive: Bool) {
        guard let requestId = lastRequestId, !feedbackSent else { return }
        feedbackSent = true
        voice.sendFeedback(requestId: requestId, positive: positive)
    }

    private var thinkingDots: some View {
        HStack(spacing: 6) {
            ForEach(0..<3) { i in
                Circle()
                    .fill(PipTheme.accent)
                    .frame(width: 7, height: 7)
                    .offset(y: isBreathing ? -7 : 0)
                    .opacity(isBreathing ? 1 : 0.35)
                    .animation(
                        .easeInOut(duration: 0.6).repeatForever(autoreverses: true).delay(Double(i) * 0.15),
                        value: isBreathing
                    )
            }
        }
        .frame(height: 14)
    }

    private var phaseLabel: String {
        if voice.serverState == .paused {
            return "dijeda"
        }
        switch phase {
        case .listening: return "siap dengerin"
        case .thinking: return "mikir…"
        case .done: return "nih jawabannya"
        }
    }

    private func requestAccessAndListen() async {
        guard await voice.requestMicPermission() else {
            authorizationError = "Akses mikrofon belum diizinkan. Aktifkan lewat Pengaturan."
            return
        }
        await voice.setVoiceMode(voiceMode)
        await voice.setListeningMode(listeningMode)
        voice.start(listeningMode: listeningMode, onEvent: handle)
    }

    /// Mode A/B is fixed for the lifetime of a voice connection (same as
    /// listening mode) — switching means tearing down and reconnecting.
    private func switchVoiceMode(to mode: String) {
        guard mode != voiceMode else { return }
        voiceMode = mode
        phase = .listening
        transcript = ""
        reply = ""
        voice.stop()
        Task {
            await voice.setVoiceMode(mode)
            voice.start(listeningMode: listeningMode, onEvent: handle)
        }
    }

    /// Turn-detection sensitivity (Mode A) / VAD threshold (Mode B) are
    /// fixed for the lifetime of a voice connection, so switching modes
    /// mid-session means tearing down and reconnecting.
    private func switchListeningMode(to mode: VoiceSocketClient.ListeningMode) {
        guard mode != listeningMode else { return }
        listeningMode = mode
        phase = .listening
        transcript = ""
        reply = ""
        voice.stop()
        Task {
            await voice.setListeningMode(mode)
            voice.start(listeningMode: mode, onEvent: handle)
        }
    }

    private func handleHold(_ pressing: Bool) {
        if pressing {
            voice.beginTalking()
            phase = .listening
            transcript = ""
        } else {
            voice.endTalking()
            phase = .thinking
        }
    }

    private func handle(_ event: VoiceSocketClient.Event) {
        switch event {
        case .transcript(let text, let isFinal):
            transcript = text
            if isFinal {
                finalQuestion = text
                phase = .thinking
                lastRequestId = nil
            }
        case .reply(let text, let isFinal, let requestId):
            reply = text
            phase = .done
            if isFinal {
                lastRequestId = requestId
                feedbackSent = false
            }
        case .interrupt:
            phase = .listening
            reply = ""
            transcript = ""
            lastRequestId = nil
            triggerInterruptedPulse()
        case .error(let message):
            authorizationError = message
        case .voiceDetected:
            // Instant local feedback (0 network round trip) the moment the
            // mic itself notices real speech — overwritten the moment a
            // real transcript delta/final arrives from the server.
            if phase == .listening, transcript.isEmpty {
                transcript = "Oke, aku denger nih"
            }
        case .notice(let message):
            showNotice(message)
        }
    }

    /// One-shot banner (e.g. Ollama fallback notice) — auto-dismisses so
    /// it doesn't permanently occupy space above the blob for the rest of
    /// the session.
    private func showNotice(_ message: String) {
        noticeResetTask?.cancel()
        withAnimation { notice = message }
        noticeResetTask = Task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard !Task.isCancelled else { return }
            withAnimation { notice = nil }
        }
    }

    /// Transient "interrupted" avatar state (PRD §4.1: `interrupted --(0.3s)-->
    /// listening`) — the underlying playback/transcript state already reset
    /// synchronously above; this just gives the avatar a beat to react.
    private func triggerInterruptedPulse() {
        interruptedResetTask?.cancel()
        isInterruptedPulse = true
        interruptedResetTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            isInterruptedPulse = false
        }
    }

    private func tapBlob() {
        guard phase == .done else { return }
        voice.stopPlayback()
        phase = .listening
        reply = ""
        transcript = ""
        lastRequestId = nil
    }

    private func endSession() {
        voice.stop()
        onEnd()
    }
}

/// Read-only past turns for this voice session — same `messages` table/
/// session_id as the Chat screen's history (both mode_b_pipeline.py and
/// voice_mode_a.py persist voice turns via `session_store.add_message`),
/// fetched via `VoiceSocketClient.loadHistory()`. Already scoped to the
/// logged-in user: `GET /api/session/{id}/history` 403s on a session_id
/// owned by someone else, so there's nothing further to filter here.
private struct VoiceHistorySheet: View {
    let messages: [VoiceSocketClient.HistoryMessage]

    var body: some View {
        NavigationStack {
            ScrollView {
                if messages.isEmpty {
                    Text("Belum ada riwayat untuk sesi ini.")
                        .font(.pipBody(13))
                        .foregroundStyle(PipTheme.ink.opacity(0.5))
                        .padding(.top, 24)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(messages.enumerated()), id: \.offset) { _, message in
                            HStack {
                                if message.isUser { Spacer(minLength: 40) }
                                Text(message.text)
                                    .font(.pipBody(14))
                                    .foregroundStyle(message.isUser ? .white : PipTheme.ink)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(message.isUser ? PipTheme.accent : PipTheme.ink.opacity(0.06))
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                                if !message.isUser { Spacer(minLength: 40) }
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .background(PipTheme.cream)
            .navigationTitle("Riwayat obrolan suara")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// Swaps the blob's gesture based on listening mode: tap-to-barge-in for
/// responsive/patient, press-and-hold-to-talk for ptt.
private struct BlobInteraction: ViewModifier {
    let listeningMode: VoiceSocketClient.ListeningMode
    let onTap: () -> Void
    let onHoldChange: (Bool) -> Void

    func body(content: Content) -> some View {
        if listeningMode == .ptt {
            content.onLongPressGesture(minimumDuration: 0, pressing: onHoldChange, perform: {})
        } else {
            content.onTapGesture(perform: onTap)
        }
    }
}

#Preview {
    VoiceSessionView(onEnd: {})
}
