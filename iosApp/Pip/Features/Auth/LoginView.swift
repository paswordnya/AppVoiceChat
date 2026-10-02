import SwiftUI

/// Email/password login — the auth screen `OnboardingView` deferred.
struct LoginView: View {
    var onLoggedIn: () -> Void
    var onGoToSignup: () -> Void

    private let auth = AuthClient()
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isSubmitting = false

    private var canSubmit: Bool {
        !email.isEmpty && !password.isEmpty && !isSubmitting
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 22) {
                ZStack {
                    BlobShape()
                        .fill(
                            LinearGradient(
                                colors: [PipTheme.buddyFrom, PipTheme.buddyTo],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: PipTheme.buddyTo.opacity(0.4), radius: 25, y: 18)
                    PipBlobFace()
                }
                .frame(width: 110, height: 110)

                VStack(spacing: 10) {
                    Text("Welcome back")
                        .font(.pipDisplay(26, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)

                    Text("Masuk untuk lanjut ngobrol sama Pip.")
                        .font(.pipBody(15))
                        .foregroundStyle(PipTheme.ink.opacity(0.6))
                        .multilineTextAlignment(.center)
                }
            }

            Spacer()

            VStack(spacing: 14) {
                if let errorMessage {
                    Text(errorMessage)
                        .font(.pipBody(13))
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }

                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                SecureField("Password", text: $password)
                    .textContentType(.password)
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                Button(action: submit) {
                    Group {
                        if isSubmitting {
                            ProgressView().tint(.white)
                        } else {
                            Text("Masuk")
                                .font(.pipDisplay(16, weight: .semibold))
                        }
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(canSubmit ? PipTheme.accent : PipTheme.accent.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .disabled(!canSubmit)

                Button("Belum punya akun? Daftar", action: onGoToSignup)
                    .font(.pipBody(14, weight: .medium))
                    .foregroundStyle(PipTheme.accent)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 20)
        .padding(.bottom, 46)
        .background(PipTheme.cream.ignoresSafeArea())
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        Task {
            let result = await auth.login(email: email, password: password)
            isSubmitting = false
            switch result {
            case .success:
                onLoggedIn()
            case .failure(let message):
                errorMessage = message
            }
        }
    }
}

#Preview {
    LoginView(onLoggedIn: {}, onGoToSignup: {})
}
