import Foundation
import PipShared

/// Bridge to the shared module's `AuthViewModel` — owns the Kotlin
/// ViewModel via `IOSAccessorsKt`, exposes a Swift-native async API so
/// `LoginView`/`SignupView` never touch Koin/KMP types directly. Not
/// `ObservableObject` (unlike `ChatSocketClient`) — there's no `@Published`
/// state to observe here, `login`/`signup` return their outcome directly.
@MainActor
final class AuthClient {
    // Named AuthOutcome, not `Result` — shadowing Swift's stdlib `Result` in a
    // file that also does Kotlin suspend/async interop broke type inference
    // on the `try await` call sites below (found via a real build failure).
    enum AuthOutcome {
        case success
        case failure(String)
    }

    private let viewModel: AuthViewModel

    init() {
        viewModel = IOSAccessorsKt.getAuthViewModel()
    }

    /// True if a token from a previous login/signup is already stored —
    /// local-only (`KeyValueStore`), no network, safe to call at splash time.
    var isLoggedIn: Bool {
        IOSAccessorsKt.getSplashViewModel().isLoggedIn
    }

    func login(email: String, password: String) async -> AuthOutcome {
        do {
            let state = try await viewModel.login(email: email, password: password)
            return Self.map(state)
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    func signup(email: String, password: String, yearOfBirth: Int?) async -> AuthOutcome {
        do {
            let yearOfBirthKotlin: KotlinInt? = yearOfBirth.map { KotlinInt(int: Int32($0)) }
            let state = try await viewModel.signup(email: email, password: password, yearOfBirth: yearOfBirthKotlin)
            return Self.map(state)
        } catch {
            return .failure("Can't reach Pip's backend. Is it running?")
        }
    }

    private static func map(_ state: AuthUiState) -> AuthOutcome {
        switch onEnum(of: state) {
        case .success:
            return .success
        case .error(let e):
            return .failure(e.message)
        case .idle, .loading:
            return .failure("Unexpected state.")
        }
    }
}
