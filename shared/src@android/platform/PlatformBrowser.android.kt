@file:Suppress("MatchingDeclarationName") // platform actual of the common expect object

package nl.petrichor.app.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

actual object PlatformBrowser {
    @Suppress("TooGenericExceptionCaught") // no handler for ACTION_VIEW must not crash, matching other platforms
    actual fun open(url: String) {
        val intent =
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            applicationContext().startActivity(intent)
        } catch (_: Exception) {
            // No activity handles ACTION_VIEW (no browser installed, non-http url): degrade
            // silently, matching the JVM/iOS/WASM actuals.
        }
    }

    /**
     * Resolves the application context on demand (same technique as MediaStorage) so no context
     * is held in a field that could go stale.
     */
    @SuppressLint("PrivateApi")
    @Suppress("TooGenericExceptionCaught") // reflection failures all mean "try the next lookup"
    private fun applicationContext(): Context =
        try {
            Class
                .forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as Context
        } catch (_: Exception) {
            Class
                .forName("android.app.AppGlobals")
                .getMethod("getInitialApplication")
                .invoke(null) as Context
        }
}
