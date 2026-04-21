package com.doppio.syncdo

import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.crdt.TodoListDelta
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

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSockets()
    configureSerialization()

    val syncServer = SyncServer(
        initialState = { TodoListCrdt() },
        fullStateAsDelta = { state ->
            TodoListDelta(
                items = state.items,
                membership = state.itemIds.toDelta(),
                clock = state.clock,
            )
        },
    )

    routing {
        get("/") {
            call.respondText("SyncDO Server is running")
        }
        syncEndpoint(syncServer, TodoListDelta.serializer(), path = "/sync")
    }
}
