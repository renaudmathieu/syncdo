# SyncDO

Kotlin Multiplatform CRDT primitives and a sync engine for building offline-first,
real-time collaborative apps that share the same code on Android, iOS, and JVM.

## Why use it

Collaborative software has to answer one hard question: **what happens when two
devices edit the same data at the same time, possibly while offline?** The usual
answers - last write from the server wins, throw a merge conflict at the user,
or coordinate through a lock - break the offline experience, lose writes, or
force every peer to be online.

SyncDO gives you [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
building blocks whose `merge` is commutative, associative, and idempotent, so
every replica converges to the same state no matter in what order updates
arrive. That means:

- **Offline-first by default.** Mutate freely while disconnected; pending changes
  are buffered and pushed when the connection comes back. No lost writes, no
  "resolve conflicts" dialog.
- **Real-time sync without a source of truth.** Peers exchange small deltas over
  WebSocket and converge automatically. The server is a fan-out and catch-up
  helper, not a merge arbiter.
- **Predictable semantics you can reason about.** LWW registers use timestamps
  tie-broken by node id so concurrent writes with identical clocks still
  converge everywhere. OR-Sets give add-wins behaviour: a concurrent edit + delete
  keeps the edit.
- **Delta-based - not full-state.** Every mutation produces a small patch you
  ship over the wire. First connects can catch up incrementally from a bounded
  server log; clients beyond the window fall back to a single full-state delta.
- **One Kotlin codebase.** Android, iOS (arm64 + simulator), and JVM are all
  first-class targets. No duplicated merge logic between platforms.

Use it when you need: collaborative todo/notes/kanban, shared settings across
devices, multi-user presence or cursors, any append-or-edit document that must
survive flaky networks without user-visible conflicts.

## What the libraries give you

Two libraries, published independently - pick one or both.

**`com.doppio.syncdo:crdt`** - composable CRDT primitives:

| Type                            | What it's for                                                              |
|---------------------------------|----------------------------------------------------------------------------|
| `VectorClock`                   | Per-node logical timestamps for causal ordering.                           |
| `LwwRegister<T>`                | Single-valued field where the latest write wins (titles, flags, settings). |
| `OrSet<E>`                      | Observed-Remove Set with add-wins semantics for concurrent add/remove.     |
| `Delta<D>` / `DeltaState<S, D>` | Interfaces to define your own aggregate and its incremental patches.       |
| `DeltaBuffer<D>`                | Accumulates local mutations between pushes.                                |
| `Map<K, V>.mergeStates(...)`    | Helper for "a map of CRDTs is a CRDT" aggregates.                          |

All types are `@Serializable` (kotlinx.serialization), so your on-the-wire
format is whatever serializer you plug in - JSON by default, binary if you need
it.

**`com.doppio.syncdo:sync`** - transport for any `Delta<D>`:

| Type                            | What it's for                                                                                  |
|---------------------------------|------------------------------------------------------------------------------------------------|
| `SyncEngine<D>`                 | Client WebSocket engine: connect, backoff reconnect, push pending deltas, apply remote deltas. |
| `SyncStatus`                    | Observable `StateFlow` - `Synced` / `Syncing` / `PendingChanges` / `Offline` / `Error`.        |
| `SyncMessage<D>`                | Sealed protocol: `PushDelta`, `PullRequest`, `PullResponse`.                                   |
| `SyncServer<S, D>` (JVM)        | Authoritative state + bounded delta log + mutex.                                               |
| `Route.syncEndpoint(...)` (JVM) | Drop-in Ktor WebSocket handler.                                                                |

## Installation

```kotlin
// build.gradle.kts - KMP commonMain
dependencies {
    implementation("com.doppio.syncdo:crdt:0.1.0-SNAPSHOT")
    implementation("com.doppio.syncdo:sync:0.1.0-SNAPSHOT")
}
```

Artifacts are published to Maven Local while the project is pre-release. Add
`mavenLocal()` to your repositories until the first stable release hits Central.

## Using it - a minimal end-to-end example

Model a counter that any number of peers can increment offline. Every replica
converges to the sum of every observed increment.

**1. Define your CRDT aggregate and its delta** (shared between client and server):

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
  override fun isEmpty() = amount == 0L
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

**2. Buffer local mutations, ship them, apply remote ones** (client):

```kotlin
val buffer = DeltaBuffer<CounterDelta>()
var state = Counter()

fun increment(by: Long) {
  val nextClock = state.clock.increment(nodeId)
  val delta = CounterDelta(nextClock, nodeId, by)
  state = state.applyDelta(delta)
  buffer.record(delta)
  engine.pushPendingDelta()          // fire and forget; engine handles offline
}

val engine = SyncEngine(
  deltaSerializer = CounterDelta.serializer(),
  serverHost = "sync.example.com",
  serverPort = 8080,
  nodeId = nodeId,
  scope = applicationScope,
  onRemoteDelta = { remote -> state = state.applyDelta(remote) },
  getPendingDelta = { buffer.flush() },
  restorePendingDelta = { buffer.restore(it) },
  getLocalClock = { state.clock },
)
engine.start()

engine.syncStatus.collect { status -> /* update your connection indicator */ }
```

**3. Serve the endpoint** (JVM server):

```kotlin
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import com.doppio.syncdo.sync.server.SyncServer
import com.doppio.syncdo.sync.server.syncEndpoint

embeddedServer(Netty, port = 8080) {
  install(WebSockets)
  val sync = SyncServer(
    initialState = { Counter() },
    fullStateAsDelta = { s -> CounterDelta(s.clock, "server", s.value) },
  )
  routing {
    syncEndpoint(sync, CounterDelta.serializer(), path = "/sync")
  }
}.start(wait = true)
```

That's the whole pipeline: mutate locally, push a delta, the server merges it
into authoritative state and broadcasts it to every other peer, each peer
applies it to local state. Disconnect any client at any time and it keeps
working; reconnect and it catches up with a single pull.

## Learn more

- [`:crdt` reference](crdt/README.md) - every primitive with examples.
- [`:sync` reference](sync/README.md) - protocol details, server options, error handling.
- [`docs/crdt-implementation.md`](docs/crdt-implementation.md) - design deep-dive.
