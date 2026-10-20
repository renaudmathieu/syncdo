package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.CrdtState
import com.doppio.syncdo.crdt.VectorClock

/**
 * What the sync layer needs from an application state: a [CrdtState] that also carries
 * the [VectorClock] of the events it contains. [merge] must merge the clocks too.
 *
 * The same type is used for full states and for deltas (a delta is just a small state),
 * and [clock] tells which deltas a peer has not seen yet.
 */
public interface ClockedState<S : ClockedState<S>> : CrdtState<S> {
    /**
     * The events this state contains: the clock of the replica that produced it. For a delta,
     * the clock of its author right after the change.
     */
    public val clock: VectorClock
}
