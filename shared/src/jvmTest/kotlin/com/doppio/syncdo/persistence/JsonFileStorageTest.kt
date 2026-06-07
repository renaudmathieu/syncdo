package com.doppio.syncdo.persistence

import com.doppio.syncdo.crdt.OrSet
import com.doppio.syncdo.crdt.TodoItemCrdt
import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.crdt.VectorClock
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class JsonFileStorageTest {

    private lateinit var tempDir: File
    private lateinit var storage: JsonFileStorage
    private val capturedLogs = mutableListOf<Pair<String, Throwable?>>()

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("syncdo-test").toFile()
        capturedLogs.clear()
        storage = JsonFileStorage(
            basePath = tempDir.absolutePath,
            logger = { msg, err -> capturedLogs += msg to err },
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sampleState(): TodoListCrdt {
        val item = TodoItemCrdt.create(
            id = "1",
            title = "buy milk",
            position = 1.0,
            nodeId = "node-A",
            timestamp = Instant.fromEpochMilliseconds(1_000),
        )
        return TodoListCrdt(
            itemIds = OrSet<String>().add("1", "node-A", 1),
            items = mapOf("1" to item),
            clock = VectorClock().increment("node-A"),
        )
    }

    @Test
    fun `load returns null when file missing`() = runTest {
        assertNull(storage.load())
    }

    @Test
    fun `save then load round-trips state`() = runTest {
        val state = sampleState()
        storage.save(state)

        val loaded = storage.load()
        assertNotNull(loaded)
        assertTrue(loaded.itemIds.contains("1"))
        assertEquals("buy milk", loaded.items["1"]!!.title.value)
    }

    @Test
    fun `load quarantines corrupt JSON and returns null`() = runTest {
        val statePath = File(tempDir, "syncdo-state.json")
        statePath.writeText("{ this is not valid json")

        val result = storage.load()

        assertNull(result)
        assertTrue(capturedLogs.isNotEmpty(), "logger should have been called")
        assertTrue(!statePath.exists(), "corrupt file should be removed")
        val backups = tempDir.listFiles()?.filter { it.name.contains(".corrupt-") }.orEmpty()
        assertEquals(1, backups.size, "exactly one quarantine backup expected")
        assertEquals("{ this is not valid json", backups.single().readText())
    }

    @Test
    fun `save leaves no tmp residue on success`() = runTest {
        storage.save(sampleState())
        val names = tempDir.listFiles()?.map { it.name }.orEmpty()
        assertEquals(listOf("syncdo-state.json"), names)
    }
}
