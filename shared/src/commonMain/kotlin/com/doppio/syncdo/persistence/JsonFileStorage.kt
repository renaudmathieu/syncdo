package com.doppio.syncdo.persistence

import com.doppio.syncdo.crdt.TodoListCrdt
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class JsonFileStorage(
    private val basePath: String = getStoragePath(),
    private val logger: (String, Throwable?) -> Unit = { _, _ -> },
) : LocalStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val filePath get() = "$basePath/syncdo-state.json"

    override suspend fun load(): TodoListCrdt? {
        val text = readTextFile(filePath) ?: return null
        return try {
            json.decodeFromString<TodoListCrdt>(text)
        } catch (e: SerializationException) {
            quarantineCorruptFile(text, e)
            null
        }
    }

    override suspend fun save(state: TodoListCrdt) {
        val text = json.encodeToString(TodoListCrdt.serializer(), state)
        writeTextFile(filePath, text)
    }

    private suspend fun quarantineCorruptFile(originalText: String, cause: Throwable) {
        val backupPath = "$filePath.corrupt-${Clock.System.now().toEpochMilliseconds()}"
        logger("Corrupted state at $filePath; quarantined to $backupPath", cause)
        runCatching { writeTextFile(backupPath, originalText) }
        runCatching { deleteFile(filePath) }
    }
}
