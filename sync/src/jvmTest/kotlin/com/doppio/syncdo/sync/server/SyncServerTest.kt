package com.doppio.syncdo.sync.server

import com.doppio.syncdo.sync.TestState
import com.doppio.syncdo.sync.clockOf
import com.doppio.syncdo.sync.deltaOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncServerTest {

    @Test
    fun mergeDelta_updatesCanonicalStateAndLogs() = runTest {
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )

        server.mergeDelta(deltaOf(clockOf("a" to 1), "x"))
        server.mergeDelta(deltaOf(clockOf("a" to 2), "y"))

        val state = server.currentState()
        assertEquals(setOf("x", "y"), state.values)
        assertEquals(clockOf("a" to 2), state.clock)
    }

    @Test
    fun getDeltaSince_returnsFullState_whenClientClockIsEmpty() = runTest {
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )
        server.mergeDelta(deltaOf(clockOf("a" to 1), "x"))

        val catchup = server.getDeltaSince(clockOf())

        assertEquals(setOf("x"), catchup.entries)
    }

    @Test
    fun getDeltaSince_returnsOnlyMissedDeltas_whenClientIsPartiallyCaughtUp() = runTest {
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )
        server.mergeDelta(deltaOf(clockOf("a" to 1), "x"))
        server.mergeDelta(deltaOf(clockOf("a" to 2), "y"))

        val catchup = server.getDeltaSince(clockOf("a" to 1))

        assertEquals(setOf("y"), catchup.entries)
    }

    @Test
    fun getDeltaSince_fallsBackToFullState_whenLogNoLongerCoversGap() = runTest {
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
            maxLogSize = 2,
        )
        server.mergeDelta(deltaOf(clockOf("a" to 1), "x"))
        server.mergeDelta(deltaOf(clockOf("a" to 2), "y"))
        server.mergeDelta(deltaOf(clockOf("a" to 3), "z"))

        // Client's clock { a: 0 } points to before "x", but "x" has been evicted from the log.
        val catchup = server.getDeltaSince(clockOf("a" to 0))

        assertEquals(setOf("x", "y", "z"), catchup.entries)
    }

    @Test
    fun getDeltaSince_fallsBackToFullState_whenClientIsFullyCaughtUp() = runTest {
        // Current behavior: a caught-up client receives the full state as a delta
        // instead of an empty one. Merging is idempotent so this is harmless, but it's
        // worth pinning as observed behavior — see CHANGELOG for the known inefficiency.
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )
        server.mergeDelta(deltaOf(clockOf("a" to 1), "x"))

        val catchup = server.getDeltaSince(clockOf("a" to 1))

        assertEquals(setOf("x"), catchup.entries)
        assertFalse(catchup.isEmpty())
    }

    @Test
    fun getDeltaSince_emptyLogOnFreshClient_returnsFullStateEvenIfEmpty() = runTest {
        val server = SyncServer(
            initialState = { TestState() },
            fullStateAsDelta = TestState::toDelta,
        )

        val catchup = server.getDeltaSince(clockOf())

        assertTrue(catchup.isEmpty())
        assertFalse(catchup.entries.any())
    }
}
