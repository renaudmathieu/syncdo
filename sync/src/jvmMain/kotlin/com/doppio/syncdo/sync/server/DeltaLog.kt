package com.doppio.syncdo.sync.server

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.VectorClock

/**
 * Bounded ring buffer of deltas the server has observed, keyed by their [VectorClock].
 * Used to answer "what has client X missed since their clock Y?" without replaying full state.
 */
internal class DeltaLog<D : Delta<D>>(private val maxSize: Int = 100) {
    private val entries = ArrayDeque<Pair<VectorClock, D>>()

    fun append(delta: D) {
        entries.addLast(delta.clock to delta)
        while (entries.size > maxSize) {
            entries.removeFirst()
        }
    }

    /**
     * Returns merged deltas the client hasn't seen (based on [clientClock]).
     * Returns null if the log doesn't go back far enough — the caller should send full state.
     */
    fun getDeltasSince(clientClock: VectorClock): D? {
        if (entries.isEmpty()) return null
        val missed = entries
            .filter { (deltaClock, _) -> !deltaClock.isLessThanOrEqual(clientClock) }
            .map { it.second }
        if (missed.isEmpty()) return null
        return missed.reduce { acc, delta -> acc.merge(delta) }
    }
}
