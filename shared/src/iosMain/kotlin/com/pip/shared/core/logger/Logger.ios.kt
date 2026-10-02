package com.pip.shared.core.logger

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(
        tag: String,
        message: String,
    ) {
        NSLog("[$tag] $message")
    }

    actual fun i(
        tag: String,
        message: String,
    ) {
        NSLog("[$tag] $message")
    }

    actual fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        NSLog("[$tag] $message ${throwable?.message.orEmpty()}")
    }

    actual fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        NSLog("[$tag] $message ${throwable?.message.orEmpty()}")
    }
}
