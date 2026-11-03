package nl.petrichor.app.ui.recommendation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendationCalendarTest {
    private val today = LocalDate(2026, 11, 3)

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `calendar shows the month of the selected day and marks it as selected`() = runDesktopComposeUiTest {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    RecommendationCalendar(
                        selectedDate = LocalDate(2026, 11, 12),
                        today = today,
                        onDateSelected = {}
                    )
                }
            }
        }

        onNodeWithText("November 2026").assertIsDisplayed()
        onNodeWithText("12").assertIsSelected()
        onNodeWithText("3").assertIsNotSelected()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `clicking a day reports its date`() = runDesktopComposeUiTest {
        var selectedDate: LocalDate? = null
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    RecommendationCalendar(
                        selectedDate = today,
                        today = today,
                        onDateSelected = { selectedDate = it }
                    )
                }
            }
        }

        onNodeWithText("15").performClick()

        assertEquals(LocalDate(2026, 11, 15), selectedDate)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `month navigation changes the displayed month and today jumps back`() = runDesktopComposeUiTest {
        var selectedDate: LocalDate? = null
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    RecommendationCalendar(
                        selectedDate = today,
                        today = today,
                        onDateSelected = { selectedDate = it }
                    )
                }
            }
        }

        onNodeWithContentDescription("Next month").performClick()
        onNodeWithText("December 2026").assertIsDisplayed()

        onNodeWithText("Today").performClick()
        onNodeWithText("November 2026").assertIsDisplayed()
        assertEquals(today, selectedDate)
    }
}
