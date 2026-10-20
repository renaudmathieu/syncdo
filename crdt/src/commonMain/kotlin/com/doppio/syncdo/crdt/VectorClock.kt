package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/** Identifier of a replica (a device, a server). Must be unique and stable over time. */
public typealias NodeId = String

/**
 * Vector clock: one event counter per node, used to track causality between replicas.
 *
 * Each node increments only its own entry, once per local event ([increment]). Merging
 * two clocks takes the max of each entry ([merge]): the result is the clock of a replica
 * that has seen the events of both.
 *
 * Clocks are only *partially* ordered. Given two clocks `a` and `b`:
 * - `a.isLessThanOrEqual(b)` is true when `b` has seen every event `a` has seen;
 * - when neither `a <= b` nor `b <= a`, the clocks are *concurrent*: each replica has
 *   seen events the other has not.
 *
 * ```kotlin
 * val a = VectorClock().increment("A").increment("A") // {A=2}
 * val b = VectorClock().increment("B")                // {B=1}
 * a.isLessThanOrEqual(b)                              // false: a and b are concurrent
 * a.merge(b)                                          // {A=2, B=1}
 * ```
 *
 * A vector clock is itself a CRDT: [merge] is commutative, associative and idempotent.
 *
 * @property entries the counter of each node. A missing node counts as 0.
 */
@Serializable
public data class VectorClock(
    public val entries: Map<NodeId, Long> = emptyMap()
) : CrdtState<VectorClock> {

    /** The counter of [nodeId], or 0 if this clock has never seen it. */
    public operator fun get(nodeId: NodeId): Long = entries.getOrElse(nodeId) { 0L }

    /** A copy of this clock where the counter of [nodeId] is increased by one. */
    public fun increment(nodeId: NodeId): VectorClock =
        copy(entries = entries + (nodeId to this[nodeId] + 1))

    /** The pointwise max of both clocks. */
    override fun merge(other: VectorClock): VectorClock =
        VectorClock((entries.keys + other.entries.keys).associateWith { maxOf(this[it], other[it]) })

    /**
     * True if every entry of this clock is <= the matching entry of [other], i.e. [other]
     * has seen every event this clock has seen. Two concurrent clocks are each false for
     * the other.
     */
    public fun isLessThanOrEqual(other: VectorClock): Boolean =
        (entries.keys + other.entries.keys).all { this[it] <= other[it] }
}
