package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Last-Writer-Wins register: holds a single value, and on merge the most recent write wins.
 *
 * "Most recent" means the larger [timestamp]. When two writes share the same instant, the
 * one whose [nodeId] sorts last wins, so every replica picks the same value.
 *
 * Use it for a single field where "the last edit wins" is acceptable: a title, a note, a
 * completion flag. Concurrent writes are not combined, one of them is discarded. For a
 * collection where concurrent additions must all survive, use [OrSet] instead.
 *
 * A register is immutable. To write a new value, create a new register and merge it:
 *
 * ```kotlin
 * val fromA = LwwRegister("Buy milk", Instant.fromEpochMilliseconds(1_000), "A")
 * val fromB = LwwRegister("Buy oat milk", Instant.fromEpochMilliseconds(2_000), "B")
 * fromA.merge(fromB).value // "Buy oat milk": written later
 * ```
 *
 * Limits:
 * - timestamps come from each device's wall clock, so a device whose clock runs ahead
 *   wins more often;
 * - a node must never write two different values at the same instant: the pair
 *   ([timestamp], [nodeId]) identifies a write. Otherwise [merge] would not be
 *   commutative and replicas could keep different values.
 *
 * @property value the current value.
 * @property timestamp when [value] was written.
 * @property nodeId the node that wrote [value], used as a tie-breaker.
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
