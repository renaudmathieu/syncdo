package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.VectorClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncServerTest {

    private fun clock(vararg entries: Pair<String, Long>) = VectorClock(mapOf(*entries))

    @Test
    fun upToDateClientGetsNothing() = runTest {
        val server = SyncServer(CounterState())
        val pushed = CounterState(clock("A" to 1), value = 3)
        server.mergeDelta(pushed)

        assertNull(server.getDeltaSince(pushed.clock))
    }

    @Test
    fun clientBehindGetsOnlyMissedDeltas() = runTest {
        val server = SyncServer(CounterState())
        server.mergeDelta(CounterState(clock("A" to 1), value = 1))
        server.mergeDelta(CounterState(clock("A" to 2), value = 2))

        assertEquals(CounterState(clock("A" to 2), value = 2), server.getDeltaSince(clock("A" to 1)))
    }

    @Test
    fun clientBehindTruncatedLogGetsFullState() = runTest {
        val server = SyncServer(CounterState(), maxLogSize = 1)
        server.mergeDelta(CounterState(clock("A" to 1), value = 1))
        server.mergeDelta(CounterState(clock("A" to 2), value = 2)) // evicts the first delta

        assertEquals(server.currentState(), server.getDeltaSince(VectorClock()))
    }

    @Test
    fun stateLoadedFromDiskIsSentToNewClients() = runTest {
        val loaded = CounterState(clock("A" to 5), value = 42)
        val server = SyncServer(loaded)

        assertEquals(loaded, server.getDeltaSince(VectorClock()))
    }
}
