package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * A delta representing changes to the TodoList CRDT.
 * Contains only the items that were added, updated, or removed since last sync.
 */
@Serializable
data class TodoListDelta(
    val addedOrUpdatedItems: Map<String, TodoItemCrdt> = emptyMap(),
    val removedItemTags: Map<String, Set<UniqueTag>> = emptyMap(),
    val addedItemTags: Map<String, Set<UniqueTag>> = emptyMap(),
    val clock: VectorClock = VectorClock()
) {
    fun isEmpty(): Boolean =
        addedOrUpdatedItems.isEmpty() && removedItemTags.isEmpty() && addedItemTags.isEmpty()

    /**
     * Merge two deltas into one (accumulate changes).
     */
    fun merge(other: TodoListDelta): TodoListDelta {
        val mergedItems = (addedOrUpdatedItems.keys + other.addedOrUpdatedItems.keys).associateWith { id ->
            val a = addedOrUpdatedItems[id]
            val b = other.addedOrUpdatedItems[id]
            when {
                a != null && b != null -> a.merge(b)
                a != null -> a
                else -> b!!
            }
        }
        val mergedRemovedTags = (removedItemTags.keys + other.removedItemTags.keys).associateWith { id ->
            removedItemTags.getOrElse(id) { emptySet() } +
                other.removedItemTags.getOrElse(id) { emptySet() }
        }
        val mergedAddedTags = (addedItemTags.keys + other.addedItemTags.keys).associateWith { id ->
            addedItemTags.getOrElse(id) { emptySet() } +
                other.addedItemTags.getOrElse(id) { emptySet() }
        }
        return TodoListDelta(mergedItems, mergedRemovedTags, mergedAddedTags, clock.merge(other.clock))
    }
}
