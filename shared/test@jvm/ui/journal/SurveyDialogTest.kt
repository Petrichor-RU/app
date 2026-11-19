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
import nl.petrichor.app.data.outfit.SurveyContextAnswers
import nl.petrichor.app.data.outfit.SurveyContextQuestion
import nl.petrichor.app.data.outfit.WarmthVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class SubmittedSurvey(
    val entryId: Long,
    val score: Int,
    val verdict: WarmthVerdict,
    val context: SurveyContextAnswers,
)

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
            var submitted: SubmittedSurvey? = null
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SurveyDialog(
                            entry = unsurveyedEntry(),
                            onDismiss = {},
                            onSubmit = { entryId, score, verdict, context ->
                                submitted = SubmittedSurvey(entryId, score, verdict, context)
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
            onNodeWithTag("survey_followup_prompt").assertDoesNotExist()
            onNodeWithText("Submit").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(SubmittedSurvey(1L, 4, WarmthVerdict.PERFECT, SurveyContextAnswers()), submitted)
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
                            onSubmit = { _, _, _, _ -> true },
                            onLogOutfitFirst = { logFirst = true },
                        )
                    }
                }
            }

            onNodeWithText("Submit").assertDoesNotExist()
            onNodeWithText("Log outfit first").assertIsDisplayed().performClick()
            waitForIdle()

            assertTrue(logFirst)
        }

    @Test
    fun `optional follow-up prompt renders when requested and submit remains enabled without answering it`() =
        runDesktopComposeUiTest {
            var submitted: SubmittedSurvey? = null
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SurveyDialog(
                            entry = unsurveyedEntry(),
                            onDismiss = {},
                            onSubmit = { entryId, score, verdict, context ->
                                submitted = SubmittedSurvey(entryId, score, verdict, context)
                                true
                            },
                            onLogOutfitFirst = {},
                            selectFollowUpQuestion = { _, _ -> SurveyContextQuestion.GOT_WET },
                        )
                    }
                }
            }

            onNodeWithTag("survey_followup_prompt").assertIsDisplayed()
            onNodeWithText(SurveyContextQuestion.GOT_WET.prompt).assertIsDisplayed()
            onNodeWithText("Got wet").assertIsDisplayed()
            onNodeWithText("Stayed dry").assertIsDisplayed()

            onNodeWithTag("survey_score_slider").performSemanticsAction(SemanticsActions.SetProgress) { set -> set(5f) }
            onNodeWithText("Perfect").performClick()
            // Submit stays enabled even though neither optional follow-up chip was clicked.
            onNodeWithText("Submit").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(SubmittedSurvey(1L, 5, WarmthVerdict.PERFECT, SurveyContextAnswers()), submitted)
        }

    @Test
    fun `optional follow-up chip toggles selection cleanly and records answer when chosen`() =
        runDesktopComposeUiTest {
            var submitted: SubmittedSurvey? = null
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SurveyDialog(
                            entry = unsurveyedEntry(),
                            onDismiss = {},
                            onSubmit = { entryId, score, verdict, context ->
                                submitted = SubmittedSurvey(entryId, score, verdict, context)
                                true
                            },
                            onLogOutfitFirst = {},
                            selectFollowUpQuestion = { _, _ -> SurveyContextQuestion.MOSTLY_INDOORS },
                        )
                    }
                }
            }

            onNodeWithTag("survey_score_slider").performSemanticsAction(SemanticsActions.SetProgress) { set -> set(4f) }
            onNodeWithText("Too warm").performClick()
            onNodeWithTag("survey_followup_positive").performClick()
            onNodeWithText("Submit").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(
                SubmittedSurvey(1L, 4, WarmthVerdict.TOO_WARM, SurveyContextAnswers(mostlyIndoors = true)),
                submitted,
            )
        }
}
