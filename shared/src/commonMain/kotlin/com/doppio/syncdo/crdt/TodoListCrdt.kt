package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * Aggregate CRDT for the entire todo list.
 * Composes an OR-Set (for membership) with a map of TodoItemCrdt (for item data).
 */
@Serializable
data class TodoListCrdt(
    val itemIds: OrSet<String> = OrSet(),
    val items: Map<String, TodoItemCrdt> = emptyMap(),
    val clock: VectorClock = VectorClock()
) : CrdtState<TodoListCrdt> {

    override fun merge(other: TodoListCrdt): TodoListCrdt {
        val mergedIds = itemIds.merge(other.itemIds)
        val activeIds = mergedIds.elements()

        // Merge item data for all active IDs
        val mergedItems = mutableMapOf<String, TodoItemCrdt>()
        for (id in activeIds) {
            val thisItem = items[id]
            val otherItem = other.items[id]
            mergedItems[id] = when {
                thisItem != null && otherItem != null -> thisItem.merge(otherItem)
                thisItem != null -> thisItem
                otherItem != null -> otherItem
                else -> continue // ID in set but no data — should not happen
            }
        }

        return TodoListCrdt(
            itemIds = mergedIds,
            items = mergedItems,
            clock = clock.merge(other.clock)
        )
    }
}
