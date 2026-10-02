package com.pip.shared.viewmodel

/**
 * Mirrors iOS's real single-card onboarding (PRD §4.2) — not
 * `index.html`'s richer 3-page carousel (that's Phase N+1, §19).
 */
class OnboardingViewModel {
    fun onSkipOrContinue() {
        // Single decision point today: both actions go to Main (§4.2). A
        // persisted "onboarding seen" flag is a documented gap (§16), not
        // implemented here — left for whoever picks up that fix.
    }
}
