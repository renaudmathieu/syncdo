package com.doppio.syncdo.model

import com.doppio.syncdo.crdt.TodoItemCrdt
import kotlin.time.Instant

data class TodoItem(
    val id: String,
    val title: String,
    val note: String,
    val completed: Boolean,
    val position: Double,
    val createdAt: Instant,
    val lastModified: Instant
)

fun TodoItemCrdt.toModel(): TodoItem = TodoItem(
    id = id,
    title = title.value,
    note = note.value,
    completed = completed.value,
    position = position.value,
    createdAt = createdAt,
    lastModified = maxOf(
        title.timestamp,
        note.timestamp,
        completed.timestamp,
        position.timestamp
    )
)
