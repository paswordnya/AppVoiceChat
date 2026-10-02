import Foundation
import PipShared

/// Bridge to the shared module's `SettingsViewModel` — owns the Kotlin
/// ViewModel via `IOSAccessorsKt`, exposes a Swift-native async API. Same
/// shape as `ProfileClient`/`AuthClient`: buddy/personality/voice & reaction
/// toggles/theme/privacy, synced cross-device via backend `/settings`. Each
/// method below maps 1:1 to a `SettingsViewModel` method — the toggles take
/// no arguments Kotlin-side (it flips its own cached last-loaded value), so
/// no `current` param needs threading through from the view.
/// Distinct from `ProfileClient`, which is the Adaptive Conversation
/// Engine's learned-preference concept ("Personalisasi").
@MainActor
final class SettingsClient {
    enum SettingsOutcome {
        case success(UserSettingsDto)
        case failure(String)
    }

    private let viewModel: SettingsViewModel

    init() {
        viewModel = IOSAccessorsKt.getSettingsViewModel()
    }

    func load() async -> SettingsOutcome { await run { try await self.viewModel.load() } }

    func setBuddy(_ buddy: String) async -> SettingsOutcome { await run { try await self.viewModel.setBuddy(buddy: buddy) } }

    func setPersonality(_ personality: String) async -> SettingsOutcome {
        await run { try await self.viewModel.setPersonality(personality: personality) }
    }

    func toggleVoiceReplies() async -> SettingsOutcome { await run { try await self.viewModel.toggleVoiceReplies() } }

    func toggleAnimatedReactions() async -> SettingsOutcome {
        await run { try await self.viewModel.toggleAnimatedReactions() }
    }

    func toggleHaptics() async -> SettingsOutcome { await run { try await self.viewModel.toggleHaptics() } }

    func setSpeakingSpeed(_ speed: Int32) async -> SettingsOutcome {
        await run { try await self.viewModel.setSpeakingSpeed(speed: speed) }
    }

    func setBrandColor(_ hex: String) async -> SettingsOutcome { await run { try await self.viewModel.setBrandColor(hex: hex) } }

    func toggleStoreConversations() async -> SettingsOutcome {
        await run { try await self.viewModel.toggleStoreConversations() }
    }

    /// Clears the local scrollback cache only — there's no server-side
    /// "delete this account's message history" endpoint today.
    func clearHistory() {
        viewModel.clearHistory()
    }

    private func run(_ call: () async throws -> PipResult<UserSettingsDto>) async -> SettingsOutcome {
        do {
            return Self.map(try await call())
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    private static func map(_ result: PipResult<UserSettingsDto>) -> SettingsOutcome {
        switch onEnum(of: result) {
        case .success(let s):
            guard let value = s.value else { return .failure("Data settings tidak lengkap.") }
            return .success(value)
        case .failure(let f):
            return .failure(message(for: f.error))
        }
    }

    private static func message(for error: PipError) -> String {
        switch onEnum(of: error) {
        case .unauthorized:
            return "Sesi login sudah tidak valid — coba login lagi."
        case .noConnectivity:
            return "Tidak bisa terhubung ke server. Coba lagi."
        case .server(let s):
            return "Server error (\(s.statusCode))."
        default:
            return "Terjadi kesalahan. Coba lagi."
        }
    }
}
