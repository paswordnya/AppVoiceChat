package com.pip.shared.viewmodel

/**
 * Thin host state (PRD §4.2/§13a) — embeds Chat inline, owns Voice/Chat
 * full-screen presentation flags on the native side. No shared state
 * beyond that today; kept as an explicit class so both platforms have one
 * place this grows into, rather than each hand-rolling presentation state.
 */
class MainViewModel
