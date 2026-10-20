package com.doppio.syncdo.repository

import com.doppio.syncdo.crdt.*
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.model.toModel
import com.doppio.syncdo.persistence.LocalStorage
import com.doppio.syncdo.sync.DeltaBuffer
import com.doppio.syncdo.sync.SyncEngine
import com.doppio.syncdo.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OfflineFirstTodoRepository(
    private val nodeId: NodeId,
    private val storage: LocalStorage,
    initialState: TodoListCrdt = TodoListCrdt(),
    scope: CoroutineScope,
) : TodoRepository {

    private val mutex = Mutex()
    private val crdtState = MutableStateFlow(initialState)
    private val deltaBuffer = DeltaBuffer<TodoListCrdt>()
    private var syncEngine: SyncEngine<TodoListCrdt>? = null
    private var statusCollectionJob: Job? = null

    override val todos: StateFlow<List<TodoItem>> = crdtState
        .map { it.toTodoItems() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = initialState.toTodoItems()
        )

    private val _syncStatus = MutableStateFlow(SyncStatus.Offline)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun attachSyncEngine(engine: SyncEngine<TodoListCrdt>, scope: CoroutineScope) {
        statusCollectionJob?.cancel()
        syncEngine = engine
        statusCollectionJob = scope.launch {
            engine.syncStatus.collect { status -> _syncStatus.value = status }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun addTodo(title: String) = mutate { state ->
        val id = Uuid.random().toString()
        val clock = state.clock.increment(nodeId)
        // Our own clock entry never repeats, so it doubles as a unique tag counter.
        val tag = UniqueTag(nodeId, clock[nodeId])
        val item = TodoItemCrdt.create(id, title, nextPosition(state), nodeId, Clock.System.now())
        state.commit(
            TodoListCrdt(
                itemIds = state.itemIds.addDelta(id, tag),
                items = mapOf(id to item),
                clock = clock,
            )
        )
    }

    override suspend fun removeTodo(id: String) = mutate { state ->
        val membershipDelta = state.itemIds.removeDelta(id)
        if (membershipDelta.isEmpty()) return@mutate state
        state.commit(TodoListCrdt(itemIds = membershipDelta, clock = state.clock.increment(nodeId)))
    }

    override suspend fun toggleCompleted(id: String) =
        updateItem(id) { copy(completed = LwwRegister(!completed.value, Clock.System.now(), nodeId)) }

    override suspend fun updateTitle(id: String, newTitle: String) =
        updateItem(id) { copy(title = LwwRegister(newTitle, Clock.System.now(), nodeId)) }

    override suspend fun updateNote(id: String, newNote: String) =
        updateItem(id) { copy(note = LwwRegister(newNote, Clock.System.now(), nodeId)) }

    override suspend fun reorderTodo(id: String, newPosition: Double) =
        updateItem(id) { copy(position = LwwRegister(newPosition, Clock.System.now(), nodeId)) }

    private suspend fun updateItem(id: String, transform: TodoItemCrdt.() -> TodoItemCrdt) =
        mutate { state ->
            val item = state.items[id]?.takeIf { state.itemIds.contains(id) } ?: return@mutate state
            state.commit(TodoListCrdt(items = mapOf(id to item.transform()), clock = state.clock.increment(nodeId)))
        }

    /** Record [delta] for sync and apply it locally: the same value serves both purposes. */
    private suspend fun TodoListCrdt.commit(delta: TodoListCrdt): TodoListCrdt {
        deltaBuffer.record(delta)
        return merge(delta)
    }

    /**
     * Called by SyncEngine when remote deltas arrive.
     */
    suspend fun mergeRemoteDelta(delta: TodoListCrdt) {
        mutex.withLock {
            crdtState.value = crdtState.value.merge(delta)
            storage.save(crdtState.value)
        }
    }

    suspend fun getPendingDelta(): TodoListCrdt? = deltaBuffer.flush()

    suspend fun restorePendingDelta(delta: TodoListCrdt) = deltaBuffer.record(delta)

    fun getLocalClock(): VectorClock = crdtState.value.clock

    private suspend fun mutate(block: suspend (TodoListCrdt) -> TodoListCrdt) {
        mutex.withLock {
            crdtState.update { block(crdtState.value) }
            storage.save(crdtState.value)
        }
        syncEngine?.pushPendingDelta()
    }

    private fun nextPosition(currentState: TodoListCrdt): Double {
        val maxPos = currentState.items.values
            .filter { currentState.itemIds.contains(it.id) }
            .maxOfOrNull { it.position.value }
            ?: 0.0
        return maxPos + 1.0
    }

    private fun TodoListCrdt.toTodoItems(): List<TodoItem> =
        itemIds.elements().mapNotNull { id -> items[id]?.toModel() }.sortedBy { it.position }
}
