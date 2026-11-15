package nl.petrichor.app.data

import nl.petrichor.app.AppMode
import java.io.File

/**
 * Directory holding the app's on-disk data: the wardrobe database, stored images and the
 * locations DataStore. In presentation mode (announced by the desktop entry point through the
 * `petrichor.mode` system property before any storage is created) a separate root is used so the
 * demonstration never touches the normal app's data and always starts empty.
 */
internal fun petrichorDataDirectory(): File {
    val directoryName =
        if (System.getProperty(AppMode.JVM_MODE_PROPERTY) == AppMode.PRESENTATION_VALUE) {
            ".petrichor-presentation"
        } else {
            ".petrichor"
        }
    return File(System.getProperty("user.home"), directoryName)
        .apply { check(mkdirs() || isDirectory) { "Could not create petrichor data directory." } }
}
