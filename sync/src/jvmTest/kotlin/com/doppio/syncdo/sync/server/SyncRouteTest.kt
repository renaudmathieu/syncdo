package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.DeltaState
import com.doppio.syncdo.crdt.VectorClock
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

@Serializable
data class CounterState(
    override val clock: VectorClock = VectorClock(),
    val value: Int = 0,
) : DeltaState<CounterState, CounterDelta> {
    override fun merge(other: CounterState): CounterState = CounterState(
        clock = clock.merge(other.clock),
        value = maxOf(value, other.value),
    )

    override fun applyDelta(delta: CounterDelta): CounterState = merge(
        CounterState(clock = delta.clock, value = delta.value),
    )
}

@Serializable
data class CounterDelta(
    override val clock: VectorClock,
    val value: Int,
) : Delta<CounterDelta> {
    override fun merge(other: CounterDelta): CounterDelta = CounterDelta(
        clock = clock.merge(other.clock),
        value = maxOf(value, other.value),
    )

    override fun isEmpty(): Boolean = value == 0 && clock.entries.isEmpty()
}

private val json = Json { ignoreUnknownKeys = true }
private val msg = SyncMessage.serializer(CounterDelta.serializer())

class SyncRouteTest {

    @Test
    fun pushFromOneClientBroadcastsToOthers() = testApplication {
        application {
            install(ServerWebSockets)
            val server = SyncServer<CounterState, CounterDelta>(
                initialState = { CounterState() },
                fullStateAsDelta = { state -> CounterDelta(state.clock, state.value) },
            )
            routing {
                syncEndpoint(server, CounterDelta.serializer())
            }
        }
        val client = createClient { install(WebSockets) }

        val broadcastReceived = CompletableDeferred<SyncMessage.PullResponse<CounterDelta>>()
        val receiverReady = CompletableDeferred<Unit>()

        coroutineScope {
            val receiverJob = launch {
                client.webSocket("/sync") {
                    val pull: SyncMessage<CounterDelta> = SyncMessage.PullRequest(VectorClock(), "B")
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
                val delta = CounterDelta(VectorClock(mapOf("A" to 1L)), value = 7)
                val push: SyncMessage<CounterDelta> = SyncMessage.PushDelta(delta, nodeId = "A")
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
            val server = SyncServer<CounterState, CounterDelta>(
                initialState = { CounterState(clock = VectorClock(mapOf("X" to 5L)), value = 42) },
                fullStateAsDelta = { state -> CounterDelta(state.clock, state.value) },
            )
            routing {
                syncEndpoint(server, CounterDelta.serializer())
            }
        }
        val client = createClient { install(WebSockets) }

        client.webSocket("/sync") {
            val req: SyncMessage<CounterDelta> = SyncMessage.PullRequest(VectorClock(), "client")
            send(Frame.Text(json.encodeToString(msg, req)))

            val frame = incoming.receive() as Frame.Text
            val decoded = json.decodeFromString(msg, frame.readText()) as SyncMessage.PullResponse<CounterDelta>
            assertEquals(42, decoded.delta.value)
            assertEquals(5L, decoded.delta.clock["X"])
        }
    }
}
