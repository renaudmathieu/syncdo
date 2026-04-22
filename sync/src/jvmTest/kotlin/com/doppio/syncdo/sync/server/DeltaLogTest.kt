package com.doppio.syncdo.sync.server

import com.doppio.syncdo.sync.clockOf
import com.doppio.syncdo.sync.deltaOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeltaLogTest {

    @Test
    fun empty_logReturnsNull() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>()
        assertNull(log.getDeltasSince(clockOf()))
    }

    @Test
    fun returnsMergedMissedDeltas_whenClientClockIsBehind() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>()
        log.append(deltaOf(clockOf("a" to 1), "x"))
        log.append(deltaOf(clockOf("a" to 2), "y"))

        val missed = log.getDeltasSince(clockOf("a" to 0))

        assertEquals(setOf("x", "y"), missed?.entries)
        assertEquals(clockOf("a" to 2), missed?.clock)
    }

    @Test
    fun returnsNull_whenClientHasSeenEverything() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>()
        log.append(deltaOf(clockOf("a" to 1), "x"))
        log.append(deltaOf(clockOf("a" to 2), "y"))

        assertNull(log.getDeltasSince(clockOf("a" to 2)))
    }

    @Test
    fun onlyIncludesDeltasThatAreNotDominated() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>()
        log.append(deltaOf(clockOf("a" to 1), "x"))
        log.append(deltaOf(clockOf("b" to 1), "y"))
        log.append(deltaOf(clockOf("a" to 2), "z"))

        val missed = log.getDeltasSince(clockOf("a" to 1))

        // "x" is at or before the client's clock; "y" and "z" are after.
        assertEquals(setOf("y", "z"), missed?.entries)
    }

    @Test
    fun returnsNull_whenEvictedHistoryIsNotCoveredByClientClock() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>(maxSize = 2)
        log.append(deltaOf(clockOf("a" to 1), "x"))
        log.append(deltaOf(clockOf("a" to 2), "y"))
        log.append(deltaOf(clockOf("a" to 3), "z"))

        // Client with clock { a: 0 } is behind the eviction boundary — caller should send
        // full state rather than a partial catch-up that silently skips "x".
        assertNull(log.getDeltasSince(clockOf("a" to 0)))
    }

    @Test
    fun stillReturnsMissed_whenClientClockCoversEvictedHistory() {
        val log = DeltaLog<com.doppio.syncdo.sync.TestDelta>(maxSize = 2)
        log.append(deltaOf(clockOf("a" to 1), "x"))
        log.append(deltaOf(clockOf("a" to 2), "y"))
        log.append(deltaOf(clockOf("a" to 3), "z"))

        // Client has already seen "x" (clock a:1), so eviction of "x" is harmless.
        val missed = log.getDeltasSince(clockOf("a" to 1))

        assertEquals(setOf("y", "z"), missed?.entries)
    }
}
