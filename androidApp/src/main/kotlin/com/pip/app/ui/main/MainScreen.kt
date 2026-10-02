package com.pip.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pip.app.ui.chat.ChatSessionScreen
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.voice.VoiceSessionScreen

/**
 * Thin host, ported 1:1 from iOS's `MainView.swift` real (audited)
 * behavior — embeds Chat inline as the default surface, presents Voice
 * full-screen when triggered from Chat's mic button (PRD §4.2/§13a).
 */
@Composable
fun MainScreen() {
    var showVoice by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        ChatSessionScreen(onRequestVoice = { showVoice = true })

        if (showVoice) {
            VoiceSessionScreen(onEnd = { showVoice = false })
        }
    }
}
