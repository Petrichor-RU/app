package nl.petrichor.app

/**
 * Which face of the app runs. [APP] is the normal application with its tab shell;
 * [PRESENTATION] is the on-stage slide deck (writeups/2026-11-15-PRESENTATION.md). The mode is
 * decided at runtime by the platform entry points — desktop via launch arguments, WASM via the
 * page URL — and both Android and iOS always stay in [APP].
 */
enum class AppMode {
    APP,
    PRESENTATION,
    ;

    companion object {
        /** System property the desktop entry point uses to announce the parsed mode. */
        const val JVM_MODE_PROPERTY = "petrichor.mode"

        /** Value of [JVM_MODE_PROPERTY] (and of the query parameter) that selects presentation mode. */
        const val PRESENTATION_VALUE = "presentation"

        private const val PRESENTATION_ARGUMENT = "--presentation"
        private const val MODE_QUERY_PARAMETER = "mode=$PRESENTATION_VALUE"

        /** JVM launch decision: presentation when args contain [PRESENTATION_ARGUMENT] or the property is set. */
        fun fromJvmLaunchArgs(
            args: List<String>,
            modeProperty: String?,
        ): AppMode = if (PRESENTATION_ARGUMENT in args || modeProperty == PRESENTATION_VALUE) PRESENTATION else APP

        /** WASM page decision: presentation when the query contains `mode=presentation`. */
        fun fromUrlQuery(query: String?): AppMode =
            if (
                query != null &&
                query.removePrefix("?").split('&').any { it.equals(MODE_QUERY_PARAMETER, ignoreCase = true) }
            ) {
                PRESENTATION
            } else {
                APP
            }
    }
}
