package com.pip.shared.core.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS-specific bindings land here as Phase 2+ needs them (PRD §5.2) — none yet. */
actual fun platformModule(): Module = module {}
