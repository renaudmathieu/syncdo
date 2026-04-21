package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * Aggregate CRDT for the entire todo list.
 * Composes an OR-Set (for membership) with a map of TodoItemCrdt (for item data).
 *
 * Each field is handled by a generic `:crdt` operator:
 * - [itemIds] merges via [OrSet.merge];
 * - [items] merges via [mergeStates] ("map of CRDTs is a CRDT");
 * - [clock] merges via [VectorClock.merge] (pointwise max).
 *
 * Composition of join-semilattices is a join-semilattice, so the aggregate
 * converges for free.
 */
@Serializable
data class TodoListCrdt(
    val itemIds: OrSet<String> = OrSet(),
    val items: Map<String, TodoItemCrdt> = emptyMap(),
    override val clock: VectorClock = VectorClock()
) : DeltaState<TodoListCrdt, TodoListDelta> {

    override fun merge(other: TodoListCrdt): TodoListCrdt {
        val mergedIds = itemIds.merge(other.itemIds)
        val activeIds = mergedIds.elements()
        return TodoListCrdt(
            itemIds = mergedIds,
            items = items.mergeStates(other.items).filterKeys { it in activeIds },
            clock = clock.merge(other.clock),
        )
    }

    override fun applyDelta(delta: TodoListDelta): TodoListCrdt = merge(
        TodoListCrdt(
            itemIds = OrSet<String>().applyDelta(delta.membership),
            items = delta.items,
            clock = delta.clock,
        )
    )
}
