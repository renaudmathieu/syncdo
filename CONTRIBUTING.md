# Contributing

Thanks for your interest in SyncDO! A few notes to make contributions land
smoothly.

## Project layout

- `:crdt` and `:sync` are **published libraries**. Their public APIs are pinned
  by `kotlinx-binary-compatibility-validator` against the `api/` baselines in
  each module. Any visible change must be followed by `./gradlew :<module>:apiDump`
  and a committed `api/` diff.
- `:shared`, `:composeApp`, `:server` are **sample code** demonstrating how to
  use the libraries. They are not published. Domain-specific types (anything
  Todo-flavoured) belong here, not in the libraries.

See [CLAUDE.md](CLAUDE.md) for a deeper map of the repo.

## Local development

```shell
./gradlew build                                # everything (slow first time)
./gradlew :shared:jvmTest                      # fastest feedback for sample CRDT
./gradlew :crdt:allTests :sync:allTests        # libraries (incl. iOS sim)
./gradlew :crdt:apiCheck :sync:apiCheck        # binary-compat gate
./gradlew :crdt:apiDump :sync:apiDump          # regenerate baselines on intentional API change
./gradlew :crdt:publishToMavenLocal :sync:publishToMavenLocal
```

To run the demo end-to-end locally:

```shell
./gradlew :server:run                          # in one terminal
./gradlew :composeApp:run                      # in another (desktop)
```

## Coding conventions

- Kotlin official style (`kotlin.code.style=official`).
- Public library APIs need KDoc. Method-level docs should call out exception
  behaviour and any subtle ordering constraints.
- Prefer changes to `.kts` build files via `libs.versions.toml`; don't pin
  versions inline.
- Logging in libraries goes through `SyncLogger` (see `:sync`). Don't
  reintroduce `println` in `commonMain`.

## Pull requests

- One logical change per PR.
- Run `./gradlew check` before opening - it covers tests and `apiCheck` for the
  published libraries.
- If your change touches `:crdt` or `:sync` public surface, mention it in the
  PR description and include the `apiDump` diff.

## License

By contributing you agree your work is licensed under Apache 2.0 (see
[LICENSE](LICENSE)).
