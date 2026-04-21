package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

@Serializable
data class UniqueTag(
    val nodeId: NodeId,
    val counter: Long
)

/**
 * Observed-Remove Set (OR-Set) with add-wins semantics.
 * Each add attaches a unique tag. Remove tombstones only the tags observed at remove time.
 * Concurrent add + remove = add wins (the new tag survives).
 */
@Serializable
data class OrSet<E>(
    val entries: Map<E, Set<UniqueTag>> = emptyMap(),
    val tombstones: Map<E, Set<UniqueTag>> = emptyMap()
) : CrdtState<OrSet<E>> {

    fun add(element: E, nodeId: NodeId, counter: Long): OrSet<E> =
        addWithDelta(element, nodeId, counter).first

    fun remove(element: E): OrSet<E> = removeWithDelta(element).first

    /**
     * Add [element] with a fresh tag and return both the new set and the
     * [OrSetDelta] describing the change, so callers can ship it over the wire
     * without re-deriving it from state.
     */
    fun addWithDelta(element: E, nodeId: NodeId, counter: Long): Pair<OrSet<E>, OrSetDelta<E>> {
        val tag = UniqueTag(nodeId, counter)
        val currentTags = entries.getOrElse(element) { emptySet() }
        val next = copy(entries = entries + (element to (currentTags + tag)))
        val delta = OrSetDelta(addedTags = mapOf(element to setOf(tag)))
        return next to delta
    }

    /**
     * Remove [element] (tombstoning every tag currently observed for it) and
     * return both the new set and the [OrSetDelta] describing the change.
     * If [element] is not live, the returned delta is empty.
     */
    fun removeWithDelta(element: E): Pair<OrSet<E>, OrSetDelta<E>> {
        val observedTags = entries.getOrElse(element) { emptySet() }
        if (observedTags.isEmpty()) return this to OrSetDelta()
        val currentTombstones = tombstones.getOrElse(element) { emptySet() }
        val next = copy(
            entries = entries - element,
            tombstones = tombstones + (element to (currentTombstones + observedTags))
        )
        val delta = OrSetDelta(removedTags = mapOf(element to observedTags))
        return next to delta
    }

    fun contains(element: E): Boolean {
        val activeTags = entries.getOrElse(element) { emptySet() }
        val removedTags = tombstones.getOrElse(element) { emptySet() }
        return (activeTags - removedTags).isNotEmpty()
    }

    fun elements(): Set<E> = entries.keys.filter { contains(it) }.toSet()

    override fun merge(other: OrSet<E>): OrSet<E> {
        val mergedTombstones = tombstones.unionValues(other.tombstones)

        val mergedEntries = mutableMapOf<E, Set<UniqueTag>>()
        for (key in entries.keys + other.entries.keys) {
            val allTags = entries.getOrElse(key) { emptySet() } +
                other.entries.getOrElse(key) { emptySet() }
            val liveTags = allTags - mergedTombstones.getOrElse(key) { emptySet() }
            if (liveTags.isNotEmpty()) {
                mergedEntries[key] = liveTags
            }
        }

        return OrSet(mergedEntries, mergedTombstones)
    }

    /**
     * Fold an [OrSetDelta] into this set. Equivalent to merging with the
     * partial [OrSet] reconstructed from the delta.
     */
    fun applyDelta(delta: OrSetDelta<E>): OrSet<E> =
        merge(OrSet(entries = delta.addedTags, tombstones = delta.removedTags))

    /**
     * View this set as an [OrSetDelta] (every live tag as an add, every
     * tombstoned tag as a remove). Used when shipping a full-state delta as
     * an incremental one — e.g. for a first-sync catch-up.
     */
    fun toDelta(): OrSetDelta<E> =
        OrSetDelta(addedTags = entries, removedTags = tombstones)
}
