package com.doppio.syncdo.crdt

/**
 * A state-based CRDT: a value that replicas modify independently and later combine
 * with [merge].
 *
 * ## The contract
 *
 * For all states `a`, `b` and `c`, [merge] must be:
 * - **commutative**: `a.merge(b) == b.merge(a)`;
 * - **associative**: `a.merge(b).merge(c) == a.merge(b.merge(c))`;
 * - **idempotent**: `a.merge(a) == a`.
 *
 * In other words the type is a *join-semilattice*. Replicas that have received the same
 * states end up equal, whatever the delivery order and however many times each state was
 * delivered. No coordination between replicas is needed.
 *
 * ## Deltas are states
 *
 * There is no separate delta type. A delta is a small state of the same type that holds
 * only a change, for example the result of [OrSet.addDelta]. Apply it locally with
 * [merge], then send the very same value to the other replicas, which merge it too:
 *
 * ```kotlin
 * val delta = set.addDelta("milk", UniqueTag("nodeA", 1))
 * set = set.merge(delta) // local update
 * send(delta)            // other replicas: theirs.merge(delta)
 * ```
 *
 * ## Composition
 *
 * A data class whose fields are all CRDTs is itself a CRDT when its [merge] merges each
 * field with the field's own [merge]. For a map of CRDTs, use [mergeStates].
 *
 * @param T the implementing type itself (`class Foo : CrdtState<Foo>`), so that [merge]
 *   takes and returns the concrete type.
 */
public interface CrdtState<T : CrdtState<T>> {

    /** Combine this state with [other] and return the result. Neither operand is modified. */
    public fun merge(other: T): T
}
