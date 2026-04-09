package com.doppio.syncdo.di

import com.doppio.syncdo.SERVER_PORT
import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.persistence.JsonFileStorage
import com.doppio.syncdo.persistence.LocalStorage
import com.doppio.syncdo.persistence.getStoragePath
import com.doppio.syncdo.repository.OfflineFirstTodoRepository
import com.doppio.syncdo.repository.TodoRepository
import com.doppio.syncdo.sync.NodeIdProvider
import com.doppio.syncdo.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppModule {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var _repository: OfflineFirstTodoRepository
    private var _syncEngine: SyncEngine? = null
    private var initialized = false

    val repository: TodoRepository
        get() = _repository

    suspend fun initialize(serverHost: String = "localhost") {
        if (initialized) return

        val storagePath = getStoragePath()
        val nodeIdProvider = NodeIdProvider(storagePath)
        val nodeId = nodeIdProvider.getNodeId()
        val storage: LocalStorage = JsonFileStorage(storagePath)

        // Create repository once
        val initialState = storage.load() ?: TodoListCrdt()
        _repository = OfflineFirstTodoRepository(
            nodeId = nodeId,
            storage = storage,
            initialState = initialState,
            scope = scope,
        )

        // Create sync engine with lambdas pointing to the SAME repository instance
        _syncEngine = SyncEngine(
            serverUrl = serverHost,
            serverPort = SERVER_PORT,
            nodeId = nodeId,
            scope = scope,
            onRemoteDelta = { delta -> _repository.mergeRemoteDelta(delta) },
            getPendingDelta = { _repository.getPendingDelta() },
            restorePendingDelta = { delta -> _repository.restorePendingDelta(delta) },
            getLocalClock = { _repository.getLocalClock() }
        )

        // Attach sync engine to repository (no second instance)
        _repository.attachSyncEngine(_syncEngine!!, scope)

        initialized = true
    }

    fun startSync() {
        _syncEngine?.start()
    }

    fun stopSync() {
        _syncEngine?.stop()
    }
}
