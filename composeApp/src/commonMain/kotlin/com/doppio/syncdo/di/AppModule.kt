package com.doppio.syncdo.di

import com.doppio.syncdo.SERVER_PORT
import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.crdt.TodoListDelta
import com.doppio.syncdo.persistence.JsonFileStorage
import com.doppio.syncdo.persistence.LocalStorage
import com.doppio.syncdo.persistence.getStoragePath
import com.doppio.syncdo.repository.OfflineFirstTodoRepository
import com.doppio.syncdo.repository.TodoRepository
import com.doppio.syncdo.sync.NodeIdProvider
import com.doppio.syncdo.sync.SyncEngine
import com.doppio.syncdo.sync.SyncLogLevel
import com.doppio.syncdo.sync.SyncLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object AppModule {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var _repository: OfflineFirstTodoRepository
    private var _syncEngine: SyncEngine<TodoListDelta>? = null
    private var initialized = false

    val repository: TodoRepository
        get() = _repository

    suspend fun initialize(serverHost: String = "localhost") {
        if (initialized) return

        val storagePath = getStoragePath()
        val nodeIdProvider = NodeIdProvider(storagePath)
        val nodeId = nodeIdProvider.getNodeId()
        val storage: LocalStorage = JsonFileStorage(
            basePath = storagePath,
            logger = { msg, err -> println("SyncDO: $msg ${err?.message.orEmpty()}") },
        )

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
            deltaSerializer = TodoListDelta.serializer(),
            serverHost = serverHost,
            serverPort = SERVER_PORT,
            nodeId = nodeId,
            scope = scope,
            onRemoteDelta = { delta -> _repository.mergeRemoteDelta(delta) },
            getPendingDelta = { _repository.getPendingDelta() },
            restorePendingDelta = { delta -> _repository.restorePendingDelta(delta) },
            getLocalClock = { _repository.getLocalClock() },
            logger = SyncLogger { level, msg, err ->
                if (level >= SyncLogLevel.Warn) println("SyncDO[$level] $msg ${err?.message.orEmpty()}")
            },
        )

        // Attach sync engine to repository (no second instance)
        _repository.attachSyncEngine(_syncEngine!!, scope)

        initialized = true
    }

    fun startSync() {
        _syncEngine?.start()
    }

    suspend fun stopSync() {
        _syncEngine?.stop()
    }

    /**
     * Releases the sync engine, cancels the module scope, and resets state so
     * [initialize] can be called again on a fresh app session. Safe to call
     * even if the module was never initialized.
     */
    suspend fun shutdown() {
        if (!initialized) return
        _syncEngine?.stop()
        _syncEngine = null
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        initialized = false
    }
}
