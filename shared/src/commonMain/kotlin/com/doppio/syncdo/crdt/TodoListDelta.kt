package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable

/**
 * A delta representing changes to the TodoList CRDT.
 *
 * Composes three independent pieces — each merged via its own operator from
 * `:crdt`:
 * - [items]: a map of [TodoItemCrdt] updates, merged field-by-field via
 *   [mergeStates] (maps of CRDTs are themselves CRDTs).
 * - [membership]: an [OrSetDelta] carrying add/remove tags for item IDs.
 * - [clock]: the outer causal clock shared by the whole delta.
 */
@Serializable
data class TodoListDelta(
    val items: Map<String, TodoItemCrdt> = emptyMap(),
    val membership: OrSetDelta<String> = OrSetDelta(),
    override val clock: VectorClock = VectorClock()
) : Delta<TodoListDelta> {

    override fun isEmpty(): Boolean = items.isEmpty() && membership.isEmpty()

    override fun merge(other: TodoListDelta): TodoListDelta = TodoListDelta(
        items = items.mergeStates(other.items),
        membership = membership.merge(other.membership),
        clock = clock.merge(other.clock),
    )
}
