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
    fun `calendar shows the week of the selected day by default and marks it as selected`() = runDesktopComposeUiTest {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    RecommendationCalendar(
                        selectedDate = LocalDate(2026, 11, 5),
                        today = today,
                        onDateSelected = {}
                    )
                }
            }
        }

        onNodeWithText("November 2026").assertIsDisplayed()
        onNodeWithText("5").assertIsSelected()
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

        onNodeWithText("7").performClick()

        assertEquals(LocalDate(2026, 11, 7), selectedDate)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `expanding to full month shows the month grid and navigates between months`() = runDesktopComposeUiTest {
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

        onNodeWithContentDescription("Show full month").performClick()
        onNodeWithText("15").assertIsDisplayed()

        onNodeWithContentDescription("Next month").performClick()
        onNodeWithText("December 2026").assertIsDisplayed()

        onNodeWithText("Today").performClick()
        onNodeWithText("November 2026").assertIsDisplayed()
        assertEquals(today, selectedDate)
    }
}
