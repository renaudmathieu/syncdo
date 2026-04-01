package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.NodeId
import com.doppio.syncdo.persistence.getStoragePath
import com.doppio.syncdo.persistence.readTextFile
import com.doppio.syncdo.persistence.writeTextFile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NodeIdProvider(
    private val basePath: String = getStoragePath()
) {
    private var cachedId: NodeId? = null

    @OptIn(ExperimentalUuidApi::class)
    suspend fun getNodeId(): NodeId {
        cachedId?.let { return it }
        val path = "$basePath/node-id.txt"
        val existing = readTextFile(path)
        if (existing != null) {
            cachedId = existing.trim()
            return cachedId!!
        }
        val newId = Uuid.random().toString()
        writeTextFile(path, newId)
        cachedId = newId
        return newId
    }
}
