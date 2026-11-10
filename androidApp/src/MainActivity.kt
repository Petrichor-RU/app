package nl.petrichor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import nl.petrichor.app.data.location.initLocationDataStore
import nl.petrichor.app.data.wardrobe.WardrobeDatabaseProvider
import nl.petrichor.app.data.wardrobe.initClothingImageStorage
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.petrichor.app.data.outfitLog.OutfitLogDatabaseProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initLocationDataStore(this)

        // Initialize Room databases
        WardrobeDatabaseProvider.init(this)
        OutfitLogDatabaseProvider.init(this)

        // Initialize image storage
        initClothingImageStorage(this)

        // Open the outfit logging database
        // This also ensures that its physical database file is created.
        lifecycleScope.launch {
            OutfitLogDatabaseProvider
                .getDatabase()
                .outfitLogDao()
                .getLogCount()
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
