# CLAUDE.md

Guidance for Claude Code working in this repo.

## What this repo is

Two publishable KMP libraries (`:crdt`, `:sync`) + a Todo sample app (`:shared`,
`:composeApp`, `:server`) that demonstrates using them together.

**The sample code is not part of the published surface.** When proposing changes,
keep library APIs (anything in `:crdt` and `:sync`) clean, generic, and
domain-agnostic. Todo-specific concepts belong in `:shared`.

## Module map

| Module | Kind | Targets | Notes |
|---|---|---|---|
| `:crdt` | **published library** | android, iosArm64, iosSimulatorArm64, jvm | Generic CRDT primitives. No app-specific types. |
| `:sync` | **published library** | android, iosArm64, iosSimulatorArm64, jvm | `commonMain` = client engine + protocol; `jvmMain` = Ktor server helpers. Depends on `:crdt`. |
| `:shared` | sample glue | android, ios, jvm | Todo CRDT + repository + file persistence. Depends on `:crdt` + `:sync` (both `api`). |
| `:composeApp` | sample UI | android, ios, jvm | Compose Multiplatform. |
| `:server` | sample server | jvm | Ktor + `syncEndpoint` from `:sync`. |

## Build & test

```shell
./gradlew build                                # Everything (slow first time — iOS link)
./gradlew :crdt:allTests :sync:allTests        # Library tests (incl. iOS sim)
./gradlew :shared:jvmTest                      # Todo CRDT tests (fastest feedback)
./gradlew :server:test
./gradlew :composeApp:run                      # Desktop sample (needs :server running)
./gradlew :server:run                          # Port 8080
./gradlew :crdt:apiCheck :sync:apiCheck        # Binary-compat guard against committed api/ baselines
./gradlew :crdt:apiDump :sync:apiDump          # Regenerate baselines after intentional API changes
./gradlew :crdt:publishToMavenLocal :sync:publishToMavenLocal
```

## Library architecture

### `:crdt`

- `CrdtState<T>` — base interface with `merge`.
- `Delta<D>` — partial change with `clock`, `merge`, `isEmpty`.
- `DeltaState<S, D>` — `CrdtState` that supports `applyDelta(delta)`.
- `VectorClock`, `LwwRegister<T>` (uses `kotlin.time.Instant`), `OrSet<E>`,
  `DeltaBuffer<D>`.
- All types `@Serializable`.

### `:sync`

- `SyncMessage<D>` — sealed: `PushDelta`, `PullRequest`, `PullResponse`. Serialize
  with `SyncMessage.serializer(deltaSerializer)`.
- `SyncEngine<D : Delta<D>>` — client WebSocket engine. Constructor takes a
  `KSerializer<D>`, four suspending callbacks (`onRemoteDelta`,
  `getPendingDelta`, `restorePendingDelta`, `getLocalClock`), and an optional
  `SyncLogger` (defaults to `Noop`). `stop()` is suspending and joins the
  connect loop before returning. Exponential backoff reconnect.
- `SyncLogger` — `fun interface` sink for diagnostics. Replaces all internal
  `println` calls.
- `SyncStatus` — `Synced` / `Syncing` / `PendingChanges` / `Offline` / `Error`.
- `com.doppio.syncdo.sync.server.SyncServer<S : DeltaState<S, D>, D : Delta<D>>`
  (jvmMain) — authoritative state + bounded `DeltaLog` + mutex. Optional
  `onStateChanged` constructor hook fires inside the mutex after every merge
  (used by the sample `:server` for disk persistence).
- `Route.syncEndpoint(server, deltaSerializer, path = "/sync")` (jvmMain) —
  Ktor WebSocket handler; broadcasts pushes to peers, prunes peers whose send
  fails, answers pulls with missed deltas, falls back to full-state delta if
  the log doesn't reach the client's clock.

## Sample data flow (Todo)

1. User action → `TodoViewModel` → `TodoRepository.addTodo(...)`.
2. `OfflineFirstTodoRepository` updates local `TodoListCrdt`, persists JSON via
   `JsonFileStorage`, records a `TodoListDelta` in its `DeltaBuffer`.
3. `SyncEngine<TodoListDelta>` pushes buffered delta over WebSocket.
4. `:server` → `SyncServer.mergeDelta(...)` → broadcast `PullResponse` to peers.
5. Peers' `onRemoteDelta` callback → `repository.mergeRemoteDelta(...)` → state
   update + re-persist.

## Conventions

- Manual DI: `composeApp/.../di/AppModule.kt`. No Koin/Dagger.
- Server port `8080` (see `shared/.../Constants.kt`). Host defaults to
  `localhost`; overridable in `AppModule.initialize(serverHost)`.
- `FileIO` in `:shared` uses `expect/actual` per platform — sample concern, not
  part of the library surface.
- Publishing metadata lives in `gradle.properties` (`GROUP`, `VERSION_NAME`)
  and each library's `build.gradle.kts` (`mavenPublishing { pom { … } }`).
- Any change to `:crdt` or `:sync` public API must be followed by
  `./gradlew :<module>:apiDump` and a committed `api/` diff — `apiCheck` runs
  in `build` and will fail otherwise.

## `design.pen`

Pencil design file at the repo root. Read only via the Pencil MCP tools, not
standard file tools.
