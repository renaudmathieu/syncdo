package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class LwwRegisterTest {

    private fun instant(epochMs: Long) = Instant.fromEpochMilliseconds(epochMs)

    @Test
    fun mergeKeepsNewerTimestamp() {
        val a = LwwRegister("old", instant(1000), "nodeA")
        val b = LwwRegister("new", instant(2000), "nodeB")
        assertEquals("new", a.merge(b).value)
        assertEquals("new", b.merge(a).value)
    }

    @Test
    fun mergeIsCommutative() {
        val a = LwwRegister("valA", instant(1000), "nodeA")
        val b = LwwRegister("valB", instant(2000), "nodeB")
        assertEquals(a.merge(b), b.merge(a))
    }

    @Test
    fun mergeIsIdempotent() {
        val a = LwwRegister("val", instant(1000), "nodeA")
        assertEquals(a, a.merge(a))
    }

    @Test
    fun mergeIsAssociative() {
        val a = LwwRegister("a", instant(1000), "nodeA")
        val b = LwwRegister("b", instant(2000), "nodeB")
        val c = LwwRegister("c", instant(3000), "nodeC")
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)))
    }

    @Test
    fun tieBreakByNodeId() {
        val a = LwwRegister("valA", instant(1000), "nodeA")
        val b = LwwRegister("valB", instant(1000), "nodeB")
        // Same timestamp → higher nodeId wins (nodeB > nodeA)
        val merged = a.merge(b)
        assertEquals("valB", merged.value)
        assertEquals("nodeB", merged.nodeId)
        // Commutative
        assertEquals(a.merge(b), b.merge(a))
    }

    @Test
    fun concurrentWritesDifferentTimestamps() {
        // Simulates two devices writing offline, then merging
        val deviceA = LwwRegister("Buy milk", instant(5000), "device-A")
        val deviceB = LwwRegister("Buy eggs", instant(5500), "device-B")
        val result = deviceA.merge(deviceB)
        assertEquals("Buy eggs", result.value) // Device B wrote later
    }
}
