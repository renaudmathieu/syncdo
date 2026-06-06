package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Last-Writer-Wins register: the write with the larger [timestamp] wins, with
 * lexicographic [nodeId] order as a deterministic tie-breaker so replicas
 * converge on the same value even when two writes share the same instant.
 *
 * Use for single-cell fields where "most recent edit wins" is the right
 * semantic (title, note, completion flag). For collections where concurrent
 * adds should both survive, prefer [OrSet] instead.
 */
@Serializable
data class LwwRegister<T>(
    val value: T,
    val timestamp: Instant,
    val nodeId: NodeId
) : CrdtState<LwwRegister<T>> {

    /**
     * Returns the register with the larger [timestamp]; on a timestamp tie,
     * the one whose [nodeId] sorts last wins.
     */
    override fun merge(other: LwwRegister<T>): LwwRegister<T> = when {
        timestamp > other.timestamp -> this
        timestamp < other.timestamp -> other
        nodeId >= other.nodeId -> this
        else -> other
    }
}
