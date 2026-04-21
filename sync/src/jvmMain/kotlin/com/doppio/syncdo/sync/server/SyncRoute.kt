package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.DeltaState
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
 * val syncServer = SyncServer(::MyCrdt, ::fullStateAsDelta)
 * routing {
 *     syncEndpoint(syncServer, MyDelta.serializer())
 * }
 * ```
 */
fun <S : DeltaState<S, D>, D : Delta<D>> Route.syncEndpoint(
    server: SyncServer<S, D>,
    deltaSerializer: KSerializer<D>,
    path: String = "/sync",
) {
    val messageSerializer = SyncMessage.serializer(deltaSerializer)
    val json = Json { ignoreUnknownKeys = true }
    val connections = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

    webSocket(path) {
        connections += this
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val message = json.decodeFromString(messageSerializer, frame.readText())
                when (message) {
                    is SyncMessage.PushDelta -> {
                        server.mergeDelta(message.delta)
                        val broadcast: SyncMessage<D> = SyncMessage.PullResponse(message.delta)
                        val payload = json.encodeToString(messageSerializer, broadcast)
                        connections.forEach { peer ->
                            if (peer !== this) {
                                runCatching { peer.send(Frame.Text(payload)) }
                            }
                        }
                        val missed = server.getDeltaSince(message.delta.clock)
                        if (!missed.isEmpty()) {
                            val catchup: SyncMessage<D> = SyncMessage.PullResponse(missed)
                            send(Frame.Text(json.encodeToString(messageSerializer, catchup)))
                        }
                    }
                    is SyncMessage.PullRequest -> {
                        val delta = server.getDeltaSince(message.clock)
                        val response: SyncMessage<D> = SyncMessage.PullResponse(delta)
                        send(Frame.Text(json.encodeToString(messageSerializer, response)))
                    }
                    is SyncMessage.PullResponse -> Unit // Client-bound; ignored on server.
                }
            }
        } finally {
            connections -= this
        }
    }
}
