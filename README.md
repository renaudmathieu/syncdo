# SyncDO

Kotlin Multiplatform libraries for CRDT-based sync, plus a collaborative todo app demonstrating them.

Licensed under [Apache 2.0](LICENSE).

## Modules

### Published libraries

- [**`:crdt`**](crdt/README.md) — `com.doppio.syncdo:crdt` — generic CRDT primitives
  (`VectorClock`, `LwwRegister`, `OrSet`, `Delta`/`DeltaState`, `DeltaBuffer`).
- [**`:sync`**](sync/README.md) — `com.doppio.syncdo:sync` — generic WebSocket sync
  engine + Ktor server routing. Parameterized on any `Delta<D>` from `:crdt`.

Targets: Android, iOS (arm64 + simulatorArm64), JVM.

### Sample apps (not published)

- [**`:shared`**](shared/README.md) — Todo domain glue: `TodoListCrdt`,
  `TodoListDelta`, `OfflineFirstTodoRepository`, file-based persistence.
- **`:composeApp`** — Compose Multiplatform UI (Android, iOS, Desktop).
- [**`:server`**](server/README.md) — Ktor sample server wiring the generic
  `syncEndpoint` with Todo CRDT types.

```
┌────────────────────────────────────────┐      ┌────────────┐
│    composeApp (Android / iOS / JVM)    │      │   server   │
└──────────────────┬─────────────────────┘      └─────┬──────┘
                   ▼                                   ▼
┌──────────────────────────────────────────────────────────┐
│                       :shared (sample)                   │
│   TodoListCrdt · TodoListDelta · OfflineFirstRepository  │
└─────────────────────┬──────────────────────┬─────────────┘
                      ▼                      ▼
            ┌──────────────────┐   ┌────────────────────┐
            │  :sync (library) │──▶│  :crdt (library)   │
            │  SyncEngine<D>   │   │  VectorClock       │
            │  SyncServer<S,D> │   │  LwwRegister       │
            │  SyncMessage<D>  │   │  OrSet · Delta     │
            └──────────────────┘   └────────────────────┘
```

Architecture deep-dive: [docs/crdt-implementation.md](docs/crdt-implementation.md).

## Using the libraries

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { mavenLocal(); mavenCentral() }
}
// build.gradle.kts (KMP, commonMain)
dependencies {
    implementation("com.doppio.syncdo:crdt:0.1.0-SNAPSHOT")
    implementation("com.doppio.syncdo:sync:0.1.0-SNAPSHOT")
}
```

Quickstarts: [`:crdt` README](crdt/README.md) (build a CRDT aggregate),
[`:sync` README](sync/README.md) (client engine + server route).

## Running the sample app

Prereqs: JDK 11+, Android Studio / IntelliJ with KMP plugin, two devices or
emulators for the sync demo.

```shell
./gradlew :server:run                  # Port 8080
./gradlew :composeApp:run              # Desktop
./gradlew :composeApp:assembleDebug    # Android APK
# iOS: open iosApp/iosApp.xcodeproj in Xcode → run the iosApp scheme.
```

Server config (env vars):

- `SYNCDO_PORT` — bind port (default `8080`).
- `SYNCDO_STATE_DIR` — directory for the persisted JSON state (default `./data`).

On first launch each client asks for the server host. Enter:

- `localhost` if the server runs on the same machine as a desktop client.
- `10.0.2.2` from an **Android emulator** to reach the host machine.
- Your machine's LAN IP (e.g. `192.168.1.42`) from an iOS simulator, physical
  device, or any other machine.

The host is persisted under the client's storage path, so subsequent launches
skip the prompt.

### Sync demo scenarios

1. **Real-time sync** — Add a task on device A, it appears on B within ~1s.
2. **Concurrent different fields** — Edit title on A and note on B simultaneously; both survive.
3. **Concurrent same field** — Both devices converge to the later-timestamp write (LWW, node-id tiebreak).
4. **Offline buffering** — Disable sync on A, mutate, re-enable; pending deltas push cleanly.
5. **Add-wins** — Concurrent edit + delete → edit wins (OR-Set semantics).

## Tests & build

```shell
./gradlew build                                 # Everything
./gradlew :crdt:allTests :sync:allTests         # Library tests (incl. iOS sim)
./gradlew :shared:jvmTest :server:test          # Sample tests
./gradlew :crdt:apiCheck :sync:apiCheck         # Binary-compat guard
./gradlew :crdt:publishToMavenLocal \
          :sync:publishToMavenLocal             # Local publish
```
