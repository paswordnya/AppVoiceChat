//
//  ContentView.swift
//  Pip
//
//  Created by Rakka on 11/07/26.
//

import PipShared
import SwiftUI

#if DEBUG
import PulseUI
#endif

private enum AppStage {
    case splash
    case onboarding
    case login
    case signup
    case main
}

struct ContentView: View {
    @State private var stage: AppStage = .splash
    #if DEBUG
    @State private var isNetworkConsolePresented = false
    #endif

    var body: some View {
        ZStack {
            switch stage {
            case .splash:
                SplashView {
                    // Local-only check (KeyValueStore, no network) — already
                    // logged in skips both Onboarding and Login entirely.
                    if IOSAccessorsKt.getSplashViewModel().isLoggedIn {
                        advance(to: .main)
                    } else {
                        advance(to: .onboarding)
                    }
                }
                .transition(.opacity)

            case .onboarding:
                OnboardingView {
                    advance(to: .login)
                }
                .transition(.opacity)

            case .login:
                LoginView(onLoggedIn: { advance(to: .main) }, onGoToSignup: { advance(to: .signup) })
                    .transition(.opacity)

            case .signup:
                SignupView(onSignedUp: { advance(to: .main) }, onGoToLogin: { advance(to: .login) })
                    .transition(.opacity)

            case .main:
                MainView()
                    .transition(.opacity)
            }

            #if DEBUG
            networkConsoleButton
            #endif
        }
        #if DEBUG
        .sheet(isPresented: $isNetworkConsolePresented) {
            NavigationView { ConsoleView() }
        }
        #endif
    }

    #if DEBUG
    // Bottom-LEADING, well clear of the composer's bottom-trailing mic/send
    // buttons on the main chat screen (Splash/Onboarding/Auth have nothing
    // there either way) — bottom-trailing was where this used to sit, and
    // it rendered fine on Splash but was invisible/untappable on the chat
    // screen because the composer's own send button occupies that exact
    // corner too.
    private var networkConsoleButton: some View {
        VStack {
            Spacer()
            HStack {
                Button {
                    isNetworkConsolePresented = true
                } label: {
                    Image(systemName: "network")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(12)
                        .background(.blue.opacity(0.85), in: Circle())
                        .shadow(radius: 4)
                }
                .padding(.leading, 16)
                .padding(.bottom, 100)
                Spacer()
            }
        }
    }
    #endif

    private func advance(to next: AppStage) {
        withAnimation(.easeOut(duration: 0.3)) {
            stage = next
        }
    }
}

#Preview {
    ContentView()
}
