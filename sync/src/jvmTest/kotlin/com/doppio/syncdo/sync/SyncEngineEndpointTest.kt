package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.DeltaBuffer
import com.doppio.syncdo.sync.server.SyncServer
import com.doppio.syncdo.sync.server.syncEndpoint
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineEndpointTest {

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var syncServer: SyncServer<TestState, TestDelta>
    private var port: Int = 0

    @BeforeTest
    fun startServer() {
        port = ServerSocket(0).use { it.localPort }
        syncServer = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )
        server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            routing {
                syncEndpoint(syncServer, TestDelta.serializer())
            }
        }.start(wait = false)
    }

    @AfterTest
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 100)
    }

    @Test
    fun clientsConvergeViaPushAndBroadcast() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clientA = TestClient(nodeId = "a", scope = scopeA, port = port)
        val clientB = TestClient(nodeId = "b", scope = scopeB, port = port)

        try {
            clientA.engine.start()
            clientB.engine.start()
            awaitSynced(clientA, clientB)

            clientA.recordLocal(setOf("x"))

            awaitUntil {
                clientB.receivedDeltas.any { "x" in it.entries }
            }
            assertTrue("x" in clientB.state.values, "client B should converge on x")
        } finally {
            clientA.engine.stop()
            clientB.engine.stop()
            scopeA.cancel()
            scopeB.cancel()
        }
    }

    @Test
    fun freshClientReceivesServerStateOnConnect() = runBlocking {
        syncServer.mergeDelta(TestDelta(clockOf("seed" to 1), setOf("pre-existing")))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = TestClient(nodeId = "fresh", scope = scope, port = port)

        try {
            client.engine.start()
            awaitUntil {
                "pre-existing" in client.state.values
            }
            assertEquals(setOf("pre-existing"), client.state.values)
        } finally {
            client.engine.stop()
            scope.cancel()
        }
    }

    @Test
    fun offlineEditIsRestoredAndPushedOnReconnect() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = TestClient(nodeId = "c", scope = scope, port = port)

        try {
            // Record while offline — no engine started, no session.
            client.recordLocal(setOf("offline-edit"))
            client.engine.pushPendingDelta() // no session yet → restored

            assertTrue(client.hasPending(), "delta should still be pending while offline")

            client.engine.start()
            awaitUntil {
                "offline-edit" in syncServer.currentState().values
            }
        } finally {
            client.engine.stop()
            scope.cancel()
        }
    }

    // --- helpers ---

    private suspend fun awaitSynced(vararg clients: TestClient) = awaitUntil {
        clients.all { it.engine.syncStatus.value == SyncStatus.Synced }
    }

    private suspend fun awaitUntil(
        timeoutMs: Long = 5_000,
        predicate: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!predicate()) delay(25)
        }
    }
}

private class TestClient(
    val nodeId: String,
    val scope: CoroutineScope,
    port: Int,
) {
    var state: TestState = TestState()
        private set
    val receivedDeltas = mutableListOf<TestDelta>()
    private val buffer = DeltaBuffer<TestDelta>()
    private var localCounter: Long = 0

    val engine: SyncEngine<TestDelta> = SyncEngine(
        deltaSerializer = TestDelta.serializer(),
        serverHost = "127.0.0.1",
        serverPort = port,
        nodeId = nodeId,
        scope = scope,
        onRemoteDelta = { delta ->
            receivedDeltas += delta
            state = state.applyDelta(delta)
        },
        getPendingDelta = { buffer.flush() },
        restorePendingDelta = { delta -> buffer.restore(delta) },
        getLocalClock = { state.clock },
    )

    fun recordLocal(values: Set<String>) {
        localCounter += 1
        val delta = TestDelta(
            clock = state.clock.increment(nodeId),
            entries = values,
        )
        state = state.applyDelta(delta)
        buffer.record(delta)
        scope.launch { engine.pushPendingDelta() }
    }

    fun hasPending(): Boolean {
        val drained = buffer.flush() ?: return false
        buffer.restore(drained)
        return true
    }
}
