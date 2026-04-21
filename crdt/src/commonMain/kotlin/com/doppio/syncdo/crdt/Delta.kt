package com.doppio.syncdo.crdt

/**
 * A partial change to a [DeltaState]. Deltas can be accumulated (merged) and shipped
 * over the network instead of full state, enabling efficient incremental sync.
 *
 * Implementations must be commutative, associative, and idempotent under [merge].
 */
interface Delta<D : Delta<D>> {
    val clock: VectorClock
    fun merge(other: D): D
    fun isEmpty(): Boolean
}
