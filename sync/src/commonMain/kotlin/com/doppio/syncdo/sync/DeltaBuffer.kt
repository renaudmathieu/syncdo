package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.CrdtState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Outbox of local changes waiting to be pushed. It holds at most one delta: every
 * [record] merges into it. Coroutine-safe thanks to an internal [Mutex].
 *
 * To put back a delta whose push failed, simply [record] it again: [CrdtState.merge] is
 * commutative, so the order in which deltas are recorded does not matter.
 */
class DeltaBuffer<D : CrdtState<D>> {

    private val mutex = Mutex()
    private var pending: D? = null

    suspend fun record(delta: D): Unit = mutex.withLock {
        pending = pending?.merge(delta) ?: delta
    }

    /** Atomically take the pending delta and empty the buffer. Null if nothing is pending. */
    suspend fun flush(): D? = mutex.withLock {
        pending.also { pending = null }
    }
}
