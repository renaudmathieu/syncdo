# Changelog

All notable changes to the published libraries (`:crdt`, `:sync`) are recorded
here. Sample code (`:shared`, `:composeApp`, `:server`) is excluded.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [SemVer](https://semver.org/spec/v2.0.0.html). Until 1.0 the
public API may evolve; breaking changes are called out below.

## [Unreleased]

### Added
- `SyncLogger` (`fun interface`) in `:sync`. Inject via `SyncEngine`'s new
  `logger` parameter (defaults to `SyncLogger.Noop`). Internal `println` calls
  have been removed.
- `SyncServer.onStateChanged` constructor hook (`:sync`, JVM) - fires inside
  the merge mutex with the new state, intended for persistence, metrics, and
  audit logs.
- `DeltaBuffer` is now thread-safe via an internal `Mutex`. `record`, `flush`,
  and `restore` are `suspend` functions (`:crdt`).
- JSON round-trip tests for every `@Serializable` type in `:crdt` and an
  end-to-end integration test for `SyncRoute` via `testApplication`.
- KDoc for `LwwRegister.merge`, `OrSet.elements` / `contains`, `VectorClock.dominates`
  / `isLessThanOrEqual`, and the `SyncEngine` callback contract.

### Changed
- **Breaking (`:sync`):** `SyncEngine.stop()` is now `suspend` and waits for the
  connect coroutine to fully cancel before returning. Removes the
  start/stop race that could leave two WebSockets open.
- **Breaking (`:sync`):** `SyncEngine`'s `getPendingDelta` and
  `restorePendingDelta` callbacks are now `suspend (D) -> …` so they can call
  the new suspending `DeltaBuffer` API.
- `:crdt` now exposes `kotlinx-coroutines-core` as an `api` dependency
  (transitively visible to consumers).

### Fixed
- `SyncRoute` now removes dead peers from its connection set when a broadcast
  send fails, preventing accumulation of stale sessions.

## [0.1.0-SNAPSHOT]

Initial pre-release.
