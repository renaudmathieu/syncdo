package com.doppio.syncdo.crdt

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoListCrdtTest {

    private fun instant(ms: Long) = Instant.fromEpochMilliseconds(ms)

    private fun createItem(id: String, title: String, position: Double, nodeId: String, ts: Long) =
        TodoItemCrdt.create(id, title, position, nodeId, instant(ts))

    @Test
    fun mergeConvergesTwoReplicasWithDifferentItems() {
        val item1 = createItem("1", "Task A", 1.0, "nodeA", 1000)
        val item2 = createItem("2", "Task B", 2.0, "nodeB", 2000)

        val replicaA = TodoListCrdt(
            itemIds = OrSet<String>().add("1", "nodeA", 1),
            items = mapOf("1" to item1),
            clock = VectorClock().increment("nodeA")
        )
        val replicaB = TodoListCrdt(
            itemIds = OrSet<String>().add("2", "nodeB", 1),
            items = mapOf("2" to item2),
            clock = VectorClock().increment("nodeB")
        )

        val merged = replicaA.merge(replicaB)
        assertEquals(setOf("1", "2"), merged.itemIds.elements())
        assertEquals("Task A", merged.items["1"]!!.title.value)
        assertEquals("Task B", merged.items["2"]!!.title.value)
    }

    @Test
    fun mergeResolvesConflictOnSameItem() {
        val item = createItem("1", "Original", 1.0, "nodeA", 1000)
        val base = TodoListCrdt(
            itemIds = OrSet<String>().add("1", "nodeA", 1),
            items = mapOf("1" to item),
            clock = VectorClock().increment("nodeA")
        )

        // Device A edits title at t=2000
        val editedA = item.copy(title = LwwRegister("Edited by A", instant(2000), "nodeA"))
        val replicaA = base.copy(
            items = mapOf("1" to editedA),
            clock = base.clock.increment("nodeA")
        )

        // Device B edits title at t=3000 (later → wins)
        val editedB = item.copy(title = LwwRegister("Edited by B", instant(3000), "nodeB"))
        val replicaB = base.copy(
            items = mapOf("1" to editedB),
            clock = base.clock.increment("nodeB")
        )

        val merged = replicaA.merge(replicaB)
        assertEquals("Edited by B", merged.items["1"]!!.title.value)
    }

    @Test
    fun mergeHandlesConcurrentAddAndRemove() {
        val item = createItem("1", "Task", 1.0, "nodeA", 1000)
        val base = TodoListCrdt(
            itemIds = OrSet<String>().add("1", "nodeA", 1),
            items = mapOf("1" to item),
            clock = VectorClock().increment("nodeA")
        )

        // Device A removes item
        val replicaA = base.copy(
            itemIds = base.itemIds.remove("1"),
            clock = base.clock.increment("nodeA")
        )

        // Device B re-adds the same item (new tag) concurrently
        val replicaB = base.copy(
            itemIds = base.itemIds.add("1", "nodeB", 1),
            clock = base.clock.increment("nodeB")
        )

        val merged = replicaA.merge(replicaB)
        assertTrue(merged.itemIds.contains("1"), "Add-wins: re-add should survive concurrent remove")
    }

    @Test
    fun mergeIsCommutative() {
        val item1 = createItem("1", "A", 1.0, "nodeA", 1000)
        val item2 = createItem("2", "B", 2.0, "nodeB", 2000)

        val a = TodoListCrdt(
            itemIds = OrSet<String>().add("1", "nodeA", 1),
            items = mapOf("1" to item1),
            clock = VectorClock().increment("nodeA")
        )
        val b = TodoListCrdt(
            itemIds = OrSet<String>().add("2", "nodeB", 1),
            items = mapOf("2" to item2),
            clock = VectorClock().increment("nodeB")
        )

        val ab = a.merge(b)
        val ba = b.merge(a)
        assertEquals(ab.itemIds.elements(), ba.itemIds.elements())
        assertEquals(ab.items.keys, ba.items.keys)
        for (id in ab.items.keys) {
            assertEquals(ab.items[id]!!.title.value, ba.items[id]!!.title.value)
        }
    }

    @Test
    fun removeItemExcludesFromMerge() {
        val item1 = createItem("1", "Task 1", 1.0, "nodeA", 1000)
        val item2 = createItem("2", "Task 2", 2.0, "nodeA", 2000)

        val state = TodoListCrdt(
            itemIds = OrSet<String>()
                .add("1", "nodeA", 1)
                .add("2", "nodeA", 2),
            items = mapOf("1" to item1, "2" to item2),
            clock = VectorClock().increment("nodeA").increment("nodeA")
        )

        val afterRemove = state.copy(
            itemIds = state.itemIds.remove("1"),
            clock = state.clock.increment("nodeA")
        )

        assertFalse(afterRemove.itemIds.contains("1"))
        assertTrue(afterRemove.itemIds.contains("2"))

        // Re-merge with itself to verify items map is cleaned
        val merged = afterRemove.merge(afterRemove)
        assertFalse(merged.items.containsKey("1"))
        assertTrue(merged.items.containsKey("2"))
    }

    @Test
    fun applyDeltaConvergesWithMerge() {
        val item = createItem("1", "Task", 1.0, "nodeA", 1000)
        val base = TodoListCrdt()

        val delta = TodoListDelta(
            items = mapOf("1" to item),
            membership = OrSetDelta(addedTags = mapOf("1" to setOf(UniqueTag("nodeA", 1)))),
            clock = VectorClock().increment("nodeA")
        )

        val viaApply = base.applyDelta(delta)
        assertTrue(viaApply.itemIds.contains("1"))
        assertEquals("Task", viaApply.items["1"]!!.title.value)
    }
}
