package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * CRDT representation of a single todo item.
 * Each mutable field is a LWW-Register for automatic conflict resolution.
 * Composition of semi-lattices = semi-lattice (course ch.6).
 */
@Serializable
data class TodoItemCrdt(
    val id: String,
    val title: LwwRegister<String>,
    val note: LwwRegister<String>,
    val completed: LwwRegister<Boolean>,
    val position: LwwRegister<Double>,
    val createdAt: Instant
) : CrdtState<TodoItemCrdt> {

    override fun merge(other: TodoItemCrdt): TodoItemCrdt {
        require(id == other.id) { "Cannot merge items with different IDs: $id vs ${other.id}" }
        return TodoItemCrdt(
            id = id,
            title = title.merge(other.title),
            note = note.merge(other.note),
            completed = completed.merge(other.completed),
            position = position.merge(other.position),
            createdAt = minOf(createdAt, other.createdAt)
        )
    }

    companion object {
        fun create(
            id: String,
            title: String,
            position: Double,
            nodeId: NodeId,
            timestamp: Instant
        ): TodoItemCrdt = TodoItemCrdt(
            id = id,
            title = LwwRegister(title, timestamp, nodeId),
            note = LwwRegister("", timestamp, nodeId),
            completed = LwwRegister(false, timestamp, nodeId),
            position = LwwRegister(position, timestamp, nodeId),
            createdAt = timestamp
        )
    }
}
