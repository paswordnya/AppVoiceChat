package com.pip.shared.core.logger

import android.util.Log

actual object Logger {
    actual fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message)
    }

    actual fun i(
        tag: String,
        message: String,
    ) {
        Log.i(tag, message)
    }

    actual fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        Log.w(tag, message, throwable)
    }

    actual fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        Log.e(tag, message, throwable)
    }
}
