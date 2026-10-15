# Project guidance

- Keep the app structure simple and consistent with the existing Compose architecture.
- The project should only have one `MaterialSymbolsRenderingScope`, and it must be defined in `App.kt`.
- Do not create additional `MaterialSymbolsRenderingScope` instances in other files or composables.
- Prefer updating the existing app-level scope in `App.kt` instead of introducing duplicate scopes elsewhere.
- Keep changes focused and avoid unrelated refactors.

For the clothing detail screen:
- State should be remember. If I open a detail view, switch tabs, and go back, the detail view should still be open
- The opening animation should only play when opening the tab
- The closing animation should only play when closing the tab