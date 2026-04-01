package com.doppio.syncdo.repository

import com.doppio.syncdo.crdt.*
import com.doppio.syncdo.model.SyncStatus
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.model.toModel
import com.doppio.syncdo.persistence.LocalStorage
import com.doppio.syncdo.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OfflineFirstTodoRepository(
    private val nodeId: NodeId,
    private val storage: LocalStorage,
) : TodoRepository {

    private val mutex = Mutex()
    private var state = TodoListCrdt()
    private val deltaBuffer = DeltaBuffer()
    private var tagCounter = 0L
    private var syncEngine: SyncEngine? = null
    private var statusCollectionJob: kotlinx.coroutines.Job? = null

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    override val todos: StateFlow<List<TodoItem>> = _todos

    private val _syncStatus = MutableStateFlow(SyncStatus.Offline)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus

    fun attachSyncEngine(engine: SyncEngine, scope: CoroutineScope) {
        statusCollectionJob?.cancel()
        syncEngine = engine
        statusCollectionJob = scope.launch {
            engine.syncStatus.collect { status -> _syncStatus.update { status } }
        }
    }

    suspend fun initialize() {
        mutex.withLock {
            state = storage.load() ?: TodoListCrdt()
            // Restore tagCounter from existing state to avoid collisions
            tagCounter = state.clock[nodeId]
            emitTodos()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun addTodo(title: String) = mutate { currentState ->
        val id = Uuid.random().toString()
        val now = Clock.System.now()
        val position = nextPosition(currentState)
        val item = TodoItemCrdt.create(id, title, position, nodeId, now)
        val tag = nextTag()
        val newClock = currentState.clock.increment(nodeId)

        val newState = currentState.copy(
            itemIds = currentState.itemIds.add(id, tag.nodeId, tag.counter),
            items = currentState.items + (id to item),
            clock = newClock
        )
        deltaBuffer.recordAdd(id, item, tag, newClock)
        newState
    }

    override suspend fun removeTodo(id: String) = mutate { currentState ->
        val observedTags = currentState.itemIds.entries[id] ?: return@mutate currentState
        val newClock = currentState.clock.increment(nodeId)

        val newState = currentState.copy(
            itemIds = currentState.itemIds.remove(id),
            clock = newClock
        )
        deltaBuffer.recordRemove(id, observedTags, newClock)
        newState
    }

    override suspend fun toggleCompleted(id: String) = mutate { currentState ->
        val item = currentState.items[id] ?: return@mutate currentState
        val now = Clock.System.now()
        val newClock = currentState.clock.increment(nodeId)
        val updated = item.copy(completed = LwwRegister(!item.completed.value, now, nodeId))

        val newState = currentState.copy(
            items = currentState.items + (id to updated),
            clock = newClock
        )
        deltaBuffer.recordUpdate(id, updated, newClock)
        newState
    }

    override suspend fun updateTitle(id: String, newTitle: String) = mutate { currentState ->
        val item = currentState.items[id] ?: return@mutate currentState
        val now = Clock.System.now()
        val newClock = currentState.clock.increment(nodeId)
        val updated = item.copy(title = LwwRegister(newTitle, now, nodeId))

        val newState = currentState.copy(
            items = currentState.items + (id to updated),
            clock = newClock
        )
        deltaBuffer.recordUpdate(id, updated, newClock)
        newState
    }

    override suspend fun updateNote(id: String, newNote: String) = mutate { currentState ->
        val item = currentState.items[id] ?: return@mutate currentState
        val now = Clock.System.now()
        val newClock = currentState.clock.increment(nodeId)
        val updated = item.copy(note = LwwRegister(newNote, now, nodeId))

        val newState = currentState.copy(
            items = currentState.items + (id to updated),
            clock = newClock
        )
        deltaBuffer.recordUpdate(id, updated, newClock)
        newState
    }

    override suspend fun reorderTodo(id: String, newPosition: Double) = mutate { currentState ->
        val item = currentState.items[id] ?: return@mutate currentState
        val now = Clock.System.now()
        val newClock = currentState.clock.increment(nodeId)
        val updated = item.copy(position = LwwRegister(newPosition, now, nodeId))

        val newState = currentState.copy(
            items = currentState.items + (id to updated),
            clock = newClock
        )
        deltaBuffer.recordUpdate(id, updated, newClock)
        newState
    }

    /**
     * Called by SyncEngine when remote deltas arrive.
     */
    suspend fun mergeRemoteDelta(delta: TodoListDelta) {
        mutex.withLock {
            val remotePart = TodoListCrdt(
                itemIds = OrSet(
                    entries = delta.addedItemTags,
                    tombstones = delta.removedItemTags
                ),
                items = delta.addedOrUpdatedItems,
                clock = delta.clock
            )
            state = state.merge(remotePart)
            storage.save(state)
            emitTodos()
        }
    }

    fun getPendingDelta(): TodoListDelta? = deltaBuffer.flush()

    fun restorePendingDelta(delta: TodoListDelta) = deltaBuffer.restore(delta)

    fun getLocalClock(): VectorClock = state.clock

    private suspend fun mutate(block: (TodoListCrdt) -> TodoListCrdt) {
        mutex.withLock {
            state = block(state)
            storage.save(state)
            emitTodos()
        }
        syncEngine?.pushPendingDelta()
    }

    private fun emitTodos() {
        val activeIds = state.itemIds.elements()
        val items = activeIds.mapNotNull { id ->
            state.items[id]?.toModel()
        }.sortedBy { it.position }
        _todos.update { items }
    }

    private fun nextPosition(currentState: TodoListCrdt): Double {
        val maxPos = currentState.items.values
            .filter { currentState.itemIds.contains(it.id) }
            .maxOfOrNull { it.position.value }
            ?: 0.0
        return maxPos + 1.0
    }

    private fun nextTag(): UniqueTag {
        tagCounter++
        return UniqueTag(nodeId, tagCounter)
    }
}
