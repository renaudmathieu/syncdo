# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SyncDO is a collaborative todo app built with Kotlin Multiplatform (KMP), using CRDTs for conflict-free real-time synchronization across devices. Targets: Android, iOS, Desktop (JVM), with a Ktor WebSocket server.

## Build & Run Commands

```shell
# Build shared module tests (JVM target — fastest feedback loop)
./gradlew :shared:jvmTest

# Run a single test class
./gradlew :shared:jvmTest --tests "com.doppio.syncdo.crdt.VectorClockTest"

# Run desktop app (requires server running)
./gradlew :composeApp:run

# Run server (port 8080)
./gradlew :server:run

# Build Android APK
./gradlew :composeApp:assembleDebug

# Run server tests
./gradlew :server:test

# Full build check
./gradlew build
```

## Architecture

### Three Gradle modules

- **`shared`** — Platform-agnostic business logic: CRDT types, sync protocol, persistence, repository. This is the core of the app. Targets: Android, iOS, JVM.
- **`composeApp`** — Compose Multiplatform UI layer. Depends on `shared`. Targets: Android, iOS, JVM (desktop).
- **`server`** — Ktor server (Netty, JVM only). Depends on `shared` for CRDT and sync message types. WebSocket endpoint at `/sync`.

### CRDT layer (`shared/.../crdt/`)

The sync model is built on composable CRDTs:
- **`VectorClock`** — Logical timestamps per node for causal ordering.
- **`LwwRegister<T>`** — Last-writer-wins register using `kotlinx-datetime` Instant + node ID tiebreaker.
- **`OrSet<T>`** — Observed-Remove Set with unique tags for add/remove conflict resolution.
- **`TodoItemCrdt`** — Composes LWW registers for each field (title, completed, note, position).
- **`TodoListCrdt`** — Top-level aggregate: OrSet for item membership + map of TodoItemCrdt per item.
- **`TodoListDelta`** — Partial diff structure (added/updated items, added/removed tags, clock) used for incremental sync instead of shipping full state.

All CRDT types implement `CrdtState<T>` with a `merge()` function. They are `@Serializable`.

### Sync protocol (`shared/.../sync/`)

- **`SyncMessage`** — Sealed class with variants: `PushDelta`, `PullRequest`, `PullResponse`, `FullSync`. Serialized as JSON over WebSocket.
- **`SyncEngine`** — Client-side WebSocket connection with exponential backoff reconnection. On connect, sends `PullRequest` with local clock, then pushes pending deltas.
- **`DeltaBuffer`** — Accumulates local changes into a pending delta until successfully pushed.

### Data flow

1. User action → `TodoViewModel` → `TodoRepository.addTodo()`
2. `OfflineFirstTodoRepository` updates local CRDT state, persists to JSON file, buffers delta
3. `SyncEngine` pushes delta to server via WebSocket
4. Server (`SyncRoutes`) merges into `ServerStateStore`, broadcasts to other clients
5. Remote clients receive `PullResponse`, merge delta into local state

### Persistence (`shared/.../persistence/`)

- `LocalStorage` interface with `JsonFileStorage` implementation — serializes full `TodoListCrdt` to a JSON file.
- `FileIO` has expect/actual declarations per platform (JVM, Android, iOS) for file read/write.

### DI

Manual DI via `AppModule` singleton in `composeApp`. No framework (no Koin/Dagger).

### UI (`composeApp/.../ui/`)

Compose Multiplatform with Material 3. Screens: `TaskListScreen`, `TaskDetailScreen`, `PeersScreen`. Single `TodoViewModel` backed by repository.

## Key Constants

- Server port: `8080` (defined in `shared/.../Constants.kt`)
- Server host: `localhost` by default (configurable in `AppModule.initialize()`)

## Design File

`design.pen` at root is a Pencil design file — read only via Pencil MCP tools, not with standard file tools.
