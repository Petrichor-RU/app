package nl.petrichor.app.data.wardrobe

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

actual object WardrobeDatabaseProvider {
    private val dbInstance: WardrobeDatabase by lazy {
        Room.databaseBuilder<WardrobeDatabase>(
            name = WardrobeDatabase.DATABASE_NAME,
        )
            .setDriver(WebWorkerSQLiteDriver(sqliteWorker()))
            .build()
    }

    actual fun getDatabase(): WardrobeDatabase = dbInstance
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun sqliteWorker(): Worker =
    js(
        """new Worker(
            new URL("@androidx/sqlite-web-worker/worker.js", import.meta.url),
            { type: "module" }
        )"""
    )

actual fun defaultClothingDao(): ClothingDao = WardrobeDatabaseProvider.getDatabase().clothingDao()
