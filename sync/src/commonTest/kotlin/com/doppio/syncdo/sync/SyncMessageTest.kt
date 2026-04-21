package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.VectorClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class FakeDelta(
    override val clock: VectorClock = VectorClock(),
    val value: Int = 0,
) : Delta<FakeDelta> {
    override fun merge(other: FakeDelta) =
        FakeDelta(clock.merge(other.clock), value + other.value)

    override fun isEmpty(): Boolean = value == 0
}

class SyncMessageTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val messageSerializer = SyncMessage.serializer(FakeDelta.serializer())

    @Test
    fun pushDelta_roundTripsThroughJson() {
        val original: SyncMessage<FakeDelta> = SyncMessage.PushDelta(
            delta = FakeDelta(VectorClock(mapOf("node-a" to 3L)), value = 42),
            nodeId = "node-a",
        )

        val encoded = json.encodeToString(messageSerializer, original)
        val decoded = json.decodeFromString(messageSerializer, encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("push_delta"))
    }

    @Test
    fun pullRequest_roundTripsThroughJson() {
        val original: SyncMessage<FakeDelta> = SyncMessage.PullRequest(
            clock = VectorClock(mapOf("node-a" to 7L)),
            nodeId = "node-a",
        )

        val encoded = json.encodeToString(messageSerializer, original)
        val decoded = json.decodeFromString(messageSerializer, encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("pull_request"))
    }

    @Test
    fun pullResponse_roundTripsThroughJson() {
        val original: SyncMessage<FakeDelta> = SyncMessage.PullResponse(
            delta = FakeDelta(VectorClock(mapOf("server" to 1L)), value = 9),
        )

        val encoded = json.encodeToString(messageSerializer, original)
        val decoded = json.decodeFromString(messageSerializer, encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("pull_response"))
    }
}
