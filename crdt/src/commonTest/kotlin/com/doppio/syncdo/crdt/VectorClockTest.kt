package com.doppio.syncdo.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorClockTest {

    @Test
    fun emptyClockReturnsZero() {
        val clock = VectorClock()
        assertEquals(0L, clock["node1"])
    }

    @Test
    fun incrementCreatesEntry() {
        val clock = VectorClock().increment("A")
        assertEquals(1L, clock["A"])
        assertEquals(0L, clock["B"])
    }

    @Test
    fun incrementIsMonotone() {
        val clock = VectorClock().increment("A").increment("A").increment("A")
        assertEquals(3L, clock["A"])
    }

    @Test
    fun mergeIsPointwiseMax() {
        val a = VectorClock(mapOf("A" to 3L, "B" to 1L))
        val b = VectorClock(mapOf("A" to 1L, "B" to 5L, "C" to 2L))
        val merged = a.merge(b)
        assertEquals(3L, merged["A"])
        assertEquals(5L, merged["B"])
        assertEquals(2L, merged["C"])
    }

    @Test
    fun mergeIsCommutative() {
        val a = VectorClock(mapOf("A" to 3L, "B" to 1L))
        val b = VectorClock(mapOf("A" to 1L, "B" to 5L))
        assertEquals(a.merge(b), b.merge(a))
    }

    @Test
    fun mergeIsIdempotent() {
        val a = VectorClock(mapOf("A" to 3L, "B" to 1L))
        assertEquals(a, a.merge(a))
    }

    @Test
    fun mergeIsAssociative() {
        val a = VectorClock(mapOf("A" to 3L))
        val b = VectorClock(mapOf("B" to 2L))
        val c = VectorClock(mapOf("A" to 1L, "C" to 4L))
        assertEquals((a.merge(b)).merge(c), a.merge(b.merge(c)))
    }

    @Test
    fun dominatesWhenStrictlyGreater() {
        val a = VectorClock(mapOf("A" to 3L, "B" to 2L))
        val b = VectorClock(mapOf("A" to 1L, "B" to 1L))
        assertTrue(a.dominates(b))
        assertFalse(b.dominates(a))
    }

    @Test
    fun doesNotDominateWhenEqual() {
        val a = VectorClock(mapOf("A" to 3L))
        assertFalse(a.dominates(a))
    }

    @Test
    fun doesNotDominateWhenConcurrent() {
        val a = VectorClock(mapOf("A" to 3L, "B" to 1L))
        val b = VectorClock(mapOf("A" to 1L, "B" to 5L))
        assertFalse(a.dominates(b))
        assertFalse(b.dominates(a))
    }

    @Test
    fun isLessThanOrEqual() {
        val a = VectorClock(mapOf("A" to 1L, "B" to 1L))
        val b = VectorClock(mapOf("A" to 3L, "B" to 2L))
        assertTrue(a.isLessThanOrEqual(b))
        assertFalse(b.isLessThanOrEqual(a))
        assertTrue(a.isLessThanOrEqual(a))
    }
}
