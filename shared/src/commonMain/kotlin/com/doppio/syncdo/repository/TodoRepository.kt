package com.doppio.syncdo.repository

import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface TodoRepository {
    val todos: StateFlow<List<TodoItem>>
    val syncStatus: StateFlow<SyncStatus>

    suspend fun addTodo(title: String)
    suspend fun removeTodo(id: String)
    suspend fun toggleCompleted(id: String)
    suspend fun updateTitle(id: String, newTitle: String)
    suspend fun updateNote(id: String, newNote: String)
    suspend fun reorderTodo(id: String, newPosition: Double)
}
