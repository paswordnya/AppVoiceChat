import Combine
import Foundation
import PipShared

/// Bridge to the shared module's `ChatSessionViewModel` (PRD-KMP-Migration-v2.md
/// §8, migrated per §19 Sprint 6 — was a raw `URLSessionWebSocketTask`
/// managing `/ws/chat/{session_id}` directly). Preserves the exact public
/// API this class had before the migration, so `KeyboardSessionView.swift`
/// needed zero changes: same `Event` cases, same `connect`/`send`/
/// `disconnect` signatures.
@MainActor
final class ChatSocketClient: ObservableObject {
    enum Event {
        case thinkingStarted
        case token(String)
        case done(text: String, model: String)
        case command(text: String)
        case error(String)
        /// One-shot informational message, distinct from `error` — e.g.
        /// the backend degraded to its local Ollama fallback because
        /// every cloud LLM hit its limit. The reply still came through
        /// fine; this is just letting the user know it's running on a
        /// slower/weaker fallback for now.
        case notice(String)
    }

    /// Flat shape `KeyboardSessionView` maps into its own private
    /// `ChatMessage`/`ReplyReference` structs — mirrors the pre-migration
    /// `RemoteChatMessage` that lived in `ChatHistoryEndpoint.swift`.
    struct HistoryMessage {
        let isUser: Bool
        let text: String
        let replySenderLabel: String?
        let replySnippet: String?
        let createdAt: String
    }

    @Published private(set) var isConnected = false

    private let viewModel: ChatSessionViewModel
    private var onEvent: ((Event) -> Void)?
    private var eventsTask: Task<Void, Never>?

    init() {
        viewModel = IOSAccessorsKt.getChatSessionViewModel()
    }

    /// Restores prior turns (CH-4) — `GET /api/session/{id}/history` via
    /// the shared module instead of the native `ChatHistoryEndpoint`.
    func loadHistory() async -> [HistoryMessage] {
        _ = try? await viewModel.loadHistory()
        guard let conversation = viewModel.history.value as? Conversation else { return [] }
        return conversation.messages.map { message in
            HistoryMessage(
                isUser: message.role == .user,
                text: message.content,
                replySenderLabel: message.replySenderLabel,
                replySnippet: message.replySnippet,
                createdAt: message.createdAt
            )
        }
    }

    func connect(onEvent: @escaping (Event) -> Void) {
        self.onEvent = onEvent
        guard eventsTask == nil else { return }
        viewModel.connect()
        isConnected = true

        eventsTask = Task { [weak self] in
            guard let self else { return }
            for await event in self.viewModel.events {
                self.handle(event)
            }
        }
    }

    func send(message: String, replySenderLabel: String? = nil, replySnippet: String? = nil) {
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await self.viewModel.send(text: message, replySenderLabel: replySenderLabel, replySnippet: replySnippet)
            } catch {
                self.onEvent?(.error("Can't reach Pip's backend. Is it running?"))
            }
        }
    }

    func disconnect() {
        eventsTask?.cancel()
        eventsTask = nil
        viewModel.disconnect()
        isConnected = false
    }

    private func handle(_ event: ChatServerEvent) {
        switch onEnum(of: event) {
        case .thinkingStarted:
            onEvent?(.thinkingStarted)
        case .token(let e):
            onEvent?(.token(e.text))
        case .done(let e):
            onEvent?(.done(text: e.text, model: e.model))
        case .command(let e):
            onEvent?(.command(text: e.text))
        case .error(let e):
            onEvent?(.error(e.message))
        case .notice(let e):
            onEvent?(.notice(e.text))
        }
    }
}
