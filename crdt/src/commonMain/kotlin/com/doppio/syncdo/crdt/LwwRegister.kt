package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Last-Write-Wins register: a single [value] tagged with the [timestamp] of the write and
 * the [nodeId] that produced it.
 *
 * Merge picks the entry with the greater timestamp; ties are broken by [nodeId] ordering
 * (lexicographic) so convergence is deterministic across peers. Because ties use nodeId,
 * two concurrent writes with identical timestamps from different nodes still converge to
 * the same winner everywhere without coordination.
 *
 * Suitable for fields where "most recent write wins" is the intended semantics — titles,
 * flags, single-valued settings. For multi-value or causal data, prefer [OrSet] or a
 * composite [DeltaState].
 */
@Serializable
data class LwwRegister<T>(
    val value: T,
    val timestamp: Instant,
    val nodeId: NodeId
) : CrdtState<LwwRegister<T>> {

    override fun merge(other: LwwRegister<T>): LwwRegister<T> = when {
        timestamp > other.timestamp -> this
        timestamp < other.timestamp -> other
        // Tie-break by nodeId (lexicographic) for deterministic convergence
        nodeId >= other.nodeId -> this
        else -> other
    }
}
