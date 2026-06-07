package com.doppio.syncdo.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Wire-format guard. These tests pin both the round-trip behaviour and a small
 * canonical JSON sample for each public `@Serializable` type. If the on-wire
 * shape changes (renamed property, missing `@SerialName`, surprising default-
 * elision), one of these assertions will fail before a real client refuses to
 * decode a server payload.
 */
class SerializationRoundTripTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun vectorClockRoundTrip() {
        val original = VectorClock(mapOf("nodeA" to 3L, "nodeB" to 1L))
        val text = json.encodeToString(VectorClock.serializer(), original)
        val decoded = json.decodeFromString(VectorClock.serializer(), text)
        assertEquals(original, decoded)
        assertEquals("""{"entries":{"nodeA":3,"nodeB":1}}""", text)
    }

    @Test
    fun lwwRegisterRoundTrip() {
        val original = LwwRegister(
            value = "hello",
            timestamp = Instant.fromEpochMilliseconds(1_000),
            nodeId = "nodeA",
        )
        val serializer = LwwRegister.serializer(String.serializer())
        val text = json.encodeToString(serializer, original)
        val decoded = json.decodeFromString(serializer, text)
        assertEquals(original, decoded)
    }

    @Test
    fun orSetRoundTrip() {
        val original = OrSet<String>()
            .add("a", "nodeA", 1)
            .add("b", "nodeB", 2)
            .remove("a")
        val serializer = OrSet.serializer(String.serializer())
        val text = json.encodeToString(serializer, original)
        val decoded = json.decodeFromString(serializer, text)
        assertEquals(original, decoded)
    }

    @Test
    fun orSetDeltaRoundTrip() {
        val original = OrSetDelta(
            addedTags = mapOf("a" to setOf(UniqueTag("nodeA", 1))),
            removedTags = mapOf("b" to setOf(UniqueTag("nodeB", 2))),
        )
        val serializer = OrSetDelta.serializer(String.serializer())
        val text = json.encodeToString(serializer, original)
        val decoded = json.decodeFromString(serializer, text)
        assertEquals(original, decoded)
    }

    @Test
    fun uniqueTagWireFormat() {
        val original = UniqueTag("nodeA", 42)
        val text = json.encodeToString(UniqueTag.serializer(), original)
        assertEquals("""{"nodeId":"nodeA","counter":42}""", text)
        assertEquals(original, json.decodeFromString(UniqueTag.serializer(), text))
    }
}
