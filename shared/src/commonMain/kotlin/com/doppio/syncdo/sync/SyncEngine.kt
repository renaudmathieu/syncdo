package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.NodeId
import com.doppio.syncdo.crdt.TodoListDelta
import com.doppio.syncdo.crdt.VectorClock
import com.doppio.syncdo.model.SyncStatus
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class SyncEngine(
    private val serverUrl: String,
    private val serverPort: Int,
    private val nodeId: NodeId,
    private val scope: CoroutineScope,
    private val onRemoteDelta: suspend (TodoListDelta) -> Unit,
    private val getPendingDelta: () -> TodoListDelta?,
    private val restorePendingDelta: (TodoListDelta) -> Unit,
    private val getLocalClock: () -> VectorClock
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(WebSockets)
    }

    private val _syncStatus = MutableStateFlow(SyncStatus.Offline)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private var session: WebSocketSession? = null
    private var connectJob: Job? = null

    fun start() {
        connectJob = scope.launch {
            var delay = 1000L
            while (isActive) {
                try {
                    _syncStatus.update { SyncStatus.Syncing }
                    client.webSocket(host = serverUrl, port = serverPort, path = "/sync") {
                        session = this
                        delay = 1000L // reset backoff on successful connection

                        // On connect, request full sync
                        val pullRequest = SyncMessage.PullRequest(
                            clock = getLocalClock(),
                            nodeId = nodeId
                        )
                        send(Frame.Text(json.encodeToString(SyncMessage.serializer(), pullRequest)))

                        // Push any pending local changes
                        pushPendingDelta()

                        _syncStatus.update { SyncStatus.Synced }

                        // Receive loop
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = json.decodeFromString(SyncMessage.serializer(), frame.readText())
                                handleMessage(message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("SyncDO: WebSocket connection error: ${e.message}")
                    _syncStatus.update { SyncStatus.Offline }
                }
                session = null
                delay(delay)
                delay = (delay * 2).coerceAtMost(30_000L) // exponential backoff, max 30s
            }
        }
    }

    private suspend fun handleMessage(message: SyncMessage) {
        when (message) {
            is SyncMessage.PullResponse -> {
                if (!message.delta.isEmpty()) {
                    onRemoteDelta(message.delta)
                }
                _syncStatus.update { SyncStatus.Synced }
            }

            is SyncMessage.FullSync -> {
                // Full sync received — merge entire state
                // We treat full state as a delta containing everything
                val fullDelta = TodoListDelta(
                    addedOrUpdatedItems = message.state.items,
                    addedItemTags = message.state.itemIds.entries,
                    removedItemTags = message.state.itemIds.tombstones,
                    clock = message.state.clock
                )
                onRemoteDelta(fullDelta)
                _syncStatus.update { SyncStatus.Synced }
            }

            else -> {} // PushDelta and PullRequest are server-side messages
        }
    }

    suspend fun pushPendingDelta() {
        val ws = session ?: run {
            _syncStatus.update { SyncStatus.PendingChanges }
            return
        }
        val delta = getPendingDelta() ?: return
        try {
            _syncStatus.update { SyncStatus.Syncing }
            val message = SyncMessage.PushDelta(delta = delta, nodeId = nodeId)
            ws.send(Frame.Text(json.encodeToString(SyncMessage.serializer(), message)))
        } catch (e: Exception) {
            println("SyncDO: Failed to push delta: ${e.message}")
            restorePendingDelta(delta)
            _syncStatus.update { SyncStatus.PendingChanges }
        }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        scope.launch { session?.close() }
        session = null
        _syncStatus.update { SyncStatus.Offline }
    }
}
