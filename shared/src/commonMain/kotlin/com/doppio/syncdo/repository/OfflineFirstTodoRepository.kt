package com.doppio.syncdo.repository

import com.doppio.syncdo.crdt.*
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.model.toModel
import com.doppio.syncdo.persistence.LocalStorage
import com.doppio.syncdo.sync.SyncEngine
import com.doppio.syncdo.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
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
    private val deltaBuffer = DeltaBuffer<TodoListDelta>()
    private var tagCounter = initialState.clock[nodeId]
    private var syncEngine: SyncEngine<TodoListDelta>? = null
    private var statusCollectionJob: kotlinx.coroutines.Job? = null

    override val todos: StateFlow<List<TodoItem>> = crdtState
        .map { it.toTodoItems() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = initialState.toTodoItems()
        )

    override val syncStatus: StateFlow<SyncStatus>
        field = MutableStateFlow(SyncStatus.Offline)

    fun attachSyncEngine(engine: SyncEngine<TodoListDelta>, scope: CoroutineScope) {
        statusCollectionJob?.cancel()
        syncEngine = engine
        statusCollectionJob = scope.launch {
            engine.syncStatus.collect { status -> syncStatus.update { status } }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun addTodo(title: String) = mutate { currentState ->
        val id = Uuid.random().toString()
        val now = Clock.System.now()
        val item = TodoItemCrdt.create(id, title, nextPosition(currentState), nodeId, now)
        val (newIds, membershipDelta) = currentState.itemIds.addWithDelta(id, nodeId, nextTagCounter())
        val newClock = currentState.clock.increment(nodeId)

        deltaBuffer.record(TodoListDelta(
            items = mapOf(id to item),
            membership = membershipDelta,
            clock = newClock,
        ))
        currentState.copy(
            itemIds = newIds,
            items = currentState.items + (id to item),
            clock = newClock,
        )
    }

    override suspend fun removeTodo(id: String) = mutate { currentState ->
        val (newIds, membershipDelta) = currentState.itemIds.removeWithDelta(id)
        if (membershipDelta.isEmpty()) return@mutate currentState
        val newClock = currentState.clock.increment(nodeId)

        deltaBuffer.record(TodoListDelta(
            membership = membershipDelta,
            clock = newClock,
        ))
        currentState.copy(itemIds = newIds, clock = newClock)
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
        mutate { currentState ->
            val item = currentState.items[id] ?: return@mutate currentState
            val updated = item.transform()
            val newClock = currentState.clock.increment(nodeId)

            deltaBuffer.record(TodoListDelta(
                items = mapOf(id to updated),
                clock = newClock,
            ))
            currentState.copy(
                items = currentState.items + (id to updated),
                clock = newClock,
            )
        }

    /**
     * Called by SyncEngine when remote deltas arrive.
     */
    suspend fun mergeRemoteDelta(delta: TodoListDelta) {
        mutex.withLock {
            crdtState.value = crdtState.value.applyDelta(delta)
            storage.save(crdtState.value)
        }
    }

    suspend fun getPendingDelta(): TodoListDelta? = deltaBuffer.flush()

    suspend fun restorePendingDelta(delta: TodoListDelta) = deltaBuffer.restore(delta)

    fun getLocalClock(): VectorClock = crdtState.value.clock

    private suspend fun mutate(block: suspend (TodoListCrdt) -> TodoListCrdt) {
        mutex.withLock {
            crdtState.value = block(crdtState.value)
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

    private fun nextTagCounter(): Long {
        tagCounter++
        return tagCounter
    }

    private fun TodoListCrdt.toTodoItems(): List<TodoItem> =
        itemIds.elements().mapNotNull { id -> items[id]?.toModel() }.sortedBy { it.position }
}
