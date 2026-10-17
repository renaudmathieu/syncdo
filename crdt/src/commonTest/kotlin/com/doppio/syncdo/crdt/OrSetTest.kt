package com.doppio.syncdo.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrSetTest {

    @Test
    fun addAndContains() {
        val set = OrSet<String>().add("task1", UniqueTag("nodeA", 1))
        assertTrue(set.contains("task1"))
        assertFalse(set.contains("task2"))
    }

    @Test
    fun removeElement() {
        val set = OrSet<String>()
            .add("task1", UniqueTag("nodeA", 1))
            .remove("task1")
        assertFalse(set.contains("task1"))
        assertEquals(emptySet(), set.elements())
    }

    @Test
    fun reAddAfterRemove() {
        val set = OrSet<String>()
            .add("task1", UniqueTag("nodeA", 1))
            .remove("task1")
            .add("task1", UniqueTag("nodeA", 2)) // new unique tag
        assertTrue(set.contains("task1"))
    }

    @Test
    fun mergeUnionsBothSides() {
        val a = OrSet<String>().add("task1", UniqueTag("nodeA", 1))
        val b = OrSet<String>().add("task2", UniqueTag("nodeB", 1))
        val merged = a.merge(b)
        assertTrue(merged.contains("task1"))
        assertTrue(merged.contains("task2"))
    }

    @Test
    fun mergeIsCommutative() {
        val a = OrSet<String>().add("task1", UniqueTag("nodeA", 1)).add("task2", UniqueTag("nodeA", 2))
        val b = OrSet<String>().add("task2", UniqueTag("nodeB", 1)).add("task3", UniqueTag("nodeB", 2))
        assertEquals(a.merge(b).elements(), b.merge(a).elements())
    }

    @Test
    fun mergeIsIdempotent() {
        val a = OrSet<String>().add("task1", UniqueTag("nodeA", 1))
        assertEquals(a.merge(a).elements(), a.elements())
    }

    @Test
    fun concurrentAddAndRemove_AddWins() {
        // Node A adds task1
        val base = OrSet<String>().add("task1", UniqueTag("nodeA", 1))

        // Node A removes task1
        val replicaA = base.remove("task1")

        // Concurrently, Node B re-adds task1 (with a new tag!)
        val replicaB = base.add("task1", UniqueTag("nodeB", 1))

        // Merge: add-wins, the new tag from nodeB survives
        val merged = replicaA.merge(replicaB)
        assertTrue(merged.contains("task1"), "Add-wins: concurrent add should survive remove")
    }

    @Test
    fun concurrentRemovesConverge() {
        val base = OrSet<String>()
            .add("task1", UniqueTag("nodeA", 1))
            .add("task2", UniqueTag("nodeA", 2))

        // Both replicas remove task1
        val replicaA = base.remove("task1")
        val replicaB = base.remove("task1")

        val merged = replicaA.merge(replicaB)
        assertFalse(merged.contains("task1"))
        assertTrue(merged.contains("task2"))
    }

    @Test
    fun elements_returnsOnlyActive() {
        val set = OrSet<String>()
            .add("a", UniqueTag("n1", 1))
            .add("b", UniqueTag("n1", 2))
            .add("c", UniqueTag("n1", 3))
            .remove("b")
        assertEquals(setOf("a", "c"), set.elements())
    }

    @Test
    fun mergeIsAssociative() {
        val base = OrSet<String>().add("x", UniqueTag("nodeA", 1))
        val a = base.remove("x")
        val b = base.add("x", UniqueTag("nodeB", 1))
        val c = OrSet<String>().add("y", UniqueTag("nodeC", 1))
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)))
    }

    @Test
    fun addDeltaHoldsOnlyTheNewTag() {
        val set = OrSet<String>().add("x", UniqueTag("nodeA", 1))
        val delta = set.addDelta("y", UniqueTag("nodeA", 2))
        assertEquals(OrSet(entries = mapOf("y" to setOf(UniqueTag("nodeA", 2)))), delta)
    }

    @Test
    fun removeDeltaTombstonesObservedTags() {
        val set = OrSet<String>().add("x", UniqueTag("nodeA", 1))
        val delta = set.removeDelta("x")
        assertEquals(OrSet(tombstones = mapOf("x" to setOf(UniqueTag("nodeA", 1)))), delta)
    }

    @Test
    fun removeDeltaOfMissingElementIsEmpty() {
        assertTrue(OrSet<String>().removeDelta("x").isEmpty())
    }

    @Test
    fun shippingDeltasConvergesWithShippingFullState() {
        val replicaA = OrSet<String>().add("x", UniqueTag("nodeA", 1))
        val delta = replicaA.removeDelta("x")
        val updatedA = replicaA.merge(delta)

        // Replica B already knows "x": receiving only the delta is enough.
        val replicaB = OrSet<String>().add("x", UniqueTag("nodeA", 1))
        assertEquals(updatedA, replicaB.merge(delta))
        assertEquals(updatedA, replicaB.merge(updatedA))
    }
}
