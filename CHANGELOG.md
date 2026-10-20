# Changelog

All notable changes to the published libraries (`:crdt`, `:sync`) are recorded
here. Sample code (`:shared`, `:composeApp`, `:server`) is excluded.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [SemVer](https://semver.org/spec/v2.0.0.html). Until 1.0 the
public API may evolve; breaking changes are called out below.

## [Unreleased]

### Added
- API reference generated with Dokka (`./gradlew dokkaGenerate`, output in
  `build/dokka/html`), with a landing page per module. The published
  `-javadoc.jar` now contains this HTML documentation instead of being empty.
- `ClockedState<S>` in `:sync`: a `CrdtState` that carries a `VectorClock`.
  This is all the sync layer requires from an application state.
- `OrSet.addDelta` / `OrSet.removeDelta` (`:crdt`): return the change as a
  small `OrSet`, to apply with `merge` and to ship over the network.
- `SyncLogger` (`fun interface`) in `:sync`. Inject via `SyncEngine`'s
  `logger` parameter (defaults to `SyncLogger.Noop`). Internal `println` calls
  have been removed.
- `SyncServer.onStateChanged` constructor hook (`:sync`, JVM) - fires inside
  the merge mutex with the new state, intended for persistence, metrics, and
  audit logs.
- JSON round-trip tests for every `@Serializable` type in `:crdt`, tests for
  `DeltaBuffer` and `SyncServer`, and an end-to-end integration test for
  `SyncRoute` via `testApplication`.

### Changed
- **Breaking (`:crdt`):** one type for states and deltas. `Delta`,
  `DeltaState` and `OrSetDelta` are removed; `CrdtState` (`merge`) is the only
  interface. `OrSet.addWithDelta` / `removeWithDelta` / `applyDelta` /
  `toDelta` are replaced by `addDelta` / `removeDelta` + `merge`.
  `OrSet.add` now takes a `UniqueTag`.
- **Breaking (`:crdt`):** `VectorClock.dominates` is removed (use
  `isLessThanOrEqual`). `mergeValues` and `unionValues` are now internal;
  `mergeStates` stays public.
- **Breaking:** `DeltaBuffer` moved from `:crdt` to `:sync`
  (`com.doppio.syncdo.sync.DeltaBuffer`) and is coroutine-safe (`suspend`
  `record` / `flush`). `restore` is removed: record a failed delta again.
- **Breaking (`:sync`):** `SyncEngine`, `SyncServer`, `syncEndpoint` and
  `SyncMessage` take a single `ClockedState` type parameter.
  `SyncServer` takes the initial state directly and no longer needs
  `fullStateAsDelta`.
- **Breaking (`:sync`):** `SyncEngine.stop()` is now `suspend` and waits for the
  connect coroutine to fully cancel before returning. Removes the
  start/stop race that could leave two WebSockets open.
- **Breaking (`:sync`):** `SyncEngine`'s `getPendingDelta` and
  `restorePendingDelta` callbacks are now `suspend` so they can call the
  suspending `DeltaBuffer` API.
- `:crdt` no longer depends on `kotlinx-coroutines`; `:sync` exposes it as an
  `api` dependency.

### Fixed
- `:sync` no longer compiles with `-Xexplicit-backing-fields`, which flagged every
  class of the published artifact as built by a pre-release Kotlin compiler and
  forced consumers to pass `-Xskip-prerelease-check`.
- Both libraries now pin the JVM bytecode level of their `-jvm` artifact to
  Java 11 (previously inherited from the JDK running Gradle), enable Kotlin's
  explicit API mode, and are configured for Maven Central (Central Portal,
  signed publications).
- The server no longer sends the full state after every push: an up-to-date
  client now gets no catch-up message.
- A client further behind than the server's delta log now receives the full
  state instead of silently missing the evicted deltas.
- `SyncRoute` now removes dead peers from its connection set when a broadcast
  send fails, preventing accumulation of stale sessions.

## [0.1.0-SNAPSHOT]

Initial pre-release.
