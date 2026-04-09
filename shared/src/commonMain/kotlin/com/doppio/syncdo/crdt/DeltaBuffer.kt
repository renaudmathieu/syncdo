package com.doppio.syncdo.crdt

/**
 * Accumulates local mutations as deltas for later sync.
 * Thread-safe usage is the caller's responsibility (repository uses a mutex).
 */
class DeltaBuffer {

    private var pending: TodoListDelta = TodoListDelta()

    fun recordAdd(
        id: String,
        item: TodoItemCrdt,
        tag: UniqueTag,
        clock: VectorClock
    ) {
        pending = pending.merge(
            TodoListDelta(
                addedOrUpdatedItems = mapOf(id to item),
                addedItemTags = mapOf(id to setOf(tag)),
                clock = clock
            )
        )
    }

    fun recordRemove(
        id: String,
        tombstonedTags: Set<UniqueTag>,
        clock: VectorClock
    ) {
        pending = pending.merge(
            TodoListDelta(
                removedItemTags = mapOf(id to tombstonedTags),
                clock = clock
            )
        )
    }

    fun recordUpdate(
        id: String,
        item: TodoItemCrdt,
        clock: VectorClock
    ) {
        pending = pending.merge(
            TodoListDelta(
                addedOrUpdatedItems = mapOf(id to item),
                clock = clock
            )
        )
    }

    fun flush(): TodoListDelta? {
        if (pending.isEmpty()) return null
        val delta = pending
        pending = TodoListDelta()
        return delta
    }

    fun restore(delta: TodoListDelta) {
        pending = delta.merge(pending)
    }
    
}
