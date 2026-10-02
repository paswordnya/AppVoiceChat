package com.pip.shared.core.storage

import platform.Foundation.NSUserDefaults

/** Matches iOS today's `UserDefaults.standard` use for `AppSession`/`AuthTokenStore` (PRD §4.5). */
actual class KeyValueStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun putString(
        key: String,
        value: String,
    ) {
        defaults.setObject(value, key)
    }
}
