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

For the wardrobe feature:
- `WardrobeScreen` owns the selected filter, add/edit dialog visibility, selected clothing ID, detail transition state, and snackbar presentation.
- `WardrobeScreen` renders the detail screen from the selected clothing ID and keeps a separate closing item while the exit animation finishes. Deleting an item starts deletion and closes the detail screen; the detail transition must be allowed to finish before clearing its selection.
- `WardrobeFilterBar` reports `null` for “All Types” and a clothing type ID for a specific filter. Filtering is performed by `WardrobeScreen`, not by the filter bar.
- `AddClothingDialog` is shared by add and edit flows. A clothing type is required for a new item, while edit mode starts with the existing values and may retain or replace its image.
- `WardrobeRepository` is the business boundary around `ClothingDao`: new items receive a timestamp, existing timestamps are preserved, built-in types are inserted on the first type-flow collection, and mutations are dispatched off the UI thread.
- Keep wardrobe data/repository tests in `shared/test/data/wardrobe`. Use fakes for `ClothingDao` so tests do not depend on platform Room drivers, image picker state, or permanent image storage. UI tests should assert user-visible semantics and callbacks; animation timing and platform image selection are integration concerns.
- JVM Compose UI tests live in `shared/test@jvm/ui/wardrobe` and use `runDesktopComposeUiTest`. The JVM test target needs `$compose.uiTest` plus the Linux Skiko runtime; wrap wardrobe UI content in `MaterialSymbolsRenderingScope` for the test environment because the production scope remains app-wide in `App.kt`.