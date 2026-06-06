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
import kotlinx.coroutines.cancelAndJoin
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
 * delta (typically `MyDelta.serializer()`).
 *
 * **Callback contract:**
 * - [onRemoteDelta] — invoked for every incoming non-empty delta. Exceptions here cause
 *   the connection to be torn down and reconnected with exponential backoff; the engine
 *   does **not** retry the delta itself, so callers should ensure their merge is
 *   idempotent under repeated delivery.
 * - [getPendingDelta] — called when a connection is established or after a local mutation.
 *   Should atomically read-and-clear the application's outbound buffer (returning `null`
 *   if nothing is pending). Suspending so callers can use a mutex.
 * - [restorePendingDelta] — called only if a [getPendingDelta]-returned delta failed to
 *   send. Must put the delta back at the head of the buffer; the engine will retry on
 *   the next successful connection.
 * - [getLocalClock] — read on connect to build the initial [SyncMessage.PullRequest].
 *   Must reflect every delta the application has already applied locally.
 */
class SyncEngine<D : Delta<D>>(
    deltaSerializer: KSerializer<D>,
    private val serverHost: String,
    private val serverPort: Int,
    private val nodeId: NodeId,
    private val scope: CoroutineScope,
    private val onRemoteDelta: suspend (D) -> Unit,
    private val getPendingDelta: suspend () -> D?,
    private val restorePendingDelta: suspend (D) -> Unit,
    private val getLocalClock: () -> VectorClock,
    private val path: String = "/sync",
    private val logger: SyncLogger = SyncLogger.Noop,
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
                    logger.log(SyncLogLevel.Warn, "SyncEngine: connection error: ${e.message}", e)
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
            logger.log(SyncLogLevel.Warn, "SyncEngine: failed to push delta: ${e.message}", e)
            restorePendingDelta(delta)
            syncStatus.update { SyncStatus.PendingChanges }
        }
    }

    suspend fun stop() {
        connectJob?.cancelAndJoin()
        connectJob = null
        runCatching { session?.close() }
        session = null
        syncStatus.update { SyncStatus.Offline }
    }
}
