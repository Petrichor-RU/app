package nl.petrichor.app.ui.recommendation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarHeaderPullControllerTest {
    @Test
    fun `pulling down at the very top reveals the header past the threshold`() {
        val controller = CalendarHeaderPullController({ true }, Density(1f))
        assertFalse(controller.headerVisible)

        val consumed = controller.connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, 50f), consumed) // the pull is consumed, not scrolled
        assertFalse(controller.headerVisible)

        controller.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        assertTrue(controller.headerVisible)
    }

    @Test
    fun `scrolling back down hides the header again`() {
        val controller = CalendarHeaderPullController({ true }, Density(1f))
        controller.connection.onPreScroll(Offset(0f, 100f), NestedScrollSource.UserInput)
        assertTrue(controller.headerVisible)

        controller.connection.onPostScroll(Offset(0f, -50f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(controller.headerVisible)
    }

    @Test
    fun `list scrolling between pulls resets the accumulated pull`() {
        val controller = CalendarHeaderPullController({ true }, Density(1f))
        controller.connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        controller.connection.onPostScroll(Offset(0f, 20f), Offset(0f, 10f), NestedScrollSource.UserInput) // list scrolled

        controller.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        assertFalse(controller.headerVisible) // 60 < 96 after the reset
    }

    @Test
    fun `pulling down while content above remains does not reveal the header`() {
        val controller = CalendarHeaderPullController({ false }, Density(1f))

        val consumed = controller.connection.onPreScroll(Offset(0f, 100f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumed) // left for the list to scroll
        assertFalse(controller.headerVisible)
    }
}
