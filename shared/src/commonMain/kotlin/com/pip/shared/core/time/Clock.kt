package com.pip.shared.core.time

/** Wall-clock epoch millis — `System.currentTimeMillis()` vs `NSDate`, needed for [com.pip.shared.memory.OutboxEntry]. */
expect fun currentTimeMillis(): Long
