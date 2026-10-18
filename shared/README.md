# `:shared` (sample)

Todo-specific glue code that demonstrates `:crdt` + `:sync` in action. Not published.

- `crdt/` - `TodoListCrdt` composes the generic `:crdt` primitives into the sample's
  domain shape and implements `ClockedState` from `:sync`. It is also its own delta
  type: a delta is a small `TodoListCrdt` holding only the change.
- `model/` - UI-facing `TodoItem` (mapped from `TodoItemCrdt`).
- `persistence/` - `LocalStorage` + `JsonFileStorage` + `FileIO` (expect/actual per
  platform). App-specific sample of on-disk CRDT storage.
- `repository/` - `OfflineFirstTodoRepository` composes the Todo CRDT state, the
  persistence layer, and `SyncEngine<TodoListCrdt>`.

See the library READMEs for reusable APIs: [`:crdt`](../crdt/README.md),
[`:sync`](../sync/README.md).
