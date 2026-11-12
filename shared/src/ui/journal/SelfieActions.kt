package nl.petrichor.app.ui.journal

/** Callbacks for the selfie picker buttons. */
internal class SelfieActions(
    val onPickGallery: () -> Unit,
    val onPickCamera: () -> Unit,
    val onRemovePhoto: () -> Unit,
)
