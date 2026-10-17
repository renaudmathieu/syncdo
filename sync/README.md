# SyncDO Sync

Generic WebSocket-based sync engine + Ktor server routing for Kotlin Multiplatform
CRDTs. Pairs with [`com.doppio.syncdo:crdt`](../crdt/README.md) - bring your own
`ClockedState<S>` (a `CrdtState` that carries a `VectorClock`) and the engine handles connection lifecycle,
incremental catch-up, broadcast fan-out, and offline buffering.

Targets:
- `commonMain` - protocol + client engine (Android, iOS arm64/simulatorArm64, JVM)
- `jvmMain` - generic Ktor `SyncServer` + `Route.syncEndpoint(...)` helpers

## Installation

```kotlin
// build.gradle.kts (KMP module, commonMain)
dependencies {
    implementation("com.doppio.syncdo:crdt:0.1.0-SNAPSHOT")
    implementation("com.doppio.syncdo:sync:0.1.0-SNAPSHOT")
}
```

## What you get

| Type | Where | Purpose |
|---|---|---|
| `ClockedState<S>` | commonMain | What the sync needs: `merge` + `clock`. The same type is used for full states and deltas. |
| `SyncMessage<D>` | commonMain | Sealed protocol: `PushDelta`, `PullRequest`, `PullResponse`. Serialize with `SyncMessage.serializer(deltaSerializer)`. |
| `SyncEngine<D>` | commonMain | Client: WebSocket connection, exponential backoff reconnect, pending-delta buffer, local/remote merge callbacks. |
| `DeltaBuffer<D>` | commonMain | Outbox of local changes: `record` merges into one pending delta, `flush` takes it. Coroutine-safe. |
| `SyncStatus` | commonMain | `Synced` / `Syncing` / `PendingChanges` / `Offline` / `Error` - observable on the engine as a `StateFlow`. |
| `SyncServer<S>` | jvmMain | Server: authoritative state, bounded delta log for incremental catch-up (full state when the log is too short), mutex-guarded merges. |
| `Route.syncEndpoint(...)` | jvmMain | Ktor extension: installs the `/sync` WebSocket handler that routes `SyncMessage` traffic and broadcasts to connected peers. |

## Make your CRDT syncable

```kotlin
@Serializable
data class Counter(
    override val clock: VectorClock = VectorClock(),
    val counts: GCounter = GCounter(), // see the :crdt README
) : ClockedState<Counter> {
    override fun merge(other: Counter) = Counter(clock.merge(other.clock), counts.merge(other.counts))
}
```

Each local change increments `clock` for the local node; the delta you push is a
small `Counter` carrying that new clock.

## Client quickstart

```kotlin
import com.doppio.syncdo.sync.SyncEngine

val engine = SyncEngine(
    deltaSerializer = Counter.serializer(),
    serverHost = "sync.example.com",
    serverPort = 8080,
    nodeId = myNodeId,
    scope = applicationScope,
    onRemoteDelta = { delta -> repository.applyRemote(delta) },
    getPendingDelta = { repository.drainPendingDelta() },
    restorePendingDelta = { delta -> repository.bufferPendingDelta(delta) },
    getLocalClock = { repository.currentClock() },
)
engine.start()

// Observe connection state for UI
engine.syncStatus.collect { status -> /* update indicator */ }

// After each local mutation, tell the engine to flush
repository.onMutation { engine.pushPendingDelta() }
```

## Server quickstart

```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import com.doppio.syncdo.sync.server.SyncServer
import com.doppio.syncdo.sync.server.syncEndpoint

embeddedServer(Netty, port = 8080) {
    install(WebSockets)

    val syncServer = SyncServer(initialState = Counter())

    routing {
        syncEndpoint(syncServer, Counter.serializer(), path = "/sync")
    }
}.start(wait = true)
```

## Protocol

```
   Client A                 Server                 Client B
      │                        │                        │
      │── PullRequest(clk)────▶│                        │
      │◀── PullResponse(Δ) ────│ (missed deltas or full state)
      │                        │                        │
      │── PushDelta(Δ_A) ─────▶│                        │
      │                        │── PullResponse(Δ_A) ──▶│
      │◀── PullResponse(Δ') ───│ (anything A also missed)│
      │                        │                        │
      │                        │◀──── PushDelta(Δ_B) ───│
      │◀── PullResponse(Δ_B) ──│                        │
```

On connect, the client sends `PullRequest(clock, nodeId)`. If the client is missing
something, the server replies with a `PullResponse(delta)` containing every delta it
has missed merged into one, or the full state if the bounded log no longer reaches
back to the client's clock (a full state is a valid delta).

After each local mutation the client sends `PushDelta(delta, nodeId)`. The server
merges it into authoritative state, broadcasts it as `PullResponse` to every other
connected peer, and replies to the pusher with anything they themselves might have
missed since their clock.

`PullResponse` is always client-bound; `PullRequest` and `PushDelta` are always
server-bound. The engine ignores misdirected variants.

## Design notes

- **No hardcoded domain types.** Everything is parameterized on `ClockedState<S>` -
  bring your own CRDT aggregate.
- **Generic serialization.** `SyncMessage<D>` uses kotlinx.serialization's
  type-parameter serializer: callers pass `MyState.serializer()` into
  `SyncMessage.serializer(...)` and the engine does the rest.
- **Server helpers are JVM-only.** Ktor server deps live under `jvmMain` so
  Android/iOS consumers never resolve them. A JVM client-only consumer can
  simply ignore the `com.doppio.syncdo.sync.server` package.
- **Single WebSocket per client.** Both push and pull use the same connection;
  no HTTP long-polling fallback.
