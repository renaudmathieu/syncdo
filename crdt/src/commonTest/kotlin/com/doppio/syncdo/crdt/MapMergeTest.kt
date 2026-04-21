package com.doppio.syncdo.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MapMergeTest {

    @Test
    fun mergeValuesCombinesCollidingKeys() {
        val a = mapOf("x" to 1, "y" to 2)
        val b = mapOf("y" to 10, "z" to 100)
        val merged = a.mergeValues(b) { lhs, rhs -> lhs + rhs }
        assertEquals(mapOf("x" to 1, "y" to 12, "z" to 100), merged)
    }

    @Test
    fun mergeValuesShortCircuitsOnEmpty() {
        val nonEmpty = mapOf("x" to 1)
        val empty = emptyMap<String, Int>()
        assertSame(nonEmpty, empty.mergeValues(nonEmpty) { _, _ -> error("should not be called") })
        assertSame(nonEmpty, nonEmpty.mergeValues(empty) { _, _ -> error("should not be called") })
    }

    @Test
    fun mergeStatesDelegatesToCrdtMerge() {
        val a = mapOf("r" to register("A", 1))
        val b = mapOf("r" to register("B", 2))
        val merged = a.mergeStates(b)
        assertEquals("B", merged.getValue("r").value) // LWW by timestamp
    }

    @Test
    fun unionValuesUnionsPerKey() {
        val a = mapOf("k" to setOf(1, 2), "only-a" to setOf(9))
        val b = mapOf("k" to setOf(2, 3), "only-b" to setOf(8))
        val merged = a.unionValues(b)
        assertEquals(setOf(1, 2, 3), merged["k"])
        assertEquals(setOf(9), merged["only-a"])
        assertEquals(setOf(8), merged["only-b"])
    }

    private fun register(value: String, ms: Long) =
        LwwRegister(value, kotlin.time.Instant.fromEpochMilliseconds(ms), "node")
}
