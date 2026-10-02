package com.pip.shared.core.storage

/**
 * `UserDefaults` on iOS, `SharedPreferences` on Android — the only local
 * persistence the real app uses today (PRD §4.5: a per-install session
 * UUID, nothing else). No-arg on both platforms; the Android `actual`
 * reads its `Context` from [com.pip.shared.core.di.AndroidPlatformContext],
 * which the host app must set once at startup (see that file's doc comment).
 */
expect class KeyValueStore() {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )
}
