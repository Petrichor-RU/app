package nl.petrichor.app.ui.recommendation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.recommendation.OutfitCandidate
import nl.petrichor.app.data.recommendation.RecommendationSource
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecommendationContentTest {
    private val today = LocalDate(2026, 6, 10)
    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 12, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val capType = ClothingType(id = 14, name = "Caps", supertype = ClothingSupertype.HEADWEAR)
    private val sneakerType = ClothingType(id = 15, name = "Sneakers", supertype = ClothingSupertype.FOOTWEAR)
    private val types = listOf(teeType, jeansType, capType, sneakerType)

    private fun piece(
        id: Long,
        name: String,
        typeId: Long,
    ) = ClothingPiece(
        id = id,
        name = name,
        typeId = typeId,
        material = Material.COTTON,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    @Test
    fun `opens scrolled below the calendar and shows the first candidate's groups`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            val candidates =
                listOf(
                    OutfitCandidate(
                        loggedEntry = null,
                        pieces =
                            listOf(
                                piece(1, "Tee", 10),
                                piece(2, "Jeans", 12),
                                piece(5, "Cap", 14),
                                piece(6, "Sneakers", 15),
                            ),
                        selfiePath = null,
                        generalSuggestion = null,
                        source = RecommendationSource.HEURISTIC,
                    ),
                    OutfitCandidate(null, listOf(piece(3, "Other tee", 10)), null, null, RecommendationSource.HEURISTIC),
                    OutfitCandidate(null, emptyList(), null, "T-shirt or shirt", RecommendationSource.FALLBACK),
                )
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        RecommendationContent(
                            uiState =
                                RecommendationUiState(
                                    locationName = "Amsterdam",
                                    weather = null,
                                    today = today,
                                    candidates = candidates,
                                    clothingTypes = types,
                                    laundryVersion = 0,
                                ),
                            onMarkPieceLaundry = { _, _ -> },
                        )
                    }
                }
            }
            waitForIdle()

            // The calendar pager is scrolled out of view on open.
            onNodeWithTag("calendar_pager").assertIsNotDisplayed()
            // First candidate's dynamic groups are visible below the calendar.
            onNodeWithText("Headwear").assertIsDisplayed()
            onNodeWithText("Upper body").assertIsDisplayed()
            onNodeWithText("Tee").assertIsDisplayed()
            // The laundry badge sits on the card itself in the recommendation screen.
            onNodeWithContentDescription("Mark Tee in laundry").assertIsDisplayed()
        }

    @Test
    fun `fallback suggestion text is shown for a suggestion-only candidate`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            val candidates =
                listOf(
                    OutfitCandidate(null, emptyList(), null, "Sweater and long pants", RecommendationSource.FALLBACK),
                )
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        RecommendationContent(
                            uiState =
                                RecommendationUiState(
                                    locationName = "Amsterdam",
                                    weather = null,
                                    today = today,
                                    candidates = candidates,
                                    clothingTypes = types,
                                    laundryVersion = 0,
                                ),
                            onMarkPieceLaundry = { _, _ -> },
                        )
                    }
                }
            }
            waitForIdle()

            onNodeWithText("Sweater and long pants").assertIsDisplayed()
        }

    @Test
    fun `calendar top bar stays hidden at the top until a pull-down reveals it`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            // Tall candidate so the list opens scrolled below the calendar and can scroll up to it.
            val candidates =
                listOf(
                    OutfitCandidate(
                        null,
                        listOf(piece(1, "Tee", 10), piece(2, "Jeans", 12), piece(5, "Cap", 14), piece(6, "Sneakers", 15)),
                        null,
                        null,
                        RecommendationSource.HEURISTIC,
                    ),
                )
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        RecommendationContent(
                            uiState =
                                RecommendationUiState(
                                    locationName = "Amsterdam",
                                    weather = null,
                                    today = today,
                                    candidates = candidates,
                                    clothingTypes = types,
                                    laundryVersion = 0,
                                ),
                            onMarkPieceLaundry = { _, _ -> },
                        )
                    }
                }
            }
            waitForIdle()

            // Small downward swipes scroll up until the calendar sits at the very top of the list;
            // each swipe stays below the reveal threshold, so the overshoot cannot reveal the top bar.
            var swipes = 0
            while (swipes < 20) {
                val pagerTop =
                    onAllNodesWithTag("calendar_pager")
                        .fetchSemanticsNodes()
                        .firstOrNull()
                        ?.positionInRoot
                        ?.y
                if (pagerTop != null && pagerTop >= 30f) break
                onNodeWithTag("recommendation_list").performTouchInput { swipe(center, center + Offset(0f, 30f)) }
                waitForIdle()
                swipes++
            }
            // One more small swipe settles the list at the very top.
            onNodeWithTag("recommendation_list").performTouchInput { swipe(center, center + Offset(0f, 30f)) }
            waitForIdle()
            onNodeWithTag("calendar_pager").assertIsDisplayed()
            assertEquals(0, onAllNodesWithTag("calendar_top_bar").fetchSemanticsNodes().size)

            // A deliberate pull-down at the very top reveals the top bar.
            repeat(4) {
                onNodeWithTag("recommendation_list").performTouchInput { swipe(center, center + Offset(0f, 30f)) }
                waitForIdle()
            }
            onNodeWithTag("calendar_top_bar").assertIsDisplayed()
        }
}
