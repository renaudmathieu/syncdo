# SyncDO CRDT

Generic [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
primitives for Kotlin Multiplatform. Used as the building blocks of conflict-free,
offline-first collaborative applications.

Targets: Android, iOS (arm64 + simulator arm64), JVM (desktop + server).

## Installation

```kotlin
// settings.gradle.kts — already done in most projects
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
| `CrdtState<T>` | Base interface — the `merge` operation must be commutative, associative, idempotent. |
| `VectorClock` | Logical timestamps per node for causal ordering across distributed writes. |
| `LwwRegister<T>` | Last-writer-wins register, tie-broken by node ID. Uses `kotlin.time.Instant`. |
| `OrSet<E>` | Observed-Remove Set. Concurrent add + remove → add wins. |
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

## Quickstart — build your own CRDT aggregate

```kotlin
import com.doppio.syncdo.crdt.*
import kotlinx.serialization.Serializable

@Serializable
data class CounterDelta(
    override val clock: VectorClock,
    val nodeId: NodeId,
    val amount: Long,
) : Delta<CounterDelta> {
    override fun merge(other: CounterDelta) =
        CounterDelta(clock.merge(other.clock), nodeId, amount + other.amount)

    override fun isEmpty(): Boolean = amount == 0L
}

@Serializable
data class Counter(
    override val clock: VectorClock = VectorClock(),
    val value: Long = 0L,
) : DeltaState<Counter, CounterDelta> {
    override fun merge(other: Counter) =
        Counter(clock.merge(other.clock), maxOf(value, other.value))

    override fun applyDelta(delta: CounterDelta) =
        Counter(clock.merge(delta.clock), value + delta.amount)
}
```

Pair this with [`com.doppio.syncdo:sync`](../sync/README.md) to get an offline-first
sync engine and Ktor server endpoint for free.

## Design notes

- All types are `@Serializable` (kotlinx.serialization). JSON is the default wire
  format; binary formats work too.
- `LwwRegister` uses `kotlin.time.Instant` (stdlib, Kotlin 2.1+) — no extra
  datetime dependency required.
- `OrSet` is the more subtle of the two registers. Add operations attach a fresh
  unique tag per mutation; remove tombstones only the tags observed at remove
  time. This is what gives concurrent add-vs-remove the "add wins" semantics.
