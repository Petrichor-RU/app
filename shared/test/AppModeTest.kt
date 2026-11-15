package nl.petrichor.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AppModeTest {
    @Test
    fun `jvm launch argument selects presentation mode`() {
        assertEquals(AppMode.PRESENTATION, AppMode.fromJvmLaunchArgs(listOf("--presentation"), null))
    }

    @Test
    fun `jvm system property selects presentation mode`() {
        assertEquals(AppMode.PRESENTATION, AppMode.fromJvmLaunchArgs(emptyList(), "presentation"))
    }

    @Test
    fun `jvm parser keeps app mode without a switch`() {
        assertEquals(AppMode.APP, AppMode.fromJvmLaunchArgs(emptyList(), null))
        assertEquals(AppMode.APP, AppMode.fromJvmLaunchArgs(emptyList(), "app"))
        assertEquals(AppMode.APP, AppMode.fromJvmLaunchArgs(listOf("--other"), "presentation-mode"))
    }

    @Test
    fun `wasm query parameter selects presentation mode`() {
        assertEquals(AppMode.PRESENTATION, AppMode.fromUrlQuery("?mode=presentation"))
        assertEquals(
            AppMode.PRESENTATION,
            AppMode.fromUrlQuery("?theme=dark&mode=presentation"),
        )
    }

    @Test
    fun `wasm parser keeps app mode without the parameter`() {
        assertEquals(AppMode.APP, AppMode.fromUrlQuery(null))
        assertEquals(AppMode.APP, AppMode.fromUrlQuery(""))
        assertEquals(AppMode.APP, AppMode.fromUrlQuery("?"))
        assertEquals(AppMode.APP, AppMode.fromUrlQuery("?mode=app"))
        assertEquals(AppMode.APP, AppMode.fromUrlQuery("?presentation=mode"))
    }
}
