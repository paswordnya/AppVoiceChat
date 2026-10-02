import Foundation
import PipShared

/// Bridge to the shared module's `ProfileViewModel` — the Adaptive
/// Conversation Engine's Privacy surface (PRD_TDD_pip_Voice_AI.md §23.4:
/// view/edit/reset/delete). Same shape as `AuthClient`: owns the Kotlin
/// ViewModel via `IOSAccessorsKt`, exposes a Swift-native async API.
@MainActor
final class ProfileClient {
    // Named ProfileOutcome, not `Result` — shadowing Swift's stdlib `Result`
    // broke type inference on `try await` call sites in AuthClient.swift
    // (found via a real build failure there) — same file shape here, same fix.
    enum ProfileOutcome {
        case success(ProfileDto)
        case failure(String)
    }

    private let viewModel: ProfileViewModel

    init() {
        viewModel = IOSAccessorsKt.getProfileViewModel()
    }

    func load() async -> ProfileOutcome {
        do {
            return Self.map(try await viewModel.load())
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    func update(
        preferredLanguage: String? = nil,
        communicationStyle: String? = nil,
        preferredTone: String? = nil,
        preferredResponseLength: String? = nil,
        technicalLevel: String? = nil,
        humorPreference: String? = nil,
        emojiPreference: String? = nil
    ) async -> ProfileOutcome {
        let update = ProfileUpdateDto(
            preferredLanguage: preferredLanguage,
            communicationStyle: communicationStyle,
            preferredTone: preferredTone,
            preferredResponseLength: preferredResponseLength,
            technicalLevel: technicalLevel,
            humorPreference: humorPreference,
            emojiPreference: emojiPreference
        )
        do {
            return Self.map(try await viewModel.update(update: update))
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    func reset() async -> ProfileOutcome {
        do {
            return Self.map(try await viewModel.reset())
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    func delete() async -> Bool {
        do {
            switch onEnum(of: try await viewModel.delete()) {
            case .success:
                return true
            case .failure:
                return false
            }
        } catch {
            return false
        }
    }

    func loadHistory(limit: Int32 = 50) async -> [ProfileHistoryEntryDto] {
        do {
            switch onEnum(of: try await viewModel.loadHistory(limit: limit)) {
            case .success(let s):
                return (s.value as? [ProfileHistoryEntryDto]) ?? []
            case .failure:
                return []
            }
        } catch {
            return []
        }
    }

    private static func map(_ result: PipResult<ProfileDto>) -> ProfileOutcome {
        switch onEnum(of: result) {
        case .success(let s):
            guard let value = s.value else { return .failure("Data profil tidak lengkap.") }
            return .success(value)
        case .failure(let f):
            return .failure(Self.message(for: f.error))
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
