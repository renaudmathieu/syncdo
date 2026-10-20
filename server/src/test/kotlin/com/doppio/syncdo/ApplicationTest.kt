package com.doppio.syncdo

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        // A fresh directory per run: the test must not read or write the developer's state.
        val stateDir = createTempDirectory("syncdo-test").toString()
        application {
            module(stateDir)
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("SyncDO Server is running", response.bodyAsText())
    }
}
