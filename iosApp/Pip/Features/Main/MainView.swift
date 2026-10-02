import SwiftUI

/// The "core loop" home screen from the moc: a Voice/Keyboard segmented
/// control and a big tappable mascot blob that walks through a idle ->
/// listening -> thinking -> done cycle.
///
/// Per product direction, the top toolbar (history/profile icons) is
/// intentionally left out for now — this ships the core tap-to-talk loop
/// plus a lightweight buddy switcher (tap the name pill to cycle mascots).
struct MainView: View {
    private enum InputMode {
        case voice
        case keyboard
    }

    @ObservedObject private var buddyStore = BuddyStore.shared
    @State private var inputMode: InputMode = .voice
    @State private var isBreathing = false
    @State private var isVoiceSessionPresented = false
    @State private var isKeyboardSessionPresented = false

    var body: some View {
        VStack(spacing: 0) {
//            segmentedControl
//                .padding(.top, 58)
//                .padding(.bottom, 12)
//
//            Spacer(minLength: 0)

            KeyboardSessionView()
                .frame(maxHeight: .infinity)
        }.frame(maxWidth: .infinity, alignment: .center)
        .background(PipTheme.cream.ignoresSafeArea())
        .onAppear { isBreathing = true }
        .fullScreenCover(isPresented: $isVoiceSessionPresented) {
            VoiceSessionView { isVoiceSessionPresented = false }
        }
        .fullScreenCover(isPresented: $isKeyboardSessionPresented) {
            KeyboardSessionView()
        }
    }

    private var segmentedControl: some View {
        HStack(spacing: 2) {
            segmentButton("Voice", isActive: inputMode == .voice) { inputMode = .voice }
            segmentButton("Keyboard", isActive: inputMode == .keyboard) { inputMode = .keyboard }
        }
        .padding(3)
        .background(PipTheme.ink.opacity(0.06))
        .clipShape(Capsule())
    }

    private func segmentButton(_ title: String, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.pipDisplay(13, weight: .semibold))
                .foregroundStyle(isActive ? PipTheme.accent : PipTheme.ink)
                .padding(.horizontal, 18)
                .padding(.vertical, 7)
                .background(isActive ? Color.white : Color.clear)
                .clipShape(Capsule())
                .shadow(color: .black.opacity(isActive ? 0.12 : 0), radius: 4, y: 1)
        }
        .buttonStyle(.plain)
    }

    private var voiceContent: some View {
        VStack(spacing: 18) {
            buddySwitcher

            ZStack {
                BlobShape()
                    .fill(
                        LinearGradient(
                            colors: [buddyStore.selected.colorFrom, buddyStore.selected.colorTo],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: buddyStore.selected.colorTo.opacity(0.45), radius: 30, y: 22)
                PipBlobFace()
            }
            .frame(width: 180, height: 180)
            .scaleEffect(isBreathing ? 1.045 : 1)
            .animation(.easeInOut(duration: 1.7).repeatForever(autoreverses: true), value: isBreathing)
            .animation(.easeInOut(duration: 0.25), value: buddyStore.selected)
            .onTapGesture { isVoiceSessionPresented = true }

            Text("tap to talk")
                .font(.pipDisplay(16, weight: .semibold))
                .foregroundStyle(PipTheme.ink)

            Text("tap the blob to talk")
                .font(.pipBody(14))
                .foregroundStyle(PipTheme.ink.opacity(0.5))
        }
    }

    private var buddySwitcher: some View {
        Button(action: buddyStore.cycleNext) {
            HStack(spacing: 6) {
                Text(buddyStore.selected.name)
                Image(systemName: "arrow.2.circlepath")
                    .font(.system(size: 11, weight: .semibold))
            }
            .font(.pipDisplay(14, weight: .semibold))
            .foregroundStyle(PipTheme.ink.opacity(0.45))
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(PipTheme.ink.opacity(0.05))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var keyboardContent: some View {
        VStack(spacing: 18) {
            Button {
                isKeyboardSessionPresented = true
            } label: {
                VStack(spacing: 10) {
                    Text("💬")
                        .font(.system(size: 28))
                    Text("Tap to start chatting")
                        .font(.pipDisplay(15, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .background(PipTheme.ink.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
            .buttonStyle(.plain)

            Text("Type your message to begin the conversation.")
                .font(.pipBody(14))
                .foregroundStyle(PipTheme.ink.opacity(0.5))
                .multilineTextAlignment(.center)
        }
    }

}

#Preview {
    MainView()
}
