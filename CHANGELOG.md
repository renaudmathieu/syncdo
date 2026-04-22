# Changelog

All notable changes to the `:crdt` and `:sync` libraries are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `SyncEngine` now accepts an optional `onError: (Throwable) -> Unit` callback for
  observing connection and push failures. Defaults to a no-op — the engine no longer
  writes to stdout.
- Initial `jvmTest` coverage for `SyncServer`, `DeltaLog`, and an end-to-end
  `syncEndpoint` integration test using an embedded Ktor server.
- `commonTest` coverage for `DeltaBuffer`.
- Class-level KDoc on `VectorClock` and `LwwRegister`.

### Fixed

- `DeltaLog.getDeltasSince` now returns `null` when the client's clock predates the
  oldest retained delta, so `SyncServer` correctly falls back to sending full state
  instead of silently skipping evicted history.

### Known issues

- When a client is fully caught up, `SyncServer.getDeltaSince` returns the full state
  as a delta instead of an empty one. Merges are idempotent so the effect is a harmless
  no-op on the client, but the extra bytes are wasted. A future release will distinguish
  "caught up" from "needs full state" at the server level.

## [0.1.0] - TBD

Initial public release.

- `:crdt` — generic CRDT primitives: `VectorClock`, `LwwRegister`, `OrSet` / `OrSetDelta`,
  `DeltaBuffer`, `CrdtState`, `Delta`, `DeltaState`. Targets: android, iosArm64,
  iosSimulatorArm64, jvm.
- `:sync` — client `SyncEngine` (Ktor WebSocket, exponential-backoff reconnect) and
  server helpers (`SyncServer`, `syncEndpoint` Ktor route). Same targets; server helpers
  are jvmMain-only.
- Binary-compatibility baselines committed for both libraries.
