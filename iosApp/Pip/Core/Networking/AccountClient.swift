import Foundation
import PipShared

/// Bridge to the shared module's `AccountViewModel` — the redesign's
/// "Profile" screen (personal account info: email, year of birth). Named
/// `Account`, not `Profile`, to avoid clashing with `ProfileClient` (the
/// Adaptive Conversation Engine's learned-preference screen, relabeled
/// "Personalisasi" and linked from here instead).
@MainActor
final class AccountClient {
    enum AccountOutcome {
        case success(AuthUserDto)
        case failure(String)
    }

    private let viewModel: AccountViewModel

    init() {
        viewModel = IOSAccessorsKt.getAccountViewModel()
    }

    func load() async -> AccountOutcome {
        do {
            switch onEnum(of: try await viewModel.load()) {
            case .success(let s):
                guard let value = s.value else { return .failure("Data akun tidak lengkap.") }
                return .success(value)
            case .failure(let f):
                return .failure(Self.message(for: f.error))
            }
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
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
