import SwiftUI

/// First onboarding step from the moc ("Hi, I'm Pip"): mascot intro with a
/// skip affordance and a primary CTA. Per product direction, both "Skip"
/// and "Continue with Pip" lead to Login (see `ContentView`'s `AppStage`) —
/// the mic-permission and buddy-picker steps (onboard2/onboard3) are still
/// deferred, but auth is now wired up (`LoginView`/`SignupView`).
struct OnboardingView: View {
    var onFinished: () -> Void

    @State private var isBreathing = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("Skip", action: onFinished)
                    .font(.pipBody(14))
                    .foregroundStyle(PipTheme.ink.opacity(0.45))
            }

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
                .frame(width: 150, height: 150)
                .scaleEffect(isBreathing ? 1.045 : 1)
                .animation(.easeInOut(duration: 1.7).repeatForever(autoreverses: true), value: isBreathing)

                VStack(spacing: 10) {
                    Text("Hi, I'm Pip.")
                        .font(.pipDisplay(28, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)

                    Text("Your calm little sidekick. Talk to me — I listen, think, and react.")
                        .font(.pipBody(15))
                        .foregroundStyle(PipTheme.ink.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                }
                .padding(.horizontal, 20)
            }

            Spacer()

            Button(action: onFinished) {
                Text("Continue with Pip")
                    .font(.pipDisplay(16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(PipTheme.accent)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 20)
        .padding(.bottom, 46)
        .background(PipTheme.cream.ignoresSafeArea())
        .onAppear { isBreathing = true }
    }
}

#Preview {
    OnboardingView(onFinished: {})
}
