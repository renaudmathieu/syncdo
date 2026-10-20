# Module syncdo-sync

Offline-first synchronization of `syncdo-crdt` states over a WebSocket, for Kotlin
Multiplatform. The client engine runs on Android, iOS and JVM; the server helpers
(Ktor) are JVM only.

## Start here

1. Make your CRDT implement [ClockedState][com.doppio.syncdo.sync.ClockedState]: a
   `CrdtState` that also carries a `VectorClock`. The same type is used for full states
   and for deltas.
2. On each client, buffer local deltas in a [DeltaBuffer][com.doppio.syncdo.sync.DeltaBuffer]
   and run a [SyncEngine][com.doppio.syncdo.sync.SyncEngine].
3. On the server, create a [SyncServer][com.doppio.syncdo.sync.server.SyncServer] and
   install [syncEndpoint][com.doppio.syncdo.sync.server.syncEndpoint] in your Ktor routing.

## Protocol

Three [SyncMessage][com.doppio.syncdo.sync.SyncMessage]s travel over one WebSocket:

- `PullRequest(clock, nodeId)`, client to server, on connect: "here is what I have seen";
- `PushDelta(delta, nodeId)`, client to server, after each local change;
- `PullResponse(delta)`, server to client: what the client is missing (recent deltas
  merged into one, or the full state when the server's log is too short), and every
  peer's pushed delta.

Because `merge` is idempotent and commutative, receiving a delta twice or out of order
is harmless: the protocol never needs acknowledgements.

# Package com.doppio.syncdo.sync

Protocol and client engine (common code): [ClockedState][com.doppio.syncdo.sync.ClockedState],
[SyncEngine][com.doppio.syncdo.sync.SyncEngine], [DeltaBuffer][com.doppio.syncdo.sync.DeltaBuffer],
[SyncMessage][com.doppio.syncdo.sync.SyncMessage], [SyncStatus][com.doppio.syncdo.sync.SyncStatus]
and [SyncLogger][com.doppio.syncdo.sync.SyncLogger].

# Package com.doppio.syncdo.sync.server

Ktor server helpers (JVM only): [SyncServer][com.doppio.syncdo.sync.server.SyncServer]
holds the authoritative state, [syncEndpoint][com.doppio.syncdo.sync.server.syncEndpoint]
installs the WebSocket route.
