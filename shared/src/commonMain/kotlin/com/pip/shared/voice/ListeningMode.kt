package com.pip.shared.voice

/** Mirrors the backend's `app/voice/listening_modes.py` (PRD §4.4/§7). */
enum class ListeningMode(val wireValue: String) {
    RESPONSIVE("responsive"),
    PATIENT("patient"),
    PTT("ptt"),
}
