package nl.petrichor.app.platform

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler as ComposeBackHandler

@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    ComposeBackHandler(
        enabled = enabled,
        onBack = onBack,
    )
}
