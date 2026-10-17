package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/** Identifies one `add` operation: unique as long as [counter] never repeats for a [nodeId]. */
@Serializable
data class UniqueTag(
    val nodeId: NodeId,
    val counter: Long
)

/**
 * Observed-Remove Set (OR-Set) with add-wins semantics.
 *
 * Every add attaches a fresh [UniqueTag]. A remove tombstones only the tags it has
 * observed, so a concurrent add (whose tag the remove never saw) survives: add wins.
 *
 * Invariant: [entries] only holds live tags. A tag that appears in [tombstones] is
 * dropped from [entries] by [merge], so an element is live iff it is a key of [entries].
 *
 * Deltas: [addDelta] and [removeDelta] return a small [OrSet] holding only the change.
 * Apply it locally with [merge], and ship the very same value to other replicas.
 */
@Serializable
data class OrSet<E>(
    val entries: Map<E, Set<UniqueTag>> = emptyMap(),
    val tombstones: Map<E, Set<UniqueTag>> = emptyMap()
) : CrdtState<OrSet<E>> {

    fun contains(element: E): Boolean = element in entries

    fun elements(): Set<E> = entries.keys

    fun isEmpty(): Boolean = entries.isEmpty() && tombstones.isEmpty()

    /** The change "add [element] with [tag]". */
    fun addDelta(element: E, tag: UniqueTag): OrSet<E> =
        OrSet(entries = mapOf(element to setOf(tag)))

    /** The change "remove [element]": tombstones every tag observed for it. Empty if absent. */
    fun removeDelta(element: E): OrSet<E> {
        val observedTags = entries[element] ?: return OrSet()
        return OrSet(tombstones = mapOf(element to observedTags))
    }

    fun add(element: E, tag: UniqueTag): OrSet<E> = merge(addDelta(element, tag))

    fun remove(element: E): OrSet<E> = merge(removeDelta(element))

    override fun merge(other: OrSet<E>): OrSet<E> {
        val mergedTombstones = tombstones.unionValues(other.tombstones)
        val liveEntries = entries.unionValues(other.entries)
            .mapValues { (element, tags) -> tags - mergedTombstones[element].orEmpty() }
            .filterValues { it.isNotEmpty() }
        return OrSet(liveEntries, mergedTombstones)
    }
}
