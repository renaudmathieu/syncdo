package com.doppio.syncdo.routes

import com.doppio.syncdo.store.ServerStateStore
import com.doppio.syncdo.sync.SyncMessage
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.util.Collections

private val json = Json { ignoreUnknownKeys = true }

fun Route.syncRoutes(store: ServerStateStore) {
    val connections = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

    webSocket("/sync") {
        connections += this
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = json.decodeFromString(SyncMessage.serializer(), text)

                    when (message) {
                        is SyncMessage.PushDelta -> {
                            // Merge into server state
                            store.mergeDelta(message.delta)

                            // Broadcast to other connected clients
                            val response = SyncMessage.PullResponse(message.delta)
                            val responseText = json.encodeToString(SyncMessage.serializer(), response)
                            connections.forEach { session ->
                                if (session != this) {
                                    try {
                                        session.send(Frame.Text(responseText))
                                    } catch (_: Exception) {
                                        // Client disconnected
                                    }
                                }
                            }

                            // Respond to the pusher with any changes they might have missed
                            val missedDelta = store.getDeltaSince(message.delta.clock)
                            if (!missedDelta.isEmpty()) {
                                val missedResponse = SyncMessage.PullResponse(missedDelta)
                                send(Frame.Text(json.encodeToString(SyncMessage.serializer(), missedResponse)))
                            }
                        }

                        is SyncMessage.PullRequest -> {
                            val delta = store.getDeltaSince(message.clock)
                            val response = SyncMessage.PullResponse(delta)
                            send(Frame.Text(json.encodeToString(SyncMessage.serializer(), response)))
                        }

                        else -> {} // FullSync and PullResponse are client-side messages
                    }
                }
            }
        } finally {
            connections -= this
        }
    }
}
