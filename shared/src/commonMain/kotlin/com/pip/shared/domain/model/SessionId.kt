package com.pip.shared.domain.model

/** One backend session id per install, shared by chat and voice (PRD §4.4/§6 Phase 3). */
data class SessionId(val value: String)
