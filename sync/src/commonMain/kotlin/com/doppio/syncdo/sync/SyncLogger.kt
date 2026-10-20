package com.doppio.syncdo.sync

/** Severity of a [SyncLogger] event. */
public enum class SyncLogLevel {
    /** Verbose details, useful while developing. */
    Debug,

    /** Normal lifecycle events. */
    Info,

    /** Recoverable problems, e.g. a lost connection that will be retried. */
    Warn,

    /** Failures the engine cannot recover from by itself. */
    Error,
}

/**
 * Sink for diagnostic events from [SyncEngine] and the server route. The default
 * is [Noop]; host apps inject their own (e.g. Logback on JVM, NSLog on iOS,
 * Logcat on Android) to surface connection errors, push failures, and merge
 * activity without dragging an SLF4J dependency into common code.
 */
public fun interface SyncLogger {
    /** Record one event; [error] is the exception that caused it, if any. */
    public fun log(level: SyncLogLevel, message: String, error: Throwable?)

    /** Ready-made loggers. */
    public companion object {
        /** Discards every event. */
        public val Noop: SyncLogger = SyncLogger { _, _, _ -> }
    }
}

internal fun SyncLogger.log(level: SyncLogLevel, message: String) = log(level, message, null)
