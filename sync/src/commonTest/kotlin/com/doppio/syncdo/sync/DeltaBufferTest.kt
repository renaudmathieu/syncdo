package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.CrdtState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class SetDelta(val items: Set<String>) : CrdtState<SetDelta> {
    override fun merge(other: SetDelta) = SetDelta(items + other.items)
}

class DeltaBufferTest {

    @Test
    fun flushOnEmptyBufferReturnsNull() = runTest {
        assertNull(DeltaBuffer<SetDelta>().flush())
    }

    @Test
    fun recordedDeltasAreMergedAndFlushedOnce() = runTest {
        val buffer = DeltaBuffer<SetDelta>()
        buffer.record(SetDelta(setOf("a")))
        buffer.record(SetDelta(setOf("b")))

        assertEquals(SetDelta(setOf("a", "b")), buffer.flush())
        assertNull(buffer.flush())
    }

    @Test
    fun failedPushIsRecordedBackWithNewerChanges() = runTest {
        val buffer = DeltaBuffer<SetDelta>()
        buffer.record(SetDelta(setOf("a")))
        val inFlight = buffer.flush()!!
        buffer.record(SetDelta(setOf("b")))

        buffer.record(inFlight) // push failed

        assertEquals(SetDelta(setOf("a", "b")), buffer.flush())
    }
}
