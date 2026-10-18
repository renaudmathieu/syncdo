package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * Identifies one `add` operation in an [OrSet].
 *
 * A tag must never be reused. A node can guarantee it with a counter that only grows,
 * for example its own entry in a [VectorClock], incremented before each add.
 *
 * @property nodeId the node that performed the add.
 * @property counter a number that [nodeId] never uses twice.
 */
@Serializable
data class UniqueTag(
    val nodeId: NodeId,
    val counter: Long
)

/**
 * Observed-Remove Set (OR-Set): a set whose elements can be added and removed
 * concurrently by several replicas, with *add-wins* semantics.
 *
 * ## How it works
 *
 * Every add attaches a fresh [UniqueTag] to the element. A remove does not erase the
 * element: it *tombstones* the tags it has observed. An element is in the set as long as
 * it has at least one tag that is not tombstoned.
 *
 * Suppose replica A removes "milk" while replica B concurrently adds "milk" again. B's add
 * carries a tag that A's remove has never seen, so after merge that tag is still alive and
 * "milk" stays in the set: add wins.
 *
 * ## Usage
 *
 * ```kotlin
 * var set = OrSet<String>()
 * set = set.add("milk", UniqueTag("A", 1))
 * set = set.add("eggs", UniqueTag("A", 2))
 * set = set.remove("milk")
 * set.elements() // [eggs]
 * ```
 *
 * To sync changes without sending the whole set, use [addDelta] and [removeDelta]. They
 * return a small [OrSet] holding only the change; [add] is simply `merge(addDelta(...))`.
 *
 * ## Invariant
 *
 * [entries] only holds live tags: [merge] drops every tag found in [tombstones]. So an
 * element is in the set if and only if it is a key of [entries].
 *
 * ## Limits
 *
 * Tombstones are never garbage collected: they grow with the number of removes.
 *
 * @param E the element type. With JSON, prefer types that serialize as map keys
 *   (strings, numbers, enums).
 * @property entries the live tags of each element in the set.
 * @property tombstones the removed tags of each element that was ever removed.
 */
@Serializable
data class OrSet<E>(
    val entries: Map<E, Set<UniqueTag>> = emptyMap(),
    val tombstones: Map<E, Set<UniqueTag>> = emptyMap()
) : CrdtState<OrSet<E>> {

    /** True if [element] is in the set. */
    fun contains(element: E): Boolean = element in entries

    /** The elements currently in the set. */
    fun elements(): Set<E> = entries.keys

    /**
     * True if this set holds no tag at all, live or tombstoned. Useful to detect an empty
     * delta; note that a set whose elements were all removed is not empty.
     */
    fun isEmpty(): Boolean = entries.isEmpty() && tombstones.isEmpty()

    /**
     * The change "add [element] with [tag]", as a delta to [merge].
     *
     * @param tag a tag never used before, see [UniqueTag].
     */
    fun addDelta(element: E, tag: UniqueTag): OrSet<E> =
        OrSet(entries = mapOf(element to setOf(tag)))

    /**
     * The change "remove [element]", as a delta to [merge]: it tombstones every tag this
     * set has observed for [element]. Empty (see [isEmpty]) if [element] is not in the set.
     */
    fun removeDelta(element: E): OrSet<E> {
        val observedTags = entries[element] ?: return OrSet()
        return OrSet(tombstones = mapOf(element to observedTags))
    }

    /** This set with [element] added. Shorthand for `merge(addDelta(element, tag))`. */
    fun add(element: E, tag: UniqueTag): OrSet<E> = merge(addDelta(element, tag))

    /** This set with [element] removed. Shorthand for `merge(removeDelta(element))`. */
    fun remove(element: E): OrSet<E> = merge(removeDelta(element))

    /** Union of both sets: union of the tombstones, union of the tags minus the tombstoned ones. */
    override fun merge(other: OrSet<E>): OrSet<E> {
        val mergedTombstones = tombstones.unionValues(other.tombstones)
        val liveEntries = entries.unionValues(other.entries)
            .mapValues { (element, tags) -> tags - mergedTombstones[element].orEmpty() }
            .filterValues { it.isNotEmpty() }
        return OrSet(liveEntries, mergedTombstones)
    }
}
