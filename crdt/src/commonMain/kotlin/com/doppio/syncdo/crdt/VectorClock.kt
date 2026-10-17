package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

typealias NodeId = String

/**
 * Logical clock with one counter per node. A node increments its own entry for each
 * local event; [merge] takes the pointwise max.
 */
@Serializable
data class VectorClock(
    val entries: Map<NodeId, Long> = emptyMap()
) : CrdtState<VectorClock> {

    operator fun get(nodeId: NodeId): Long = entries.getOrElse(nodeId) { 0L }

    fun increment(nodeId: NodeId): VectorClock =
        copy(entries = entries + (nodeId to this[nodeId] + 1))

    override fun merge(other: VectorClock): VectorClock =
        VectorClock((entries.keys + other.entries.keys).associateWith { maxOf(this[it], other[it]) })

    /**
     * True if every entry of this clock is <= the matching entry of [other], i.e. [other]
     * has seen every event this clock has seen. Vector clocks are only partially ordered:
     * two concurrent clocks are each false for the other.
     */
    fun isLessThanOrEqual(other: VectorClock): Boolean =
        (entries.keys + other.entries.keys).all { this[it] <= other[it] }
}
