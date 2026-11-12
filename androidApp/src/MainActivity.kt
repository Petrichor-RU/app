package nl.petrichor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import nl.petrichor.app.data.database.PetrichorDatabaseProvider
import nl.petrichor.app.data.location.initLocationDataStore
import nl.petrichor.app.platform.NotificationScheduler
import nl.petrichor.app.platform.journalActionFromIntent
import nl.petrichor.app.ui.journal.JournalActionBus
import nl.petrichor.app.ui.journal.JournalPendingAction

private const val REQUEST_NOTIFICATIONS = 1001

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initLocationDataStore(this)

        // Initialize Room database
        PetrichorDatabaseProvider.init(this)

        // Daily journal reminders (08:00 log outfit, 20:00 survey)
        NotificationScheduler.init(this)
        requestNotificationPermissionIfNeeded()
        handleJournalAction(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleJournalAction(intent)
    }

    private fun handleJournalAction(intent: Intent?) {
        when (journalActionFromIntent(intent)) {
            JournalPendingAction.ADD_OUTFIT -> JournalActionBus.request(JournalPendingAction.ADD_OUTFIT)
            JournalPendingAction.SURVEY -> JournalActionBus.request(JournalPendingAction.SURVEY)
            null -> Unit
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
