package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlinx.serialization.Serializable

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
