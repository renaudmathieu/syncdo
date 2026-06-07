package com.doppio.syncdo.sync

enum class SyncLogLevel { Debug, Info, Warn, Error }

/**
 * Sink for diagnostic events from [SyncEngine] and the server route. The default
 * is [Noop]; host apps inject their own (e.g. Logback on JVM, NSLog on iOS,
 * Logcat on Android) to surface connection errors, push failures, and merge
 * activity without dragging an SLF4J dependency into common code.
 */
fun interface SyncLogger {
    fun log(level: SyncLogLevel, message: String, error: Throwable?)

    companion object {
        val Noop: SyncLogger = SyncLogger { _, _, _ -> }
    }
}

internal fun SyncLogger.log(level: SyncLogLevel, message: String) = log(level, message, null)
