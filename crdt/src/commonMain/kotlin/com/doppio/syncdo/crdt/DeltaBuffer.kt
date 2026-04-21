package com.doppio.syncdo.crdt

/**
 * Accumulates local mutations as deltas for later sync.
 * Thread-safe usage is the caller's responsibility (repository uses a mutex).
 */
class DeltaBuffer<D : Delta<D>> {

    private var pending: D? = null

    fun record(delta: D) {
        pending = pending?.merge(delta) ?: delta
    }

    fun flush(): D? {
        val delta = pending ?: return null
        pending = null
        return delta
    }

    fun restore(delta: D) {
        pending = pending?.let { delta.merge(it) } ?: delta
    }
}
