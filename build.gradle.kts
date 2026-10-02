// Root build file — no plugins applied here, just version resolution for
// subprojects (PRD-KMP-Migration-v2.md §6 Phase 0 / §19 Sprint 7).
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ktlint) apply false
}

// PRD §6 Phase 0: "ktlint or detekt for Kotlin — team preference, not an
// architectural one." Applied to every subproject so `ktlintCheck` at the
// root covers `shared` and `androidApp` both.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
