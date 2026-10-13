# Project guidance

- Keep the app structure simple and consistent with the existing Compose architecture.
- The project should only have one `MaterialSymbolsRenderingScope`, and it must be defined in `App.kt`.
- Do not create additional `MaterialSymbolsRenderingScope` instances in other files or composables.
- Prefer updating the existing app-level scope in `App.kt` instead of introducing duplicate scopes elsewhere.
- Keep changes focused and avoid unrelated refactors.
