package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * The incremental change produced by mutating an [OrSet].
 *
 * An [OrSetDelta] is a *patch* — it composes by union under [merge] but does
 * not carry its own causal clock. Composite domain deltas embed an
 * [OrSetDelta] (or several) and attach a single [VectorClock] at the outer
 * [Delta] level.
 *
 * - [addedTags]: tags introduced by `add` operations, keyed by element.
 * - [removedTags]: tags tombstoned by `remove` operations, keyed by element.
 */
@Serializable
data class OrSetDelta<E>(
    val addedTags: Map<E, Set<UniqueTag>> = emptyMap(),
    val removedTags: Map<E, Set<UniqueTag>> = emptyMap(),
) {

    fun merge(other: OrSetDelta<E>): OrSetDelta<E> = OrSetDelta(
        addedTags = addedTags.unionValues(other.addedTags),
        removedTags = removedTags.unionValues(other.removedTags),
    )

    fun isEmpty(): Boolean = addedTags.isEmpty() && removedTags.isEmpty()
}
