# CRDT Implementation

## Overview

SyncDO uses **Conflict-free Replicated Data Types (CRDTs)** to let several devices edit a
shared todo list, even offline, without a central authority resolving conflicts. Every
device holds a full local copy of the state. Concurrent edits always converge to the same
result, with no coordination required.

The code is split in three layers:

| Layer | Module | Contents |
|---|---|---|
| Generic CRDTs | `:crdt` (published) | `CrdtState`, `VectorClock`, `LwwRegister`, `OrSet`, `mergeStates` |
| Generic sync | `:sync` (published) | `ClockedState`, `DeltaBuffer`, `SyncEngine`, `SyncServer`, `syncEndpoint` |
| Todo sample | `:shared` | `TodoItemCrdt`, `TodoListCrdt`, `OfflineFirstTodoRepository` |

The API reference of `:crdt` and `:sync` is generated with Dokka:
`./gradlew dokkaGenerate`, then open `build/dokka/html/index.html`.

## Theoretical Foundation

All CRDT types implement the `CrdtState<T>` interface, which requires a single
`merge()` operation:

```kotlin
interface CrdtState<T : CrdtState<T>> {
    fun merge(other: T): T
}
```

The `merge` operation must satisfy three properties (forming a **join-semilattice**):

| Property | Meaning |
|---|---|
| **Commutative** | `A.merge(B) == B.merge(A)` |
| **Associative** | `A.merge(B.merge(C)) == A.merge(B).merge(C)` |
| **Idempotent** | `A.merge(A) == A` |

These properties guarantee that any two replicas receiving the same set of updates
converge to the same state, regardless of the order (or the number of times) in which
updates are delivered.

### Deltas are states

SyncDO is a **delta-state CRDT** system: devices exchange small changes (deltas) instead
of the full state. There is no separate delta type: **a delta is a small state of the
same type** that holds only the change. It is applied locally with `merge` and the same
value is sent to the other replicas, which `merge` it too. Thanks to the three properties
above, a delta can be delivered twice or out of order without harm.

## Building Blocks (`:crdt`)

The `TodoListCrdt` aggregate is built by **composing simpler CRDTs**, each of which is
itself a join-semilattice. Composition of semilattices yields a semilattice, so
correctness composes as well.

### VectorClock

> `crdt/src/commonMain/kotlin/com/doppio/syncdo/crdt/VectorClock.kt`

A logical clock counting the events seen from each node:

```
{ "node-A": 5, "node-B": 3 }
```

- **`increment(nodeId)`** - bumps the counter of a node (called on every local mutation).
- **`merge(other)`** - pointwise max across all entries.
- **`isLessThanOrEqual(other)`** - true if `other` has seen everything this clock has
  seen. If neither `a <= b` nor `b <= a`, the clocks are concurrent. The server uses it
  to decide which deltas a client has missed.

### LwwRegister\<T\>

> `crdt/src/commonMain/kotlin/com/doppio/syncdo/crdt/LwwRegister.kt`

A **Last-Writer-Wins Register**: holds a single value, resolved by wall-clock timestamp.
When two writes happen at the exact same instant, the higher `nodeId` (lexicographic)
wins, ensuring deterministic convergence.

```kotlin
data class LwwRegister<T>(
    val value: T,
    val timestamp: Instant,
    val nodeId: NodeId
)
```

Merge logic:

1. Higher timestamp wins.
2. If timestamps are equal, higher nodeId wins.

Assumption: a node never writes two different values at the same instant.

Used for every mutable field of a todo item (title, note, completed, position).

### OrSet\<E\> (Observed-Remove Set)

> `crdt/src/commonMain/kotlin/com/doppio/syncdo/crdt/OrSet.kt`

An **add-wins** set. Each add attaches a globally unique tag `(nodeId, counter)`. A
remove only tombstones the tags observed at removal time. If another node concurrently
adds the same element (with a new tag), that tag survives the remove: **add wins**.

```kotlin
data class OrSet<E>(
    val entries: Map<E, Set<UniqueTag>>,       // live tags of each element
    val tombstones: Map<E, Set<UniqueTag>>     // removed tags
)
```

Merge logic:

1. Union all tombstones from both replicas.
2. Union all entry tags, then subtract tombstoned tags.
3. Elements with at least one surviving tag remain in the set (invariant: `entries`
   only holds live tags).

Deltas: `addDelta(element, tag)` and `removeDelta(element)` return a small `OrSet`
holding only the change; `add` and `remove` are `merge(addDelta(...))` and
`merge(removeDelta(...))`.

Used to track **which todo items exist** in the list.

### mergeStates

> `crdt/src/commonMain/kotlin/com/doppio/syncdo/crdt/MapMerge.kt`

`Map<K, V>.mergeStates(other)` merges two maps of CRDTs key by key: a map of CRDTs is a
CRDT. Used for the map of todo items.

## Composite CRDTs (`:shared`)

### TodoItemCrdt

> `shared/src/commonMain/kotlin/com/doppio/syncdo/crdt/TodoItemCrdt.kt`

Represents a single todo item. Each mutable field is an independent `LwwRegister`, so
concurrent edits to different fields never conflict:

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Immutable UUID |
| `title` | `LwwRegister<String>` | Task title |
| `note` | `LwwRegister<String>` | Optional note |
| `completed` | `LwwRegister<Boolean>` | Completion status |
| `position` | `LwwRegister<Double>` | Sort order |
| `createdAt` | `Instant` | Immutable creation timestamp |

Merge: field-by-field LWW merge. `createdAt` uses `minOf` (earliest wins).

### TodoListCrdt

> `shared/src/commonMain/kotlin/com/doppio/syncdo/crdt/TodoListCrdt.kt`

The **top-level aggregate**, the single source of truth for the entire todo list. It is
also its own delta type.

```kotlin
data class TodoListCrdt(
    val itemIds: OrSet<String>,               // which items exist
    val items: Map<String, TodoItemCrdt>,     // item data by ID
    override val clock: VectorClock           // causal clock, used by the sync
) : ClockedState<TodoListCrdt>
```

Merge is field by field: `itemIds.merge`, `items.mergeStates`, `clock.merge`. The data of
removed items is kept (like OR-Set tombstones); the UI only shows the items whose id is
in `itemIds.elements()`.

This means:
- If device A adds an item while device B deletes it concurrently, the item **survives**
  (add-wins).
- If device A edits the title while device B edits the note, **both edits** are
  preserved (independent LWW registers).
- If devices A and B both edit the title, the **later timestamp** wins (LWW).

## Sync (`:sync`)

### ClockedState

What the sync layer needs from the application state: a `CrdtState` that also carries a
`VectorClock`. The same type travels as full state and as delta.

### DeltaBuffer

> `sync/src/commonMain/kotlin/com/doppio/syncdo/sync/DeltaBuffer.kt`

The outbox of local changes. It holds at most one pending delta:

- `record(delta)` - merges `delta` into the pending one;
- `flush()` - returns the pending delta and empties the buffer.

When a push fails, the delta is simply recorded again: `merge` is commutative, so the
order does not matter.

### Protocol

Communication happens over one WebSocket using JSON-serialized `SyncMessage` variants:

| Message | Direction | Purpose |
|---|---|---|
| `PullRequest(clock, nodeId)` | Client → Server | "Here is what I have seen, send me what I miss" |
| `PushDelta(delta, nodeId)` | Client → Server | A local change to merge into server state |
| `PullResponse(delta)` | Server → Client | What the client is missing, or a peer's change |

### Connection lifecycle (SyncEngine)

1. The client connects to `ws://{host}:8080/sync`.
2. It sends a `PullRequest` with its local vector clock.
3. It pushes its buffered delta via `PushDelta`.
4. It enters a receive loop, merging every incoming `PullResponse`.
5. On disconnect it reconnects with **exponential backoff** (1s, 2s, 4s, ... 30s max).

### Server handling (SyncServer + syncEndpoint)

On a `PushDelta`:

1. Merge the delta into the authoritative state and append it to the `DeltaLog`.
2. Broadcast the delta to every other connected client as a `PullResponse`.
3. If the pushing client missed something, send it back.

On a `PullRequest`, the server answers with what the client is missing:

- nothing if the client is up to date;
- the missed deltas merged into one, when the `DeltaLog` (last 100 deltas) still covers
  the gap;
- the full state otherwise (a full state is a valid delta). The log tracks the clock of
  the evicted deltas and of the initial state to detect this case.

## Node Identity

> `shared/src/commonMain/kotlin/com/doppio/syncdo/sync/NodeIdProvider.kt`

Each device gets a persistent UUID stored in `node-id.txt`. This ID is used as the key in
vector clocks, in OR-Set tags, and as the tie-breaker in LWW registers. It is generated
once on first launch and reused across sessions.

## Offline-First Architecture

> `shared/src/commonMain/kotlin/com/doppio/syncdo/repository/OfflineFirstTodoRepository.kt`

The repository ties everything together. Each mutation:

1. increments the local vector clock (its new entry also serves as the OR-Set tag
   counter);
2. builds a delta: a small `TodoListCrdt` holding only the change;
3. records it in the `DeltaBuffer` and merges it into the local state;
4. persists the state to disk, then asks the `SyncEngine` to push.

When deltas arrive from the server, the repository merges them with
`TodoListCrdt.merge()`. All state access is guarded by a `Mutex`.

```
User Action
    │
    ▼
TodoViewModel
    │
    ▼
OfflineFirstTodoRepository
    ├── build delta (small TodoListCrdt)
    ├── record delta + merge locally
    ├── persist to JSON file
    └── push via SyncEngine ──► WebSocket ──► Server
                                                │
                                    ┌───────────┘
                                    ▼
                         SyncServer.mergeDelta
                                    │
                              broadcast to
                              other clients
                                    │
                                    ▼
                        SyncEngine (other device)
                                    │
                                    ▼
                    OfflineFirstTodoRepository.mergeRemoteDelta()
                                    │
                              merge CRDT state
                              persist + emit UI update
```

## Conflict Resolution Summary

| Scenario | Resolution |
|---|---|
| Two devices add an item concurrently | Both items coexist (different UUIDs) |
| One device adds, another removes the same item | Item survives (add-wins via OrSet) |
| Two devices edit different fields of the same item | Both edits preserved (independent LWW registers) |
| Two devices edit the same field of the same item | Later timestamp wins (LWW), nodeId breaks ties |
| Device is offline, makes changes, reconnects | Changes are buffered and pushed on reconnect, merged normally |
| Client reconnects after a long time offline | Server sends the full state (DeltaLog no longer covers the gap) |
