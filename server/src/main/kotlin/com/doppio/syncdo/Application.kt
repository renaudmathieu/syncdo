package com.doppio.syncdo

import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.crdt.TodoListDelta
import com.doppio.syncdo.persistence.JsonFileStorage
import com.doppio.syncdo.plugins.configureSerialization
import com.doppio.syncdo.plugins.configureSockets
import com.doppio.syncdo.sync.server.SyncServer
import com.doppio.syncdo.sync.server.syncEndpoint
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.doppio.syncdo.Application")

fun main() {
    val port = System.getenv("SYNCDO_PORT")?.toIntOrNull() ?: SERVER_PORT
    val server = embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down SyncDO server…")
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
    })

    logger.info("SyncDO server listening on :{}", port)
    server.start(wait = true)
}

fun Application.module() {
    configureSockets()
    configureSerialization()

    val storage = JsonFileStorage(
        basePath = System.getenv("SYNCDO_STATE_DIR") ?: "./data",
        logger = { msg, err -> logger.warn("storage: {}", msg, err) },
    )
    val initial = runBlocking { storage.load() } ?: TodoListCrdt()
    logger.info("Loaded {} todo items from disk", initial.itemIds.elements().size)

    val syncServer = SyncServer(
        initialState = { initial },
        fullStateAsDelta = { state ->
            TodoListDelta(
                items = state.items,
                membership = state.itemIds.toDelta(),
                clock = state.clock,
            )
        },
        onStateChanged = { state -> storage.save(state) },
    )

    routing {
        get("/") {
            call.respondText("SyncDO Server is running")
        }
        syncEndpoint(syncServer, TodoListDelta.serializer(), path = "/sync")
    }
}
