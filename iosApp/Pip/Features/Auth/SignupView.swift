import SwiftUI

/// Email/password signup, plus year of birth — the auth screen
/// `OnboardingView` deferred.
struct SignupView: View {
    var onSignedUp: () -> Void
    var onGoToLogin: () -> Void

    private let auth = AuthClient()
    @State private var email = ""
    @State private var password = ""
    @State private var yearOfBirth = ""
    @State private var errorMessage: String?
    @State private var isSubmitting = false

    private var canSubmit: Bool {
        !email.isEmpty && password.count >= 8 && !isSubmitting
    }

    var body: some View {
        ScrollView {
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
                .padding(.top, 40)

                VStack(spacing: 10) {
                    Text("Buat akun")
                        .font(.pipDisplay(26, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)

                    Text("Daftar dulu buat mulai ngobrol sama Pip.")
                        .font(.pipBody(15))
                        .foregroundStyle(PipTheme.ink.opacity(0.6))
                        .multilineTextAlignment(.center)
                }

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

                    SecureField("Password (min. 8 karakter)", text: $password)
                        .textContentType(.newPassword)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))

                    TextField("Tahun lahir (opsional)", text: $yearOfBirth)
                        .keyboardType(.numberPad)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))

                    Button(action: submit) {
                        Group {
                            if isSubmitting {
                                ProgressView().tint(.white)
                            } else {
                                Text("Daftar")
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

                    Button("Sudah punya akun? Masuk", action: onGoToLogin)
                        .font(.pipBody(14, weight: .medium))
                        .foregroundStyle(PipTheme.accent)
                        .padding(.top, 4)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 46)
        }
        .background(PipTheme.cream.ignoresSafeArea())
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        let parsedYear = Int(yearOfBirth)
        Task {
            let result = await auth.signup(email: email, password: password, yearOfBirth: parsedYear)
            isSubmitting = false
            switch result {
            case .success:
                onSignedUp()
            case .failure(let message):
                errorMessage = message
            }
        }
    }
}

#Preview {
    SignupView(onSignedUp: {}, onGoToLogin: {})
}
