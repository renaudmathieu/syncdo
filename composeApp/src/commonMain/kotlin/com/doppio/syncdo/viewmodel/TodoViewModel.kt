package com.doppio.syncdo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodoUiState(
    val todos: List<TodoItem> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Offline,
    val selectedTodoId: String? = null
) {
    val selectedTodo: TodoItem? get() = todos.find { it.id == selectedTodoId }
}

class TodoViewModel(private val repository: TodoRepository) : ViewModel() {

    private val _selectedTodoId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TodoUiState> = combine(
        repository.todos,
        repository.syncStatus,
        _selectedTodoId
    ) { todos, syncStatus, selectedId ->
        TodoUiState(
            todos = todos,
            syncStatus = syncStatus,
            selectedTodoId = selectedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodoUiState()
    )

    fun selectTodo(id: String?) {
        _selectedTodoId.update { id }
    }

    fun addTodo(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addTodo(title.trim()) }
    }

    fun removeTodo(id: String) {
        viewModelScope.launch {
            repository.removeTodo(id)
            _selectedTodoId.update { current -> if (current == id) null else current }
        }
    }

    fun toggleCompleted(id: String) {
        viewModelScope.launch { repository.toggleCompleted(id) }
    }

    fun updateTitle(id: String, newTitle: String) {
        viewModelScope.launch { repository.updateTitle(id, newTitle) }
    }

    fun updateNote(id: String, newNote: String) {
        viewModelScope.launch { repository.updateNote(id, newNote) }
    }

    fun reorderTodo(id: String, newPosition: Double) {
        viewModelScope.launch { repository.reorderTodo(id, newPosition) }
    }
}
