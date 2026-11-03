package nl.petrichor.app.platform

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit
)
