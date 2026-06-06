package com.doppio.syncdo.crdt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accumulates local mutations as deltas for later sync.
 *
 * Internally guarded by a [Mutex], so concurrent [record], [flush], and [restore]
 * calls are safe from multiple coroutines. The buffer holds at most one pending
 * delta at a time — repeated [record] calls compose via [Delta.merge].
 */
class DeltaBuffer<D : Delta<D>> {

    private val mutex = Mutex()
    private var pending: D? = null

    /** Append [delta] to the pending buffer, merging with any existing delta. */
    suspend fun record(delta: D): Unit = mutex.withLock {
        pending = pending?.merge(delta) ?: delta
    }

    /** Atomically read and clear the pending delta. Returns null if nothing is pending. */
    suspend fun flush(): D? = mutex.withLock {
        val delta = pending ?: return@withLock null
        pending = null
        delta
    }

    /**
     * Put [delta] back at the front of the buffer (e.g. after a failed push).
     * If newer deltas were recorded in the meantime, [delta] is merged into them
     * (oldest-first), preserving causal order.
     */
    suspend fun restore(delta: D): Unit = mutex.withLock {
        pending = pending?.let { delta.merge(it) } ?: delta
    }
}
