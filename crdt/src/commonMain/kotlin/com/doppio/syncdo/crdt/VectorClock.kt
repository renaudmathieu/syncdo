package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

typealias NodeId = String

@Serializable
data class VectorClock(
    val entries: Map<NodeId, Long> = emptyMap()
) : CrdtState<VectorClock> {

    operator fun get(nodeId: NodeId): Long = entries.getOrElse(nodeId) { 0L }

    fun increment(nodeId: NodeId): VectorClock {
        val current = this[nodeId]
        return copy(entries = entries + (nodeId to current + 1))
    }

    override fun merge(other: VectorClock): VectorClock {
        val allKeys = entries.keys + other.entries.keys
        val merged = allKeys.associateWith { key ->
            maxOf(this[key], other[key])
        }
        return VectorClock(merged)
    }

    /**
     * True if this clock strictly happened-after [other] (every entry >=, with
     * at least one strictly >). Use to decide whether an event causally
     * follows another. For "neither dominates the other" → events are
     * concurrent; resolve via CRDT merge, not by ordering.
     */
    fun dominates(other: VectorClock): Boolean {
        val allKeys = entries.keys + other.entries.keys
        var hasStrictlyGreater = false
        for (key in allKeys) {
            val thisVal = this[key]
            val otherVal = other[key]
            if (thisVal < otherVal) return false
            if (thisVal > otherVal) hasStrictlyGreater = true
        }
        return hasStrictlyGreater
    }

    /**
     * True if every entry of this clock is <= the corresponding entry in
     * [other]. Equivalent to "[other] dominates or equals this". Used by
     * [com.doppio.syncdo.sync.server.SyncServer]'s delta log to decide which
     * deltas a client at [other] still needs to receive.
     */
    fun isLessThanOrEqual(other: VectorClock): Boolean {
        val allKeys = entries.keys + other.entries.keys
        return allKeys.all { key -> this[key] <= other[key] }
    }
}
