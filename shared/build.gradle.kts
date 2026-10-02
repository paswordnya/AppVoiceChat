// Package tree and tooling choices per PRD-KMP-Migration-v2.md §5.2/§5.3.
// Single Gradle multiplatform module ("shared") to start — split later
// only if build times or team ownership boundaries demand it (§5.2).
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.skie)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Grouped into one XCFramework (PRD §6 Phase 5 / §19 Sprint 6) so the
    // iOS app adds a single binary dependency covering both device and
    // simulator slices, instead of two separate .framework bundles.
    val xcframework = XCFramework("PipShared")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "PipShared"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.pip.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Build-type-scoped (not `kotlin { sourceSets { androidMain ... } }`, which has no
// debug/release split) — androidMain's NetworkDebugTools.android.kt installs
// PlutoKtorInterceptor from the real artifact in debug and its API-compatible no-op in
// release, so the same Kotlin source compiles against either one.
dependencies {
    debugImplementation(libs.pluto.network)
    releaseImplementation(libs.pluto.network.noop)
}
