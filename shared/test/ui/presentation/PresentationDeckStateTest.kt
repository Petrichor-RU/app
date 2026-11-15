package nl.petrichor.app.ui.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationDeckStateTest {
    @Test
    fun `forward advances within bounds`() {
        val state = PresentationDeckState(slideCount = 3)
        assertTrue(state.next())
        assertEquals(1, state.slideIndex)
        assertTrue(state.next())
        assertEquals(2, state.slideIndex)
    }

    @Test
    fun `forward is a no-op on the last slide`() {
        val state = PresentationDeckState(slideCount = 3, initialSlide = 2)
        assertFalse(state.next())
        assertEquals(2, state.slideIndex)
    }

    @Test
    fun `backward retreats within bounds`() {
        val state = PresentationDeckState(slideCount = 3, initialSlide = 2)
        assertTrue(state.previous())
        assertEquals(1, state.slideIndex)
        assertTrue(state.previous())
        assertEquals(0, state.slideIndex)
    }

    @Test
    fun `backward is a no-op on the first slide`() {
        val state = PresentationDeckState(slideCount = 3)
        assertFalse(state.previous())
        assertEquals(0, state.slideIndex)
    }

    @Test
    fun `disabled states at the deck ends`() {
        val state = PresentationDeckState(slideCount = 3)
        assertFalse(state.canGoBack)
        assertTrue(state.canGoForward)
        state.next()
        assertTrue(state.canGoBack)
        assertTrue(state.canGoForward)
        state.next()
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
    }

    @Test
    fun `initial slide out of range is clamped into the deck`() {
        assertEquals(2, PresentationDeckState(slideCount = 3, initialSlide = 7).slideIndex)
        assertEquals(0, PresentationDeckState(slideCount = 3, initialSlide = -4).slideIndex)
    }
}
