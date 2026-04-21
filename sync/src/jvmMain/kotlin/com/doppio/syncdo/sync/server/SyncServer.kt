package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.DeltaState
import com.doppio.syncdo.crdt.VectorClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authoritative server-side state for a single CRDT document.
 *
 * Maintains the canonical state, a bounded history of recent deltas (for incremental
 * catch-up), and serializes concurrent merges with a mutex. Applications wire this to
 * a WebSocket route via [com.doppio.syncdo.sync.server.syncEndpoint].
 *
 * @param initialState factory for the starting state, e.g. `{ MyCrdt() }`.
 * @param fullStateAsDelta how to encode the whole [S] as a single delta [D]. Used when a
 *   client's clock is unknown (first connect) or too far behind the bounded delta log.
 * @param maxLogSize how many recent deltas to retain for incremental catch-up. Clients
 *   behind this window receive a full-state delta instead.
 */
class SyncServer<S : DeltaState<S, D>, D : Delta<D>>(
    initialState: () -> S,
    private val fullStateAsDelta: (S) -> D,
    maxLogSize: Int = 100,
) {
    private val mutex = Mutex()
    private var state: S = initialState()
    private val deltaLog = DeltaLog<D>(maxLogSize)

    /** Merge a client-pushed delta into the canonical state and record it in the log. */
    suspend fun mergeDelta(delta: D): D = mutex.withLock {
        state = state.applyDelta(delta)
        deltaLog.append(delta)
        delta
    }

    /**
     * Returns the deltas a client with [clientClock] hasn't seen yet. Falls back to a
     * full-state delta if the log doesn't cover the gap.
     */
    suspend fun getDeltaSince(clientClock: VectorClock): D = mutex.withLock {
        if (clientClock.entries.isEmpty()) {
            fullStateAsDelta(state)
        } else {
            deltaLog.getDeltasSince(clientClock) ?: fullStateAsDelta(state)
        }
    }

    /** Current authoritative state. Read-only snapshot. */
    suspend fun currentState(): S = mutex.withLock { state }
}
