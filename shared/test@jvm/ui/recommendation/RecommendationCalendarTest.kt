package nl.petrichor.app.ui.recommendation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecommendationCalendarTest {
    private val today = LocalDate(2026, 11, 3)

    private fun DesktopComposeUiTest.setCalendar(
        selectedDate: LocalDate = today,
        onDateSelected: (LocalDate) -> Unit = {},
    ) {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    RecommendationCalendar(
                        selectedDate = selectedDate,
                        today = today,
                        onDateSelected = onDateSelected,
                    )
                }
            }
        }
    }

    @Test
    fun `calendar shows the week of the selected day by default and marks it as selected`() = runDesktopComposeUiTest {
        setCalendar(selectedDate = LocalDate(2026, 11, 5))

        onNodeWithText("November 2026").assertIsDisplayed()
        onNodeWithText("5").assertIsSelected()
        onNodeWithText("3").assertIsNotSelected()
    }

    @Test
    fun `clicking a day reports its date`() = runDesktopComposeUiTest {
        var selectedDate: LocalDate? = null
        setCalendar(onDateSelected = { selectedDate = it })

        onNodeWithText("7").performClick()

        assertEquals(LocalDate(2026, 11, 7), selectedDate)
    }

    @Test
    fun `swiping left on the calendar advances to the next week`() = runDesktopComposeUiTest {
        setCalendar()

        onNodeWithTag("calendar_pager").performTouchInput { swipeLeft() }

        onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun `expanding to full month shows the month grid and navigates between months`() = runDesktopComposeUiTest {
        var selectedDate: LocalDate? = null
        setCalendar(onDateSelected = { selectedDate = it })

        onNodeWithContentDescription("Show full month").performClick()
        onNodeWithText("15").assertIsDisplayed()

        onNodeWithContentDescription("Next month").performClick()
        onNodeWithText("December 2026").assertIsDisplayed()

        onNodeWithText("Today").performClick()
        onNodeWithText("November 2026").assertIsDisplayed()
        assertEquals(today, selectedDate)
    }

    @Test
    fun `toggling between week and month view stays in the currently displayed month`() = runDesktopComposeUiTest {
        setCalendar()

        repeat(5) { onNodeWithContentDescription("Next week").performClick() }
        onNodeWithText("December 2026").assertIsDisplayed()

        onNodeWithContentDescription("Show full month").performClick()
        onNodeWithText("December 2026").assertIsDisplayed()

        onNodeWithContentDescription("Next month").performClick()
        onNodeWithText("January 2027").assertIsDisplayed()

        onNodeWithContentDescription("Show week").performClick()
        onNodeWithText("January 2027").assertIsDisplayed()
    }
}
