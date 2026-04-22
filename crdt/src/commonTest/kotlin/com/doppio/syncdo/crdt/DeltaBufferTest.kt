package com.doppio.syncdo.crdt

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeltaBufferTest {

    @Test
    fun flush_onEmptyBuffer_returnsNull() {
        val buffer = DeltaBuffer<FakeDelta>()
        assertNull(buffer.flush())
    }

    @Test
    fun record_thenFlush_returnsTheDelta() {
        val buffer = DeltaBuffer<FakeDelta>()
        val delta = FakeDelta(values = setOf("a"))

        buffer.record(delta)

        assertEquals(delta, buffer.flush())
    }

    @Test
    fun record_accumulatesIntoASingleMergedDelta() {
        val buffer = DeltaBuffer<FakeDelta>()
        buffer.record(FakeDelta(values = setOf("a")))
        buffer.record(FakeDelta(values = setOf("b")))
        buffer.record(FakeDelta(values = setOf("c")))

        assertEquals(setOf("a", "b", "c"), buffer.flush()?.values)
    }

    @Test
    fun flush_drainsTheBuffer() {
        val buffer = DeltaBuffer<FakeDelta>()
        buffer.record(FakeDelta(values = setOf("a")))

        buffer.flush()

        assertNull(buffer.flush())
    }

    @Test
    fun restore_onEmptyBuffer_storesTheDelta() {
        val buffer = DeltaBuffer<FakeDelta>()

        buffer.restore(FakeDelta(values = setOf("a")))

        assertEquals(setOf("a"), buffer.flush()?.values)
    }

    @Test
    fun restore_mergesWithExistingPendingDelta() {
        val buffer = DeltaBuffer<FakeDelta>()
        buffer.record(FakeDelta(values = setOf("new")))

        buffer.restore(FakeDelta(values = setOf("original")))

        // Merge is commutative (set union) — both values should be present.
        assertEquals(setOf("new", "original"), buffer.flush()?.values)
    }

    @Test
    fun flushThenRestore_reestablishesPendingState() {
        val buffer = DeltaBuffer<FakeDelta>()
        buffer.record(FakeDelta(values = setOf("a")))
        val drained = buffer.flush()!!

        buffer.restore(drained)

        assertEquals(drained, buffer.flush())
    }
}

@Serializable
private data class FakeDelta(
    override val clock: VectorClock = VectorClock(),
    val values: Set<String> = emptySet(),
) : Delta<FakeDelta> {
    override fun merge(other: FakeDelta) = FakeDelta(
        clock = clock.merge(other.clock),
        values = values + other.values,
    )

    override fun isEmpty(): Boolean = values.isEmpty()
}
