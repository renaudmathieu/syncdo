package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.VectorClock
import com.doppio.syncdo.sync.ClockedState

/**
 * Bounded history of the deltas the server has received, used to answer "what has a
 * client at clock X missed?" without resending the full state.
 *
 * @param baseClock clock of the state the log starts from (e.g. loaded from disk).
 */
internal class DeltaLog<D : ClockedState<D>>(baseClock: VectorClock, private val maxSize: Int) {
    private val deltas = ArrayDeque<D>()

    /** Clock of every event that is no longer in [deltas]: the base state plus evicted deltas. */
    private var evictedClock = baseClock

    fun append(delta: D) {
        deltas.addLast(delta)
        while (deltas.size > maxSize) {
            evictedClock = evictedClock.merge(deltas.removeFirst().clock)
        }
    }

    /** True if the log still holds everything a client at [clientClock] may have missed. */
    fun covers(clientClock: VectorClock): Boolean = evictedClock.isLessThanOrEqual(clientClock)

    /** The deltas a client at [clientClock] has not seen, merged into one. Null if none. */
    fun missedSince(clientClock: VectorClock): D? = deltas
        .filterNot { it.clock.isLessThanOrEqual(clientClock) }
        .reduceOrNull { acc, delta -> acc.merge(delta) }
}
