package com.doppio.syncdo.sync.server

import com.doppio.syncdo.sync.ClockedState
import com.doppio.syncdo.sync.SyncMessage
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.Collections
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Installs a WebSocket sync endpoint that handles [SyncMessage] traffic for the given
 * [server]. Each connection gets its pull/push messages processed and broadcasts
 * subsequent deltas to every other live connection.
 *
 * Requires the Ktor `WebSockets` plugin to be installed on the application.
 *
 * Example:
 * ```kotlin
 * install(WebSockets)
 * val syncServer = SyncServer(MyCrdt())
 * routing {
 *     syncEndpoint(syncServer, MyCrdt.serializer())
 * }
 * ```
 */
fun <S : ClockedState<S>> Route.syncEndpoint(
    server: SyncServer<S>,
    deltaSerializer: KSerializer<S>,
    path: String = "/sync",
) {
    val messageSerializer = SyncMessage.serializer(deltaSerializer)
    val json = Json { ignoreUnknownKeys = true }
    val connections = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

    webSocket(path) {
        /** Answer with what this client is missing, if anything. */
        suspend fun sendMissed(delta: S?) {
            if (delta == null) return
            val response: SyncMessage<S> = SyncMessage.PullResponse(delta)
            send(Frame.Text(json.encodeToString(messageSerializer, response)))
        }

        connections += this
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val message = json.decodeFromString(messageSerializer, frame.readText())
                when (message) {
                    is SyncMessage.PushDelta -> {
                        server.mergeDelta(message.delta)
                        val broadcast: SyncMessage<S> = SyncMessage.PullResponse(message.delta)
                        val payload = json.encodeToString(messageSerializer, broadcast)
                        // Snapshot peers under the set's monitor to avoid CME, then send outside it.
                        val peers = synchronized(connections) { connections.toList() }
                        peers.forEach { peer ->
                            if (peer !== this) {
                                runCatching { peer.send(Frame.Text(payload)) }
                                    .onFailure { connections -= peer }
                            }
                        }
                        sendMissed(server.getDeltaSince(message.delta.clock))
                    }
                    is SyncMessage.PullRequest -> sendMissed(server.getDeltaSince(message.clock))
                    is SyncMessage.PullResponse -> Unit // Client-bound; ignored on server.
                }
            }
        } finally {
            connections -= this
        }
    }
}
