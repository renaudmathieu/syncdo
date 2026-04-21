package com.doppio.syncdo.crdt

/**
 * Base interface for all CRDT states.
 * The merge operation must be commutative, associative, and idempotent (join-semilattice).
 */
interface CrdtState<T : CrdtState<T>> {
    fun merge(other: T): T
}
