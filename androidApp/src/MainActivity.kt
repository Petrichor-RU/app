package nl.petrichor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import nl.petrichor.app.data.wardrobe.WardrobeDatabaseProvider
import nl.petrichor.app.data.wardrobe.initClothingImageStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Initialize Room database
        WardrobeDatabaseProvider.init(this)
        initClothingImageStorage(this)
        
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
