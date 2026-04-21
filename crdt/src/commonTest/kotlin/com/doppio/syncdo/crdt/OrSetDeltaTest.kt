package com.doppio.syncdo.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrSetDeltaTest {

    @Test
    fun addWithDeltaReturnsStateAndDelta() {
        val (set, delta) = OrSet<String>().addWithDelta("x", "nodeA", 1)
        assertTrue(set.contains("x"))
        assertEquals(mapOf("x" to setOf(UniqueTag("nodeA", 1))), delta.addedTags)
        assertEquals(emptyMap(), delta.removedTags)
    }

    @Test
    fun removeWithDeltaReturnsStateAndDelta() {
        val base = OrSet<String>().add("x", "nodeA", 1)
        val (set, delta) = base.removeWithDelta("x")
        assertFalse(set.contains("x"))
        assertEquals(mapOf("x" to setOf(UniqueTag("nodeA", 1))), delta.removedTags)
    }

    @Test
    fun removeWithDeltaOnMissingElementYieldsEmptyDelta() {
        val (set, delta) = OrSet<String>().removeWithDelta("x")
        assertTrue(delta.isEmpty())
        assertEquals(OrSet<String>(), set)
    }

    @Test
    fun applyDeltaEquivalentToDirectMutation() {
        val direct = OrSet<String>().add("x", "nodeA", 1).add("y", "nodeB", 1)

        val d1 = OrSetDelta(addedTags = mapOf("x" to setOf(UniqueTag("nodeA", 1))))
        val d2 = OrSetDelta(addedTags = mapOf("y" to setOf(UniqueTag("nodeB", 1))))
        val viaDelta = OrSet<String>().applyDelta(d1.merge(d2))

        assertEquals(direct.elements(), viaDelta.elements())
    }

    @Test
    fun mergeAccumulatesAddsAndRemoves() {
        val tagA = UniqueTag("nodeA", 1)
        val tagB = UniqueTag("nodeB", 1)
        val a = OrSetDelta(addedTags = mapOf("x" to setOf(tagA)))
        val b = OrSetDelta(
            addedTags = mapOf("x" to setOf(tagB)),
            removedTags = mapOf("y" to setOf(tagA)),
        )
        val merged = a.merge(b)
        assertEquals(setOf(tagA, tagB), merged.addedTags["x"])
        assertEquals(setOf(tagA), merged.removedTags["y"])
    }

    @Test
    fun mergeIsCommutativeAndIdempotent() {
        val tag = UniqueTag("nodeA", 1)
        val a = OrSetDelta(addedTags = mapOf("x" to setOf(tag)))
        val b = OrSetDelta(removedTags = mapOf("y" to setOf(tag)))
        assertEquals(a.merge(b), b.merge(a))
        assertEquals(a, a.merge(a))
    }

    @Test
    fun toDeltaRoundTripsThroughApplyDelta() {
        val original = OrSet<String>()
            .add("x", "nodeA", 1)
            .add("y", "nodeB", 1)
            .remove("y")
        val rebuilt = OrSet<String>().applyDelta(original.toDelta())
        assertEquals(original.elements(), rebuilt.elements())
        assertEquals(original.entries, rebuilt.entries)
        assertEquals(original.tombstones, rebuilt.tombstones)
    }

    @Test
    fun concurrentAddViaDeltaFollowsAddWins() {
        val base = OrSet<String>().add("x", "nodeA", 1)
        val (removedSet, removedDelta) = base.removeWithDelta("x")
        val (_, readdedDelta) = base.addWithDelta("x", "nodeB", 1)

        val merged = removedSet.applyDelta(readdedDelta)
        assertTrue(merged.contains("x"), "Concurrent re-add must win via delta application")
        // sanity: removedDelta on its own tombstones the original tag
        assertEquals(setOf(UniqueTag("nodeA", 1)), removedDelta.removedTags["x"])
    }
}
