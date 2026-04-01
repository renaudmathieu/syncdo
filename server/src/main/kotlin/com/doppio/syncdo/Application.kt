package com.doppio.syncdo

import com.doppio.syncdo.plugins.configureSerialization
import com.doppio.syncdo.plugins.configureSockets
import com.doppio.syncdo.routes.syncRoutes
import com.doppio.syncdo.store.ServerStateStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSockets()
    configureSerialization()

    val store = ServerStateStore()

    routing {
        get("/") {
            call.respondText("SyncDO Server is running")
        }
        syncRoutes(store)
    }
}
