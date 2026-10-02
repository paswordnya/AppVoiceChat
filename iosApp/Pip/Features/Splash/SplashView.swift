import SwiftUI

/// Launch splash, matching `moc/Pip Prototype (standalone).html`'s "Splash"
/// screen: gradient background, floating dots, a breathing mascot blob,
/// wordmark + welcome copy, and a bouncing-dot loader with rotating status
/// text. Auto-advances after ~2.7s; tapping anywhere skips immediately.
struct SplashView: View {
    var onFinished: () -> Void

    private let loadingMessages = [
        "Getting everything ready…",
        "Preparing your AI assistant…",
        "Almost there…",
    ]

    private struct Floater: Identifiable {
        let id: Int
        var top: CGFloat?
        var left: CGFloat?
        var bottom: CGFloat?
        var right: CGFloat?
        let size: CGFloat
        let delay: Double
        let duration: Double
    }

    private let floaters: [Floater] = [
        Floater(id: 0, top: 0.14, left: 0.12, bottom: nil, right: nil, size: 46, delay: 0, duration: 5.5),
        Floater(id: 1, top: 0.20, left: nil, bottom: nil, right: 0.14, size: 30, delay: 0.6, duration: 4.8),
        Floater(id: 2, top: nil, left: 0.10, bottom: 0.22, right: nil, size: 34, delay: 1.1, duration: 6.2),
        Floater(id: 3, top: nil, left: nil, bottom: 0.16, right: 0.10, size: 52, delay: 0.3, duration: 5.0),
    ]

    @State private var messageIndex = 0
    @State private var isBreathing = false
    @State private var bounceDots = false
    @State private var floatUp = false
    @State private var hasFinished = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [PipTheme.accent, PipTheme.accentDeep],
                startPoint: UnitPoint(x: 0.35, y: 0),
                endPoint: UnitPoint(x: 0.65, y: 1)
            )

            GeometryReader { proxy in
                ForEach(floaters) { floater in
                    Circle()
                        .fill(Color.white.opacity(0.16))
                        .frame(width: floater.size, height: floater.size)
                        .position(
                            x: floater.left.map { $0 * proxy.size.width + floater.size / 2 }
                                ?? proxy.size.width - (floater.right ?? 0) * proxy.size.width - floater.size / 2,
                            y: (floater.top.map { $0 * proxy.size.height }
                                ?? proxy.size.height - (floater.bottom ?? 0) * proxy.size.height - floater.size)
                                + (floatUp ? -18 : 0)
                        )
                        .animation(
                            .easeInOut(duration: floater.duration)
                                .repeatForever(autoreverses: true)
                                .delay(floater.delay),
                            value: floatUp
                        )
                }
            }

            VStack {
                Spacer(minLength: 0)

                VStack(spacing: 14) {
                    ZStack {
                        BlobShape()
                            .fill(Color.white.opacity(0.24))
                            .shadow(color: .black.opacity(0.25), radius: 25, y: 20)
                        PipBlobFace(color: .white)
                    }
                    .frame(width: 120, height: 120)
                    .scaleEffect(isBreathing ? 1.045 : 1)
                    .animation(.easeInOut(duration: 1.7).repeatForever(autoreverses: true), value: isBreathing)

                    Text("Pip")
                        .font(.pipDisplay(32, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.top, 2)

                    Text("Welcome! 👋")
                        .font(.pipDisplay(20, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.top, 6)

                    Text("Your AI assistant is ready to help you chat, create, and get things done.")
                        .font(.pipBody(14))
                        .foregroundStyle(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 260)
                        .lineSpacing(4)

                    VStack(spacing: 10) {
                        HStack(spacing: 7) {
                            ForEach(0..<3) { i in
                                Circle()
                                    .fill(.white)
                                    .frame(width: 9, height: 9)
                                    .scaleEffect(bounceDots ? 1 : 0.7)
                                    .opacity(bounceDots ? 1 : 0.5)
                                    .animation(
                                        .easeInOut(duration: 0.55)
                                            .repeatForever(autoreverses: true)
                                            .delay(Double(i) * 0.15),
                                        value: bounceDots
                                    )
                            }
                        }

                        Text(loadingMessages[messageIndex])
                            .font(.pipBody(13))
                            .foregroundStyle(.white.opacity(0.75))
                            .contentTransition(.opacity)
                            .id(messageIndex)
                            .transition(.opacity)
                    }
                    .padding(.top, 22)
                }
                .padding(.horizontal, 32)

                Spacer(minLength: 0)
            }
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture(perform: finish)
        .onAppear {
            isBreathing = true
            bounceDots = true
            floatUp = true

            Timer.scheduledTimer(withTimeInterval: 0.9, repeats: true) { timer in
                if hasFinished {
                    timer.invalidate()
                    return
                }
                withAnimation {
                    messageIndex = (messageIndex + 1) % loadingMessages.count
                }
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 2.7, execute: finish)
        }
    }

    private func finish() {
        guard !hasFinished else { return }
        hasFinished = true
        onFinished()
    }
}

#Preview {
    SplashView(onFinished: {})
}
