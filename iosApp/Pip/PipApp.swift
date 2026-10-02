//
//  PipApp.swift
//  Pip
//
//  Created by Rakka on 11/07/26.
//

import PipShared
import SwiftUI

#if DEBUG
import Pulse
#endif

@main
struct PipApp: App {
    init() {
        #if DEBUG
        // Swizzles URLSession itself, so it captures every delegate-based
        // session app-wide — the shared KMP module's Ktor/Darwin client
        // (KtorHttpClient.kt) included — without touching the call site.
        // View captured traffic via the network icon in ContentView (debug
        // builds only).
        URLSessionProxyDelegate.enableAutomaticRegistration()
        #endif

        // PRD-KMP-Migration-v2.md §19 Sprint 6 — must run before any
        // shared-module class (ChatSocketClient's ChatSessionViewModel,
        // etc.) is constructed.
        #if DEBUG
        KoinIOSKt.doInitKoinIOS(isDebug: true, apiBaseUrl: nil)
        #else
        KoinIOSKt.doInitKoinIOS(isDebug: false, apiBaseUrl: nil)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            if ProcessInfo.processInfo.environment["PIP_UI_PREVIEW_LINKS"] == "1" {
                KeyboardSessionView()
            } else {
                ContentView()
            }
        }
    }
}
