package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

typealias NodeId = String

/**
 * Causality tracking via a map of per-node monotonic counters.
 *
 * Each node increments its own counter on every local event; when nodes exchange state,
 * their clocks are merged component-wise (max per node). Comparing two clocks answers
 * "did A happen before B, after B, or concurrently with B?" — which [DeltaState]
 * implementations use to decide when a remote change supersedes a local one and when
 * the two must be merged.
 *
 * The empty clock ([VectorClock]`()`) is the identity: it is [isLessThanOrEqual] to any
 * other clock and merges to the other clock unchanged.
 */
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
     * Returns true if this clock dominates (is strictly greater than) the other.
     * Every entry in this >= every entry in other, with at least one strictly greater.
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
     * Returns true if this clock is less than or equal to the other (other dominates or equals this).
     */
    fun isLessThanOrEqual(other: VectorClock): Boolean {
        val allKeys = entries.keys + other.entries.keys
        return allKeys.all { key -> this[key] <= other[key] }
    }
}
