package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.VectorClock
import com.doppio.syncdo.sync.ClockedState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authoritative server-side state for a single CRDT document.
 *
 * Maintains the canonical state, a bounded history of recent deltas (for incremental
 * catch-up), and serializes concurrent merges with a mutex. Applications wire this to
 * a WebSocket route via [com.doppio.syncdo.sync.server.syncEndpoint].
 *
 * @param initialState the starting state, e.g. `MyCrdt()` or a state loaded from disk.
 * @param maxLogSize how many recent deltas to retain for incremental catch-up. Clients
 *   behind this window receive the full state instead.
 * @param onStateChanged optional hook invoked with the new state after every successful
 *   merge. Runs while the merge mutex is held, so prefer fast operations (snapshots,
 *   in-memory writes, or `launch`-and-return). Use for persistence, metrics, or audit
 *   logs. Exceptions propagate to the caller of [mergeDelta].
 */
public class SyncServer<S : ClockedState<S>>(
    initialState: S,
    maxLogSize: Int = 100,
    private val onStateChanged: suspend (S) -> Unit = {},
) {
    private val mutex = Mutex()
    private var state: S = initialState
    private val deltaLog = DeltaLog<S>(initialState.clock, maxLogSize)

    /** Merge a client-pushed delta into the canonical state and record it in the log. */
    public suspend fun mergeDelta(delta: S): Unit = mutex.withLock {
        state = state.merge(delta)
        deltaLog.append(delta)
        onStateChanged(state)
    }

    /**
     * What a client at [clientClock] is missing: the merged recent deltas when the log
     * covers the gap, the full state otherwise (a full state is a valid delta). Null if
     * the client is up to date.
     */
    public suspend fun getDeltaSince(clientClock: VectorClock): S? = mutex.withLock {
        when {
            state.clock.isLessThanOrEqual(clientClock) -> null
            deltaLog.covers(clientClock) -> deltaLog.missedSince(clientClock)
            else -> state
        }
    }

    /** Current authoritative state. Read-only snapshot. */
    public suspend fun currentState(): S = mutex.withLock { state }
}
