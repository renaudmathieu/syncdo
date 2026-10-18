# Module syncdo-crdt

Generic CRDT primitives for Kotlin Multiplatform (Android, iOS, JVM).

A **CRDT** (Conflict-free Replicated Data Type) is a data type that several replicas
(phones, laptops, a server) can modify independently, even offline, and later combine
without conflicts: every replica that has received the same changes ends up with the
same value. This library provides a handful of small, serializable building blocks and
one rule to combine them.

## Start here

1. [CrdtState][com.doppio.syncdo.crdt.CrdtState] is the only interface. It defines
   `merge` and the three laws it must follow (commutative, associative, idempotent).
2. Pick the building block that matches your data (table below).
3. Compose them in a `data class` whose `merge` merges each field. The result is a CRDT
   too, with no extra proof needed.

## Choosing a type

| You are modelling | Use | On concurrent changes |
|---|---|---|
| A single field (title, flag, position) | [LwwRegister][com.doppio.syncdo.crdt.LwwRegister] | The most recent write wins. |
| A set (tags, members, item ids) | [OrSet][com.doppio.syncdo.crdt.OrSet] | Add wins over a concurrent remove. |
| A map of CRDTs (id to item) | [mergeStates][com.doppio.syncdo.crdt.mergeStates] | Values of the same key are merged. |
| "Who has seen what" | [VectorClock][com.doppio.syncdo.crdt.VectorClock] | Pointwise max of the counters. |

## Deltas are states

To sync efficiently you send *deltas* (small changes) instead of whole states. In this
library a delta has the **same type** as the state: it is a small state that holds only
the change. You apply it locally with `merge` and send the same value to the other
replicas, which `merge` it too. Since `merge` is idempotent and commutative, a delta can
be delivered twice or out of order without harm.

```kotlin
var set = OrSet<String>()
val delta = set.addDelta("milk", UniqueTag("nodeA", 1)) // only the change
set = set.merge(delta)                                  // local update
// send `delta`; each other replica does: theirs = theirs.merge(delta)
```

## Example: a shared todo list

```kotlin
@Serializable
data class TodoItem(
    val title: LwwRegister<String>,
    val done: LwwRegister<Boolean>,
) : CrdtState<TodoItem> {
    override fun merge(other: TodoItem) = TodoItem(
        title = title.merge(other.title),
        done = done.merge(other.done),
    )
}

@Serializable
data class TodoList(
    val ids: OrSet<String> = OrSet(),               // which todos exist
    val items: Map<String, TodoItem> = emptyMap(),  // their content
) : CrdtState<TodoList> {
    override fun merge(other: TodoList) = TodoList(
        ids = ids.merge(other.ids),
        items = items.mergeStates(other.items),
    )

    val visibleItems get() = ids.elements().mapNotNull { items[it] }
}
```

- Alice renames a todo while Bob checks it: both changes are kept (separate registers).
- Alice and Bob rename the same todo: the later rename wins.
- Alice deletes a todo while Bob re-adds it: it stays (add wins).

To synchronize such a state over the network, see the `syncdo-sync` module.

## Further reading

- Shapiro et al., *A comprehensive study of Convergent and Commutative Replicated Data
  Types* (INRIA, 2011).
- Almeida, Shoker, Baquero, *Delta State Replicated Data Types* (2018).

# Package com.doppio.syncdo.crdt

All the CRDT building blocks: the [CrdtState][com.doppio.syncdo.crdt.CrdtState] contract,
[VectorClock][com.doppio.syncdo.crdt.VectorClock],
[LwwRegister][com.doppio.syncdo.crdt.LwwRegister],
[OrSet][com.doppio.syncdo.crdt.OrSet] and the
[mergeStates][com.doppio.syncdo.crdt.mergeStates] helper to compose them.
