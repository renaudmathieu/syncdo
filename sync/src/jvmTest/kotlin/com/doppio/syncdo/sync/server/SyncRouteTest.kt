package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.VectorClock
import com.doppio.syncdo.sync.ClockedState
import com.doppio.syncdo.sync.SyncMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Max-register: enough to observe what the server forwards. Also used as delta. */
@Serializable
data class CounterState(
    override val clock: VectorClock = VectorClock(),
    val value: Int = 0,
) : ClockedState<CounterState> {
    override fun merge(other: CounterState): CounterState = CounterState(
        clock = clock.merge(other.clock),
        value = maxOf(value, other.value),
    )
}

private val json = Json { ignoreUnknownKeys = true }
private val msg = SyncMessage.serializer(CounterState.serializer())

class SyncRouteTest {

    @Test
    fun pushFromOneClientBroadcastsToOthers() = testApplication {
        application {
            install(ServerWebSockets)
            val server = SyncServer(
                // Non-empty so the receiver's first pull gets an answer to wait on.
                initialState = CounterState(clock = VectorClock(mapOf("X" to 1L)), value = 1),
            )
            routing {
                syncEndpoint(server, CounterState.serializer())
            }
        }
        val client = createClient { install(WebSockets) }

        val broadcastReceived = CompletableDeferred<SyncMessage.PullResponse<CounterState>>()
        val receiverReady = CompletableDeferred<Unit>()

        coroutineScope {
            val receiverJob = launch {
                client.webSocket("/sync") {
                    val pull: SyncMessage<CounterState> = SyncMessage.PullRequest(VectorClock(), "B")
                    send(Frame.Text(json.encodeToString(msg, pull)))
                    val initial = (incoming.receive() as Frame.Text).readText()
                    json.decodeFromString(msg, initial)
                    receiverReady.complete(Unit)

                    withTimeoutOrNull(3_000) {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val decoded = json.decodeFromString(msg, frame.readText())
                            if (decoded is SyncMessage.PullResponse) {
                                broadcastReceived.complete(decoded)
                                break
                            }
                        }
                    }
                }
            }

            receiverReady.await()

            client.webSocket("/sync") {
                val delta = CounterState(VectorClock(mapOf("A" to 1L)), value = 7)
                val push: SyncMessage<CounterState> = SyncMessage.PushDelta(delta, nodeId = "A")
                send(Frame.Text(json.encodeToString(msg, push)))
                delay(200)
            }

            val broadcast = withTimeoutOrNull(3_000) { broadcastReceived.await() }
            assertTrue(broadcast != null, "expected broadcast PullResponse on receiver")
            assertEquals(7, broadcast.delta.value)
            assertEquals(1L, broadcast.delta.clock["A"])

            receiverJob.cancel()
        }
    }

    @Test
    fun pullRequestReturnsFullStateOnEmptyClock() = testApplication {
        application {
            install(ServerWebSockets)
            val server = SyncServer(
                initialState = CounterState(clock = VectorClock(mapOf("X" to 5L)), value = 42),
            )
            routing {
                syncEndpoint(server, CounterState.serializer())
            }
        }
        val client = createClient { install(WebSockets) }

        client.webSocket("/sync") {
            val req: SyncMessage<CounterState> = SyncMessage.PullRequest(VectorClock(), "client")
            send(Frame.Text(json.encodeToString(msg, req)))

            val frame = incoming.receive() as Frame.Text
            val decoded = json.decodeFromString(msg, frame.readText()) as SyncMessage.PullResponse<CounterState>
            assertEquals(42, decoded.delta.value)
            assertEquals(5L, decoded.delta.clock["X"])
        }
    }
}
