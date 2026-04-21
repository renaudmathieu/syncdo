package com.doppio.syncdo.crdt

/**
 * A CRDT state that supports delta-based synchronization.
 * Extends [CrdtState] with the ability to apply incremental [Delta]s,
 * enabling efficient sync without shipping full state on every change.
 */
interface DeltaState<S : DeltaState<S, D>, D : Delta<D>> : CrdtState<S> {
    val clock: VectorClock

    /**
     * Applies a delta to this state and returns the merged result.
     * Semantically equivalent to converting the delta into a partial state and calling [merge].
     */
    fun applyDelta(delta: D): S
}
