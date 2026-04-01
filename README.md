# SyncDO

A collaborative todo app built with Kotlin Multiplatform. Uses CRDTs (Conflict-free Replicated Data Types) for real-time, conflict-free synchronization across devices — no central conflict resolution needed.

**Targets**: Android, iOS, Desktop (JVM)
**Server**: Ktor with WebSocket sync

## Architecture

```
┌──────────────┐  ┌─────────────┐  ┌─────────────┐
│   Android    │  │   Desktop   │  │     iOS     │
│  Compose UI  │  │  Compose UI │  │  Compose UI │
└──────┬───────┘  └──────┬──────┘  └───────┬─────┘
       │                 │                 │
       └─────────┬───────┘─────────┬───────┘
                 │                 │
          ┌──────┴──────┐  ┌───────┴─────┐
          │   shared    │  │   server    │
          │  CRDT core  │  │  Ktor WS    │
          │  Sync engine│  │  State store│
          │  Repository │  │  Broadcast  │
          └─────────────┘  └─────────────┘
```

- **`shared/`** — CRDT types, sync protocol, persistence, repository (Android, iOS, JVM)
- **`composeApp/`** — Compose Multiplatform UI (Android, iOS, Desktop)
- **`server/`** — Ktor WebSocket relay server (JVM)

For details on the CRDT implementation, see [docs/crdt-implementation.md](docs/crdt-implementation.md).

## Prerequisites

- **JDK 11+**
- **Android Studio** or **IntelliJ IDEA** with Kotlin Multiplatform plugin (for Android/iOS)
- Two or more devices/emulators on the same network (for the sync demo)

## Quick Start

### 1. Run the Server

The server listens on port `8080` and relays CRDT deltas between clients via WebSocket.

```shell
./gradlew :server:run
```

You should see:
```
... Responding at http://0.0.0.0:8080
```

Note your machine's local IP address (e.g., `192.168.1.42`). You'll enter this in the clients.

### 2. Run the Desktop App

In a separate terminal:

```shell
./gradlew :composeApp:run
```

On launch, enter the server IP (or `localhost` if running on the same machine) and tap **Connect**.

### 3. Run on Android

Build and install the debug APK:

```shell
./gradlew :composeApp:assembleDebug
```

Or use the Android Studio run configuration. Enter the server's local IP address when prompted.

### 4. Run on iOS

Open `iosApp/` in Xcode and run on a simulator or device. Enter the server IP when prompted.

## CRDT Sync Demo

This demo shows how CRDTs handle concurrent edits and offline scenarios.

### Demo 1: Real-Time Sync

1. Start the server (`./gradlew :server:run`).
2. Open the app on **two devices** (e.g., Desktop + Android emulator) and connect both to the server.
3. On both devices, go to the **PEERS** tab and enable sync.
4. On Device A, add a task: "Buy groceries".
5. The task appears on Device B within a second.
6. On Device B, mark the task as completed.
7. The checkmark appears on Device A.

### Demo 2: Concurrent Edits (No Conflicts)

1. With sync active on both devices:
2. On Device A, tap a task and edit the **title** to "Buy organic groceries".
3. Simultaneously, on Device B, tap the same task and edit the **note** to "From the farmers market".
4. Both changes are preserved on both devices — title and note are independent LWW registers.

### Demo 3: Concurrent Edits (Same Field)

1. On Device A, edit a task title to "Meeting at 3pm".
2. At the same time, on Device B, edit the same title to "Meeting at 4pm".
3. Both devices converge to the **same value** — whichever write has the later timestamp wins (Last-Writer-Wins). If timestamps are identical, the higher node ID breaks the tie deterministically.

### Demo 4: Offline Resilience

1. On Device A, **disable sync** (PEERS tab → toggle off).
2. On Device A (offline), add a task: "Offline task from A".
3. On Device B (still online), add a task: "Task from B".
4. On Device A, **re-enable sync**.
5. Both tasks now appear on both devices. The buffered changes from Device A are pushed and merged cleanly.

### Demo 5: Add-Wins Semantics

1. On Device A, disable sync.
2. On Device B, delete a task.
3. On Device A (which still sees the task), edit that task's title.
4. Re-enable sync on Device A.
5. The task **survives** — add-wins semantics mean a concurrent edit (add/update) beats a remove.

## Running Tests

```shell
# All shared module tests (CRDT properties, vector clock, OR-set, etc.)
./gradlew :shared:jvmTest

# A specific test class
./gradlew :shared:jvmTest --tests "com.doppio.syncdo.crdt.VectorClockTest"

# Server tests
./gradlew :server:test

# Full build
./gradlew build
```

## Build Commands

| Command | Description |
|---|---|
| `./gradlew :server:run` | Start the sync server on port 8080 |
| `./gradlew :composeApp:run` | Run the desktop app |
| `./gradlew :composeApp:assembleDebug` | Build Android APK |
| `./gradlew :shared:jvmTest` | Run shared module tests |
| `./gradlew :server:test` | Run server tests |
| `./gradlew build` | Full build check |

## Project Structure

```
SyncDO/
├── shared/                          # Platform-agnostic business logic
│   └── src/commonMain/kotlin/
│       └── com/doppio/syncdo/
│           ├── crdt/                # CRDT types (VectorClock, LwwRegister, OrSet, etc.)
│           ├── sync/                # Sync protocol (SyncEngine, SyncMessage, NodeIdProvider)
│           ├── model/               # Domain models (TodoItem, SyncStatus)
│           ├── repository/          # Offline-first repository
│           └── persistence/         # JSON file storage
├── composeApp/                      # Compose Multiplatform UI
│   └── src/commonMain/kotlin/
│       └── com/doppio/syncdo/
│           ├── ui/                  # Screens and components
│           ├── viewmodel/           # TodoViewModel
│           └── di/                  # Manual DI (AppModule)
├── server/                          # Ktor WebSocket server
│   └── src/main/kotlin/
│       └── com/doppio/syncdo/
│           ├── routes/              # WebSocket sync routes
│           ├── store/               # Server state store + delta log
│           └── plugins/             # Ktor plugins (serialization, websockets)
└── docs/                            # Documentation
    └── crdt-implementation.md       # Detailed CRDT architecture
```
