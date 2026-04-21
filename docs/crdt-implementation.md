# CRDT Implementation

## Overview

SyncDO uses **Conflict-free Replicated Data Types (CRDTs)** to enable real-time collaborative editing of a shared todo list without a central authority for conflict resolution. Every device holds a full local copy of the state. Concurrent edits on different devices always converge to the same result, with no coordination required.

The implementation lives in the `shared` module under `com.doppio.syncdo.crdt`.

## Theoretical Foundation

All CRDT types implement the `CrdtState<T>` interface, which requires a single `merge()` operation:

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

These properties guarantee that any two replicas receiving the same set of updates will converge to the same state, regardless of the order in which updates are applied.

## Building Blocks

The complex `TodoListCrdt` is built by **composing simpler CRDTs** — each of which is itself a join-semilattice. Composition of semilattices yields a semilattice, so correctness composes as well.

### VectorClock

> `shared/.../crdt/VectorClock.kt`

A logical clock tracking the number of events seen from each node. Used for causal ordering — determining which updates a node has already seen.

```
{ "node-A": 5, "node-B": 3 }
```

- **`increment(nodeId)`** — bumps the counter for a node (called on every local mutation).
- **`merge(other)`** — pointwise max across all entries.
- **`dominates(other)`** — true if this clock is strictly ahead of the other on at least one entry and not behind on any.
- **`isLessThanOrEqual(other)`** — true if the other clock has seen everything this clock has seen. Used by the server to decide which deltas a client has missed.

### LwwRegister\<T\>

> `shared/.../crdt/LwwRegister.kt`

A **Last-Writer-Wins Register** — holds a single value, resolved by wall-clock timestamp. When two writes happen at the exact same instant, the higher `nodeId` (lexicographic) wins, ensuring deterministic convergence.

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

Used for every mutable field of a todo item (title, note, completed, position).

### OrSet\<E\> (Observed-Remove Set)

> `shared/.../crdt/OrSet.kt`

An **add-wins** set. Each `add()` attaches a globally unique tag `(nodeId, counter)`. A `remove()` only tombstones the tags observed at removal time. If another node concurrently adds the same element (with a different tag), that tag survives the remove — hence **add wins**.

```kotlin
data class OrSet<E>(
    val entries: Map<E, Set<UniqueTag>>,       // active elements → their tags
    val tombstones: Map<E, Set<UniqueTag>>     // removed tags
)
```

Merge logic:

1. Union all tombstones from both replicas.
2. Union all entry tags, then subtract tombstoned tags.
3. Elements with at least one surviving tag remain in the set.

Used to track **which todo items exist** in the list.

## Composite CRDTs

### TodoItemCrdt

> `shared/.../crdt/TodoItemCrdt.kt`

Represents a single todo item. Each mutable field is an independent `LwwRegister`, so concurrent edits to different fields never conflict:

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

> `shared/.../crdt/TodoListCrdt.kt`

The **top-level aggregate** — the single source of truth for the entire todo list:

```kotlin
data class TodoListCrdt(
    val itemIds: OrSet<String>,               // set membership (which items exist)
    val items: Map<String, TodoItemCrdt>,     // item data by ID
    val clock: VectorClock                     // causal clock
)
```

Merge:

1. Merge the `OrSet` (determines which items are alive after considering adds and removes).
2. For each surviving item ID, merge the `TodoItemCrdt` data from both replicas.
3. Merge the vector clocks.

This means:
- If device A adds an item while device B deletes it concurrently → the item **survives** (add-wins).
- If device A edits the title while device B edits the note → **both edits** are preserved (independent LWW registers).
- If device A and B both edit the title → the **later timestamp** wins (LWW).

## Delta Sync

Instead of shipping the full CRDT state on every change, SyncDO uses **delta-based synchronization**.

### TodoListDelta

> `shared/.../crdt/TodoListDelta.kt`

A partial diff containing only what changed:

```kotlin
data class TodoListDelta(
    val items: Map<String, TodoItemCrdt>,  // added or updated items
    val membership: OrSetDelta<String>,    // add/remove tags for item IDs
    val clock: VectorClock,
)
```

`OrSetDelta<E>` lives in `:crdt` — it's the generic patch shape produced by
`OrSet.addWithDelta` / `removeWithDelta`, composable via `merge`. `TodoListDelta`
merges field-by-field using the `:crdt` operators: `Map.mergeStates` for the
items map, `OrSetDelta.merge` for membership, `VectorClock.merge` for the clock.

Deltas are themselves mergeable — two deltas can be combined into one, which is important for batching.

### DeltaBuffer

> `shared/.../crdt/DeltaBuffer.kt`

Accumulates local mutations into a pending delta until the sync engine successfully pushes it to the server. Supports three operations:

- `recordAdd(id, item, tag, clock)` — new item added
- `recordRemove(id, tombstonedTags, clock)` — item removed
- `recordUpdate(id, item, clock)` — item field changed
- `flush()` — returns the accumulated delta and resets the buffer

## Sync Protocol

> `shared/.../sync/`

Communication happens over WebSocket using JSON-serialized `SyncMessage` variants:

| Message | Direction | Purpose |
|---|---|---|
| `PullRequest(clock, nodeId)` | Client → Server | "Send me everything I've missed since this clock" |
| `PullResponse(delta)` | Server → Client | Delta of changes the client hasn't seen |
| `PushDelta(delta, nodeId)` | Client → Server | Local changes to merge into server state |
| `FullSync(state)` | Server → Client | Complete state (fallback when client is too far behind) |

### Connection Lifecycle (SyncEngine)

1. Client connects to `ws://{host}:8080/sync`.
2. Sends a `PullRequest` with its local vector clock.
3. Pushes any buffered local deltas via `PushDelta`.
4. Enters a receive loop, applying incoming `PullResponse` deltas.
5. On disconnect, uses **exponential backoff** (1s → 2s → 4s → ... → 30s max) to reconnect.

### Server Handling (SyncRoutes)

On receiving a `PushDelta`:

1. Merge the delta into the authoritative `ServerStateStore`.
2. Broadcast the delta to all other connected WebSocket clients as a `PullResponse`.
3. Check if the pushing client missed any changes and send those back.

On receiving a `PullRequest`:

1. Look up deltas the client hasn't seen using the `DeltaLog` (rolling buffer of last 100 deltas).
2. If the client is too far behind (or has an empty clock), fall back to sending the full state as a delta.

## Node Identity

> `shared/.../sync/NodeIdProvider.kt`

Each device gets a persistent UUID stored in `node-id.txt`. This ID is used as the key in vector clocks and as the tie-breaker in LWW registers. It's generated once on first launch and reused across sessions.

## Offline-First Architecture

> `shared/.../repository/OfflineFirstTodoRepository.kt`

The repository is the state machine that ties everything together:

1. **Local-first**: Every mutation immediately updates the local CRDT state and persists to disk.
2. **Buffer changes**: Mutations are recorded in the `DeltaBuffer` for later sync.
3. **Push when possible**: After each mutation, the repository attempts to push the buffered delta via `SyncEngine`.
4. **Merge remote**: When deltas arrive from the server, the repository merges them into local state using `TodoListCrdt.merge()`.
5. **Thread-safe**: All state access is guarded by a `Mutex`.

```
User Action
    │
    ▼
TodoViewModel
    │
    ▼
OfflineFirstTodoRepository
    ├── mutate local CRDT state
    ├── persist to JSON file
    ├── buffer delta
    └── push via SyncEngine ──► WebSocket ──► Server
                                                │
                                    ┌───────────┘
                                    ▼
                              ServerStateStore
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
| Two devices add the same item concurrently | Both items coexist (different UUIDs) |
| One device adds, another removes the same item | Item survives (add-wins via OrSet) |
| Two devices edit different fields of the same item | Both edits preserved (independent LWW registers) |
| Two devices edit the same field of the same item | Later timestamp wins (LWW), nodeId breaks ties |
| Device is offline, makes changes, reconnects | Changes are buffered and pushed on reconnect, merged normally |
| Client reconnects after being offline for a long time | Server sends full state as a delta (DeltaLog overflow fallback) |
