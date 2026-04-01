package com.doppio.syncdo.store

import com.doppio.syncdo.crdt.TodoListDelta
import com.doppio.syncdo.crdt.VectorClock

class DeltaLog(private val maxSize: Int = 100) {
    private val entries = ArrayDeque<Pair<VectorClock, TodoListDelta>>()

    fun append(delta: TodoListDelta) {
        entries.addLast(delta.clock to delta)
        while (entries.size > maxSize) {
            entries.removeFirst()
        }
    }

    /**
     * Returns merged deltas that the client hasn't seen (based on their clock).
     * Returns null if the log doesn't go back far enough.
     */
    fun getDeltasSince(clientClock: VectorClock): TodoListDelta? {
        if (entries.isEmpty()) return TodoListDelta()

        // Find deltas the client hasn't seen
        val missed = entries.filter { (deltaClock, _) ->
            !deltaClock.isLessThanOrEqual(clientClock)
        }.map { it.second }

        if (missed.isEmpty()) return TodoListDelta()

        // Merge all missed deltas into one
        return missed.reduce { acc, delta -> acc.merge(delta) }
    }
}
