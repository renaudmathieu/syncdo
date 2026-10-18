package com.doppio.syncdo.crdt

import com.doppio.syncdo.sync.ClockedState
import kotlinx.serialization.Serializable

/**
 * Aggregate CRDT for the entire todo list, also used as its own delta type: a delta is
 * a small [TodoListCrdt] holding only the change (one new item, one tombstone, ...).
 *
 * Each field is handled by a generic `:crdt` operator:
 * - [itemIds] merges via [OrSet.merge]: it decides which todos are visible;
 * - [items] merges via [mergeStates] ("map of CRDTs is a CRDT"). Data of removed todos
 *   is kept, like OR-Set tombstones, so that merge stays a pure join;
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
) : ClockedState<TodoListCrdt> {

    override fun merge(other: TodoListCrdt): TodoListCrdt = TodoListCrdt(
        itemIds = itemIds.merge(other.itemIds),
        items = items.mergeStates(other.items),
        clock = clock.merge(other.clock),
    )
}
