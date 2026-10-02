// Sprint 7 (PRD-KMP-Migration-v2.md §19): the first-ever Android app,
// consuming the `:shared` module built in Phase 0-6. UI/flow mirrors iOS
// 1:1 per §13a — not the richer `index.html` prototype (deferred, §19).
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Physical devices can't reach `10.0.2.2` (that's an AVD-only alias for the
// host's loopback) — they need the dev machine's real LAN IP instead.
// Override per-machine in the gitignored root `local.properties`
// (`BACKEND_HOST=192.168.x.x`, phone and computer must be on the same
// Wi-Fi); defaults to the emulator alias so a fresh checkout still works
// out of the box for AVD testing.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
val backendHost: String = localProperties.getProperty("BACKEND_HOST") ?: "10.0.2.2"

android {
    namespace = "com.pip.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pip.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BACKEND_HOST", "\"$backendHost\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.koin.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // On-device debugger (Pluto) — network inspector (shared/androidMain wires the Ktor
    // interceptor) + crash/ANR capture. `-no-op` variants keep release builds Pluto-free.
    debugImplementation(libs.pluto.core)
    releaseImplementation(libs.pluto.core.noop)
    debugImplementation(libs.pluto.network)
    releaseImplementation(libs.pluto.network.noop)
    debugImplementation(libs.pluto.exceptions)
    releaseImplementation(libs.pluto.exceptions.noop)
}
