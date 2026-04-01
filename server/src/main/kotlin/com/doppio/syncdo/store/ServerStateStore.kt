package com.doppio.syncdo.store

import com.doppio.syncdo.crdt.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerStateStore {
    private var state = TodoListCrdt()
    private val mutex = Mutex()
    private val deltaLog = DeltaLog(maxSize = 100)

    suspend fun mergeDelta(delta: TodoListDelta): TodoListDelta {
        return mutex.withLock {
            val remotePart = TodoListCrdt(
                itemIds = OrSet(
                    entries = delta.addedItemTags,
                    tombstones = delta.removedItemTags
                ),
                items = delta.addedOrUpdatedItems,
                clock = delta.clock
            )
            state = state.merge(remotePart)
            deltaLog.append(delta)
            delta // Return the delta for broadcasting
        }
    }

    suspend fun getDeltaSince(clientClock: VectorClock): TodoListDelta {
        return mutex.withLock {
            // If client has no clock or is too far behind, send full state as delta
            if (clientClock.entries.isEmpty()) {
                TodoListDelta(
                    addedOrUpdatedItems = state.items,
                    addedItemTags = state.itemIds.entries,
                    removedItemTags = state.itemIds.tombstones,
                    clock = state.clock
                )
            } else {
                // Try to find deltas the client hasn't seen
                val missed = deltaLog.getDeltasSince(clientClock)
                if (missed != null) {
                    missed
                } else {
                    // Client is too far behind, send full state
                    TodoListDelta(
                        addedOrUpdatedItems = state.items,
                        addedItemTags = state.itemIds.entries,
                        removedItemTags = state.itemIds.tombstones,
                        clock = state.clock
                    )
                }
            }
        }
    }

    suspend fun getFullState(): TodoListCrdt = mutex.withLock { state }
}
