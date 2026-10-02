package com.pip.shared.core.logger

/** os_log on iOS, Logcat on Android — PRD-KMP-Migration-v2.md §5.2. */
expect object Logger {
    fun d(
        tag: String,
        message: String,
    )

    fun i(
        tag: String,
        message: String,
    )

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
