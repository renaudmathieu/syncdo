# SyncDO

Kotlin Multiplatform libraries for CRDT-based sync, plus a collaborative todo app demonstrating them.

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
# iOS: open iosApp/ in Xcode
```

Enter the server's LAN IP in each client (or `localhost` if on the same host).

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
