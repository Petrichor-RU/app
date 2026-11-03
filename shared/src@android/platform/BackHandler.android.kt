package nl.petrichor.app.platform

import androidx.activity.compose.BackHandler as ComposeBackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    ComposeBackHandler(
        enabled = enabled,
        onBack = onBack
    )
}
