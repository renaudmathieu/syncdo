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

    fun add(element: E, nodeId: NodeId, counter: Long): OrSet<E> {
        val tag = UniqueTag(nodeId, counter)
        val currentTags = entries.getOrElse(element) { emptySet() }
        return copy(entries = entries + (element to (currentTags + tag)))
    }

    fun remove(element: E): OrSet<E> {
        val observedTags = entries.getOrElse(element) { emptySet() }
        if (observedTags.isEmpty()) return this
        val currentTombstones = tombstones.getOrElse(element) { emptySet() }
        return copy(
            entries = entries - element,
            tombstones = tombstones + (element to (currentTombstones + observedTags))
        )
    }

    fun contains(element: E): Boolean {
        val activeTags = entries.getOrElse(element) { emptySet() }
        val removedTags = tombstones.getOrElse(element) { emptySet() }
        return (activeTags - removedTags).isNotEmpty()
    }

    fun elements(): Set<E> = entries.keys.filter { contains(it) }.toSet()

    override fun merge(other: OrSet<E>): OrSet<E> {
        // Union all tombstones
        val allTombKeys = tombstones.keys + other.tombstones.keys
        val mergedTombstones = allTombKeys.associateWith { key ->
            tombstones.getOrElse(key) { emptySet() } +
                other.tombstones.getOrElse(key) { emptySet() }
        }

        // Union all entry tags, then subtract tombstoned tags
        val allEntryKeys = entries.keys + other.entries.keys
        val mergedEntries = mutableMapOf<E, Set<UniqueTag>>()
        for (key in allEntryKeys) {
            val allTags = entries.getOrElse(key) { emptySet() } +
                other.entries.getOrElse(key) { emptySet() }
            val deadTags = mergedTombstones.getOrElse(key) { emptySet() }
            val liveTags = allTags - deadTags
            if (liveTags.isNotEmpty()) {
                mergedEntries[key] = liveTags
            }
        }

        return OrSet(mergedEntries, mergedTombstones)
    }
}
