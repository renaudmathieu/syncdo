package com.doppio.syncdo.crdt

/**
 * Base interface for all CRDT states (state-based CRDTs).
 *
 * [merge] must be commutative, associative and idempotent (a join-semilattice), so
 * replicas that have received the same states converge whatever the order or the
 * number of deliveries.
 *
 * A delta is just a small state of the same type, holding only a change: apply it
 * with [merge] locally and ship the same value to other replicas.
 */
interface CrdtState<T : CrdtState<T>> {
    fun merge(other: T): T
}
