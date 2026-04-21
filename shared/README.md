# `:shared` (sample)

Todo-specific glue code that demonstrates `:crdt` + `:sync` in action. Not published.

- `crdt/` — `TodoListCrdt` and `TodoListDelta` implement `DeltaState` / `Delta` from
  `:crdt`, wiring generic primitives into the sample's domain shape.
- `model/` — UI-facing `TodoItem` (mapped from `TodoItemCrdt`).
- `persistence/` — `LocalStorage` + `JsonFileStorage` + `FileIO` (expect/actual per
  platform). App-specific sample of on-disk CRDT storage.
- `repository/` — `OfflineFirstTodoRepository` composes the Todo CRDT state, the
  persistence layer, and `SyncEngine<TodoListDelta>`.

See the library READMEs for reusable APIs: [`:crdt`](../crdt/README.md),
[`:sync`](../sync/README.md).
