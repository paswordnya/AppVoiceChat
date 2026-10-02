package com.pip.shared.core.storage

import android.content.Context
import com.pip.shared.core.di.AndroidPlatformContext

actual class KeyValueStore actual constructor() {
    private val prefs by lazy {
        AndroidPlatformContext.applicationContext.getSharedPreferences("pip_shared_prefs", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }
}
