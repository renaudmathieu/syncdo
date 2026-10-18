# SyncDO CRDT

Generic [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
primitives for Kotlin Multiplatform. Used as the building blocks of conflict-free,
offline-first collaborative applications.

Targets: Android, iOS (arm64 + simulator arm64), JVM (desktop + server).

## Documentation

- **API reference**: `./gradlew :crdt:dokkaGenerate`, then open
  `crdt/build/dokka/html/index.html` (or `./gradlew dokkaGenerate` for both libraries,
  in `build/dokka/html`). It is also shipped in the published `-javadoc.jar`.
  Start with the module page, then `CrdtState`.
- **Concepts and design**: [`docs/crdt-implementation.md`](../docs/crdt-implementation.md).

The whole library is five small files, readable top to bottom in this order:
`CrdtState.kt` (the contract), `VectorClock.kt`, `LwwRegister.kt`, `OrSet.kt`,
`MapMerge.kt`.

## Installation

```kotlin
// settings.gradle.kts - already done in most projects
dependencyResolutionManagement {
    repositories { mavenCentral() /* or mavenLocal() while snapshotting */ }
}

// build.gradle.kts (KMP module, commonMain)
dependencies {
    implementation("com.doppio.syncdo:crdt:0.1.0-SNAPSHOT")
}
```

## What you get

| Type | Purpose |
|---|---|
| `CrdtState<T>` | Base interface - the `merge` operation must be commutative, associative, idempotent. A delta is a small state of the same type. |
| `VectorClock` | Logical timestamps per node for causal ordering across distributed writes. |
| `LwwRegister<T>` | Last-writer-wins register, tie-broken by node ID. Uses `kotlin.time.Instant`. |
| `OrSet<E>` | Observed-Remove Set. Concurrent add + remove → add wins. `addDelta` / `removeDelta` return the change as a small `OrSet`. |

### When to use which

| You're modelling… | Reach for | Why |
|---|---|---|
| A single mutable field (title, flag, count) | `LwwRegister<T>` | Last write wins, deterministic tie-break by node ID. |
| Set membership (tags, attendees, item IDs) | `OrSet<E>` | Concurrent add + remove → add survives, matching user intuition. |
| Causal ordering of a stream of events | `VectorClock` | Per-node monotonic counters; cheap to merge (pointwise max). |
| A composite document with several of the above | Compose them in a `@Serializable data class` and implement `merge` field-wise. | Composition of join-semilattices is itself a join-semilattice, so convergence is free. See `:shared/.../TodoListCrdt` for a worked example. |

## Quickstart - build your own CRDT

A grow-only counter (G-Counter): each node only increments its own entry, and
merge takes the max per node. A delta is just a small `GCounter` carrying the new
*total* of one node (not "+1"), so receiving it twice is harmless (idempotence).

```kotlin
import com.doppio.syncdo.crdt.*
import kotlinx.serialization.Serializable

@Serializable
data class GCounter(val counts: Map<NodeId, Long> = emptyMap()) : CrdtState<GCounter> {
    val value: Long get() = counts.values.sum()

    fun incrementDelta(nodeId: NodeId) = GCounter(mapOf(nodeId to (counts[nodeId] ?: 0L) + 1))

    override fun merge(other: GCounter) = GCounter(
        (counts.keys + other.counts.keys).associateWith { maxOf(counts[it] ?: 0L, other.counts[it] ?: 0L) }
    )
}

var counter = GCounter()
val delta = counter.incrementDelta("nodeA")
counter = counter.merge(delta) // apply locally, then ship `delta` to the other replicas
```

Pair this with [`com.doppio.syncdo:sync`](../sync/README.md) to get an offline-first
sync engine and Ktor server endpoint: add a `clock` and implement its `ClockedState`.

## Design notes

- All types are `@Serializable` (kotlinx.serialization). JSON is the default wire
  format; binary formats work too.
- `LwwRegister` uses `kotlin.time.Instant` (stdlib, Kotlin 2.1+) - no extra
  datetime dependency required.
- `OrSet` is the most subtle type. Each add attaches a fresh unique tag; a remove
  tombstones only the tags observed at remove time. This is what gives concurrent
  add-vs-remove the "add wins" semantics. Tombstones are never garbage collected.
- Deltas are states: `OrSet.addDelta` / `removeDelta` return a small `OrSet` holding
  only the change. Apply it locally with `merge` and ship the same value to peers.
