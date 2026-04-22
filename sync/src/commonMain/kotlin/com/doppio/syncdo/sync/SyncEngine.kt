package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.NodeId
import com.doppio.syncdo.crdt.VectorClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Client-side sync engine. Maintains a WebSocket connection, requests missing deltas on
 * connect, pushes local mutations as they are buffered, and applies server broadcasts.
 *
 * Generic over the application's [Delta] type. Construct with the `KSerializer` for your
 * delta (typically `MyDelta.serializer()`). The repository / domain layer is responsible
 * for: buffering local mutations ([getPendingDelta]), restoring them on failure
 * ([restorePendingDelta]), applying remote deltas ([onRemoteDelta]), and reporting the
 * local vector clock ([getLocalClock]).
 *
 * Transport errors (connect failures, send failures) are surfaced through the optional
 * [onError] callback. Defaults to a no-op so library users can opt in to logging without
 * the engine ever writing to stdout.
 */
class SyncEngine<D : Delta<D>>(
    deltaSerializer: KSerializer<D>,
    private val serverHost: String,
    private val serverPort: Int,
    private val nodeId: NodeId,
    private val scope: CoroutineScope,
    private val onRemoteDelta: suspend (D) -> Unit,
    private val getPendingDelta: () -> D?,
    private val restorePendingDelta: (D) -> Unit,
    private val getLocalClock: () -> VectorClock,
    private val path: String = "/sync",
    private val onError: (Throwable) -> Unit = {},
) {
    private val messageSerializer = SyncMessage.serializer(deltaSerializer)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(WebSockets)
    }

    val syncStatus: StateFlow<SyncStatus>
        field = MutableStateFlow(SyncStatus.Offline)

    private var session: WebSocketSession? = null
    private var connectJob: Job? = null

    fun start() {
        connectJob = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                try {
                    syncStatus.update { SyncStatus.Syncing }
                    client.webSocket(host = serverHost, port = serverPort, path = path) {
                        session = this
                        backoff = 1_000L

                        val pullRequest: SyncMessage<D> = SyncMessage.PullRequest(
                            clock = getLocalClock(),
                            nodeId = nodeId,
                        )
                        send(Frame.Text(json.encodeToString(messageSerializer, pullRequest)))

                        pushPendingDelta()
                        syncStatus.update { SyncStatus.Synced }

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = json.decodeFromString(messageSerializer, frame.readText())
                                handleMessage(message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    onError(e)
                    syncStatus.update { SyncStatus.Offline }
                }
                session = null
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun handleMessage(message: SyncMessage<D>) {
        when (message) {
            is SyncMessage.PullResponse -> {
                if (!message.delta.isEmpty()) {
                    onRemoteDelta(message.delta)
                }
                syncStatus.update { SyncStatus.Synced }
            }
            is SyncMessage.PushDelta -> {
                if (!message.delta.isEmpty()) {
                    onRemoteDelta(message.delta)
                }
                syncStatus.update { SyncStatus.Synced }
            }
            is SyncMessage.PullRequest -> Unit // Server-bound; ignore on client.
        }
    }

    suspend fun pushPendingDelta() {
        val ws = session ?: run {
            syncStatus.update { SyncStatus.PendingChanges }
            return
        }
        val delta = getPendingDelta() ?: return
        try {
            syncStatus.update { SyncStatus.Syncing }
            val message: SyncMessage<D> = SyncMessage.PushDelta(delta = delta, nodeId = nodeId)
            ws.send(Frame.Text(json.encodeToString(messageSerializer, message)))
        } catch (e: Exception) {
            onError(e)
            restorePendingDelta(delta)
            syncStatus.update { SyncStatus.PendingChanges }
        }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        scope.launch { session?.close() }
        session = null
        syncStatus.update { SyncStatus.Offline }
    }
}
