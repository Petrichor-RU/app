@file:Suppress("MatchingDeclarationName") // platform file hosts the provider object and the dao accessors

package nl.petrichor.app.data.database

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import nl.petrichor.app.data.isPresentationMode
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.wardrobe.ClothingDao
import org.w3c.dom.Worker

actual object PetrichorDatabaseProvider {
    private val dbInstance: PetrichorDatabase by lazy {
        Room
            .databaseBuilder<PetrichorDatabase>(
                // Presentation mode (URL ?mode=presentation) uses a suffixed OPFS database so
                // the browser profile's real app data is untouched.
                name = if (isPresentationMode()) PRESENTATION_DATABASE_NAME else PetrichorDatabase.DATABASE_NAME,
            ).setDriver(WebWorkerSQLiteDriver(sqliteWorker()))
            .fallbackToDestructiveMigration(true)
            .build()
    }

    actual fun getDatabase(): PetrichorDatabase = dbInstance
}

/** Database file used in presentation mode, kept separate from the normal app's OPFS database. */
private const val PRESENTATION_DATABASE_NAME = "petrichor-presentation.db"

@OptIn(ExperimentalWasmJsInterop::class)
private fun sqliteWorker(): Worker =
    js(
        """new Worker(
            new URL("@androidx/sqlite-web-worker/worker.js", import.meta.url),
            { type: "module" }
        )""",
    )

actual fun defaultClothingDao(): ClothingDao = PetrichorDatabaseProvider.getDatabase().clothingDao()

actual fun defaultOutfitDao(): OutfitDao = PetrichorDatabaseProvider.getDatabase().outfitDao()
