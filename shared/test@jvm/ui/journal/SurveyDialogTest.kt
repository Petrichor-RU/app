package nl.petrichor.app.ui.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SurveyDialogTest {
    private val today = LocalDate(2026, 6, 10)

    private fun unsurveyedEntry(id: Long = 1) =
        OutfitEntryWithPieces(
            OutfitEntry(
                id = id,
                wornOnDate = today,
                photoPath = null,
                notes = null,
                temperatureC = 15.0,
                feelsLikeC = null,
                minTempC = null,
                maxTempC = null,
                weatherDescription = null,
                weatherIcon = null,
                isRainy = false,
                surveyFeelingScore = null,
                surveyWarmthVerdict = null,
                surveyedAtEpochMs = null,
            ),
            emptyList(),
        )

    @Test
    fun `submit stays disabled until both questions are answered and then reports both`() =
        runDesktopComposeUiTest {
            var submitted: Triple<Long, Int, WarmthVerdict>? = null
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SurveyDialog(
                            entry = unsurveyedEntry(),
                            onDismiss = {},
                            onSubmit = { entryId, score, verdict ->
                                submitted = Triple(entryId, score, verdict)
                                true
                            },
                            onLogOutfitFirst = {},
                        )
                    }
                }
            }

            onNodeWithText("Submit").assertIsNotEnabled()
            // Move the 1-5 slider to 4 via its SetProgress semantics action.
            onNodeWithTag("survey_score_slider").performSemanticsAction(SemanticsActions.SetProgress) { set -> set(4f) }
            onNodeWithText("Submit").assertIsNotEnabled()
            onNodeWithText("Perfect").performClick()
            onNodeWithText("Submit").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(Triple(1L, 4, WarmthVerdict.PERFECT), submitted)
        }

    @Test
    fun `empty state offers to log an outfit first and has no submit`() =
        runDesktopComposeUiTest {
            var logFirst = false
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SurveyDialog(
                            entry = null,
                            onDismiss = {},
                            onSubmit = { _, _, _ -> true },
                            onLogOutfitFirst = { logFirst = true },
                        )
                    }
                }
            }

            onNodeWithText("Submit").assertDoesNotExist()
            onNodeWithText("Log outfit first").assertIsDisplayed().performClick()
            waitForIdle()

            assertTrue(logFirst)
            assertNull(null)
        }
}
