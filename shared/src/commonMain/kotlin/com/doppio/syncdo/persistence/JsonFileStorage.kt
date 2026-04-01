package com.doppio.syncdo.persistence

import com.doppio.syncdo.crdt.TodoListCrdt
import kotlinx.serialization.json.Json

class JsonFileStorage(
    private val basePath: String = getStoragePath()
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
        } catch (e: Exception) {
            println("SyncDO: Failed to deserialize state from $filePath: ${e.message}")
            throw IllegalStateException("Corrupted state file at $filePath", e)
        }
    }

    override suspend fun save(state: TodoListCrdt) {
        val text = json.encodeToString(TodoListCrdt.serializer(), state)
        writeTextFile(filePath, text)
    }
}
