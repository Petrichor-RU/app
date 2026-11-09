package nl.petrichor.app.data.wardrobe

actual object WardrobeDatabaseProvider {
    actual fun getDatabase(): WardrobeDatabase =
        error("Room is not used on wasmJs; use defaultClothingDao() instead.")
}
