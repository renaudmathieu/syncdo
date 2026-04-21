package com.doppio.syncdo.sync

/**
 * State of a [SyncEngine]. Exposed as a `StateFlow` on the engine for UI binding.
 */
enum class SyncStatus {
    /** Connected to the server and caught up. */
    Synced,
    /** Actively pushing or pulling. */
    Syncing,
    /** Disconnected, but there are unpushed local mutations. */
    PendingChanges,
    /** Disconnected, no pending work. */
    Offline,
    /** The engine stopped because of an unrecoverable error. */
    Error,
}
