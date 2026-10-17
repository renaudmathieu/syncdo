# SyncDO CRDT

Generic [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
primitives for Kotlin Multiplatform. Used as the building blocks of conflict-free,
offline-first collaborative applications.

Targets: Android, iOS (arm64 + simulator arm64), JVM (desktop + server).

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
| `CrdtState<T>` | Base interface - the `merge` operation must be commutative, associative, idempotent. |
| `VectorClock` | Logical timestamps per node for causal ordering across distributed writes. |
| `LwwRegister<T>` | Last-writer-wins register, tie-broken by node ID. Uses `kotlin.time.Instant`. |
| `OrSet<E>` | Observed-Remove Set. Concurrent add + remove → add wins. `addDelta` / `removeDelta` return the change as a small `OrSet`. |
| `Delta<D>` | Partial change that can be merged + shipped over the wire. |
| `DeltaState<S, D>` | CRDT state that supports delta-based sync via `applyDelta(delta)`. |
| `DeltaBuffer<D>` | Accumulates local mutations until they can be pushed (thread-safe via an internal mutex; methods are `suspend`). |

### When to use which

| You're modelling… | Reach for | Why |
|---|---|---|
| A single mutable field (title, flag, count) | `LwwRegister<T>` | Last write wins, deterministic tie-break by node ID. |
| Set membership (tags, attendees, item IDs) | `OrSet<E>` | Concurrent add + remove → add survives, matching user intuition. |
| Causal ordering of a stream of events | `VectorClock` | Per-node monotonic counters; cheap to merge (pointwise max). |
| A composite document with several of the above | Compose them in a `@Serializable data class` and implement `merge` field-wise. | Composition of join-semilattices is itself a join-semilattice, so convergence is free. See `:shared/.../TodoListCrdt` for a worked example. |

## Quickstart - build your own CRDT aggregate

A grow-only counter (G-Counter): each node only increments its own entry, and
merge takes the max per node. The delta carries per-node *totals*, not increments,
so applying the same delta twice is harmless (idempotence).

```kotlin
import com.doppio.syncdo.crdt.*
import kotlinx.serialization.Serializable

@Serializable
data class CounterDelta(
    override val clock: VectorClock,
    val counts: Map<NodeId, Long>,
) : Delta<CounterDelta> {
    override fun merge(other: CounterDelta) =
        CounterDelta(clock.merge(other.clock), maxPerNode(counts, other.counts))

    override fun isEmpty(): Boolean = counts.isEmpty()
}

@Serializable
data class Counter(
    override val clock: VectorClock = VectorClock(),
    val counts: Map<NodeId, Long> = emptyMap(),
) : DeltaState<Counter, CounterDelta> {
    val value: Long get() = counts.values.sum()

    fun incrementDelta(nodeId: NodeId) =
        CounterDelta(clock.increment(nodeId), mapOf(nodeId to (counts[nodeId] ?: 0L) + 1))

    override fun merge(other: Counter) =
        Counter(clock.merge(other.clock), maxPerNode(counts, other.counts))

    override fun applyDelta(delta: CounterDelta) =
        Counter(clock.merge(delta.clock), maxPerNode(counts, delta.counts))
}

private fun maxPerNode(a: Map<NodeId, Long>, b: Map<NodeId, Long>): Map<NodeId, Long> =
    (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }
```

Pair this with [`com.doppio.syncdo:sync`](../sync/README.md) to get an offline-first
sync engine and Ktor server endpoint for free.

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
