package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.Delta
import com.doppio.syncdo.crdt.DeltaState
import com.doppio.syncdo.crdt.VectorClock
import kotlinx.serialization.Serializable

/**
 * Grow-only set used as a minimal [DeltaState] for tests. Merge is union, so it's
 * commutative, associative, and idempotent — enough to exercise the sync machinery
 * without pulling in the sample Todo CRDT.
 */
@Serializable
internal data class TestState(
    override val clock: VectorClock = VectorClock(),
    val values: Set<String> = emptySet(),
) : DeltaState<TestState, TestDelta> {

    override fun merge(other: TestState): TestState = TestState(
        clock = clock.merge(other.clock),
        values = values + other.values,
    )

    override fun applyDelta(delta: TestDelta): TestState = TestState(
        clock = clock.merge(delta.clock),
        values = values + delta.entries,
    )

    fun toDelta(): TestDelta = TestDelta(clock = clock, entries = values)
}

@Serializable
internal data class TestDelta(
    override val clock: VectorClock = VectorClock(),
    val entries: Set<String> = emptySet(),
) : Delta<TestDelta> {

    override fun merge(other: TestDelta): TestDelta = TestDelta(
        clock = clock.merge(other.clock),
        entries = entries + other.entries,
    )

    override fun isEmpty(): Boolean = entries.isEmpty()
}

internal fun clockOf(vararg entries: Pair<String, Long>): VectorClock =
    VectorClock(entries.toMap())

internal fun deltaOf(
    clock: VectorClock = VectorClock(),
    vararg values: String,
): TestDelta = TestDelta(clock, values.toSet())
