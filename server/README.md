# `:server` (sample)

Ktor sample server demonstrating `:sync`'s server-side helpers. Not published.

Boots a Netty server on port 8080 (see `Constants.kt`) and installs a generic
`SyncServer<TodoListCrdt, TodoListDelta>` via `Route.syncEndpoint(...)` at `/sync`.
All the sync routing, broadcast fan-out, and delta-log bookkeeping are supplied
by the published `:sync` library — this module contains only the concrete
`TodoListCrdt` wiring.

Run with:

```shell
./gradlew :server:run
```

See the library READMEs for reusable APIs: [`:crdt`](../crdt/README.md),
[`:sync`](../sync/README.md).
