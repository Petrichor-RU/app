package nl.petrichor.app.ui.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class OutfitDetailSheetTest {
    private val outfit =
        OutfitEntryWithPieces(
            OutfitEntry(
                id = 1L,
                wornOnDate = LocalDate(2026, 11, 13),
                photoPath = null,
                notes = "A comfortable outfit.\n".repeat(60),
                temperatureC = 15.0,
                feelsLikeC = null,
                minTempC = null,
                maxTempC = null,
                weatherDescription = "Cloudy",
                weatherIcon = null,
                isRainy = false,
                surveyFeelingScore = null,
                surveyWarmthVerdict = null,
                surveyedAtEpochMs = null,
            ),
            emptyList(),
        )

    @Test
    fun `selfie and entry details adapt at six hundred dp with survey reachable in short windows`() {
        for ((width, height) in listOf(360 to 800, 599 to 800, 600 to 800, 1200 to 800, 1200 to 360)) {
            runDesktopComposeUiTest(width = width, height = height) {
                var surveyOpened = false
                val actions = OutfitDetailActions({}, {}, {}, { surveyOpened = it == outfit }, { _, _ -> })
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme { OutfitDetailSheet(outfit, actions) }
                    }
                }

                val imageBounds = onNodeWithContentDescription("No selfie").getUnclippedBoundsInRoot()
                val notesBounds = onNodeWithText(outfit.entry.notes!!).getUnclippedBoundsInRoot()
                if (width >= 600) {
                    onNodeWithContentDescription("No selfie").assertIsDisplayed()
                    assertTrue(notesBounds.left > imageBounds.right, "Details should be beside the selfie at $width")
                } else {
                    assertTrue(notesBounds.top > imageBounds.bottom, "Details should be below the selfie at $width")
                }
                onNodeWithText("Cloudy").performScrollTo().assertIsDisplayed()
                onNodeWithText("Fill in survey").performScrollTo().performClick()
                assertTrue(surveyOpened)
                if (width >= 600) onNodeWithContentDescription("No selfie").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `entry menu actions and close remain available in compact and wide layouts`() {
        for (width in listOf(360, 600)) {
            runDesktopComposeUiTest(width = width, height = 800) {
                var edited = false
                var deleted = false
                var dismissed = false
                var laundryDays = 0
                val actions =
                    OutfitDetailActions(
                        onDismiss = { dismissed = true },
                        onEdit = { edited = it == outfit },
                        onDelete = { deleted = it == outfit },
                        onSurvey = {},
                        onMarkLaundry = { _, days -> laundryDays = days },
                    )
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme { OutfitDetailSheet(outfit, actions) }
                    }
                }

                onNodeWithContentDescription("More options").performClick()
                onNodeWithText("Edit").performClick()
                assertTrue(edited)
                onNodeWithContentDescription("More options").performClick()
                onNodeWithText("Mark outfit in laundry").performClick()
                onNodeWithText("For 1 day").performClick()
                assertEquals(1, laundryDays)
                onNodeWithContentDescription("More options").performClick()
                onNodeWithText("Delete entry").performClick()
                assertTrue(!deleted)
                onNodeWithText("Delete").performClick()
                assertTrue(deleted)
                onNodeWithContentDescription("Close").performClick()
                assertTrue(dismissed)
            }
        }
    }

    @Test
    fun `laundry notice is shown only while the until time is in the future`() {
        val now = Clock.System.now().toEpochMilliseconds()
        for ((until, visible) in listOf(0L to false, 1L to false, now to false, now + 86_400_000L to true)) {
            runDesktopComposeUiTest(width = 360, height = 800) {
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme {
                            OutfitDetailSheet(
                                outfit.copy(entry = outfit.entry.copy(laundryUntilEpochMs = until)),
                                OutfitDetailActions({}, {}, {}, {}, { _, _ -> }),
                            )
                        }
                    }
                }
                val notice = onNodeWithText("In laundry until", substring = true)
                if (visible) {
                    notice.performScrollTo().assertIsDisplayed()
                } else {
                    notice.assertDoesNotExist()
                }
            }
        }
    }
}
