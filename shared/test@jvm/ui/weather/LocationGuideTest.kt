package nl.petrichor.app.ui.weather

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.guide.MonthClimate
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class LocationGuideTest {
    // A cold profile: every season triggers the deep-cold advice set.
    private val coldProfile =
        ClimateProfile(
            place = Place("Helsinki", Coordinates(60.17, 24.94)),
            months = (1..12).map { MonthClimate(it, avgHigh = -5.0, avgLow = -12.0, precipitationMm = 40.0, humidity = 0) },
        )

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `header stays visible even while no climate profile has arrived and nothing is loading`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationGuide(profile = null, loading = false, expanded = false, onToggleExpanded = {})
                    }
                }
            }
            waitForIdle()

            // The section has no visibility switches: the header is always on screen.
            onNodeWithText("Location guide").assertIsDisplayed()
            onNodeWithContentDescription("Expand location guide").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `expanded section says so when no climate data is available`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationGuide(profile = null, loading = false, expanded = true, onToggleExpanded = {})
                    }
                }
            }
            waitForIdle()

            onNodeWithText("Location guide").assertIsDisplayed()
            onNodeWithText("No climate data available for this location yet.").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `card stays visible with a spinner instead of the chevron while the next profile loads`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationGuide(profile = null, loading = true, expanded = false, onToggleExpanded = {})
                    }
                }
            }
            waitForIdle()

            onNodeWithText("Location guide").assertIsDisplayed()
            onNodeWithContentDescription("Loading location guide").assertIsDisplayed()
            onNodeWithContentDescription("Expand location guide").assertDoesNotExist()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `previous profile stays on screen while the new one is loading`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationGuide(profile = coldProfile, loading = true, expanded = true, onToggleExpanded = {})
                    }
                }
            }
            waitForIdle()

            // Old content still there, spinner in the header, chevron replaced.
            onNodeWithText("Winter").assertIsDisplayed()
            onNodeWithText("January").assertIsDisplayed()
            onNodeWithContentDescription("Loading location guide").assertIsDisplayed()
            onNodeWithContentDescription("Collapse location guide").assertDoesNotExist()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `header appears with the profile and a tap expands and collapses the guide`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            var expanded by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationGuide(
                            profile = coldProfile,
                            expanded = expanded,
                            onToggleExpanded = { expanded = !expanded },
                        )
                    }
                }
            }
            waitForIdle()

            // Folded by default: header visible, season cards not yet.
            onNodeWithText("Location guide").assertIsDisplayed()
            onNodeWithText("Winter").assertDoesNotExist()

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            // The advice holding for all four seasons gets a General section above the season cards.
            onNodeWithText("General").assertIsDisplayed()
            onNodeWithText("Winter").assertIsDisplayed()
            onNodeWithText("Autumn").assertIsDisplayed()
            // The monthly bar graph renders the month labels (January left, July at the seventh
            // bar, December right) and the legend with the profile's max (-5°)/min (-12°) temps.
            onNodeWithText("January").assertIsDisplayed()
            onNodeWithText("July").assertIsDisplayed()
            onNodeWithText("December").assertIsDisplayed()
            onNodeWithText("-5°").assertIsDisplayed()
            onNodeWithText("-12°").assertIsDisplayed()
            // "July" sits at the left edge of the seventh of 12 equal cells, i.e. the row's midpoint.
            val janX = onNodeWithText("January").fetchSemanticsNode().positionInRoot.x
            val julX = onNodeWithText("July").fetchSemanticsNode().positionInRoot.x
            val decNode = onNodeWithText("December").fetchSemanticsNode()
            val span = decNode.positionInRoot.x + decNode.size.width - janX
            assertTrue(abs(julX - (janX + span / 2f)) < span * 0.05f, "July at $julX, row midpoint ${janX + span / 2f}")
            // The corner labels must not wrap mid-word: each is a single line, like the legend text.
            val legendHeight = onNodeWithText("-5°").fetchSemanticsNode().size.height
            assertTrue(
                onNodeWithText("January").fetchSemanticsNode().size.height <= legendHeight + 2,
                "January wrapped to multiple lines",
            )
            assertTrue(
                decNode.size.height <= legendHeight + 2,
                "December wrapped to multiple lines",
            )
            // December is right-aligned across the whole label row (overflowing left if needed),
            // not squeezed into the narrow last bar cell.
            assertTrue(
                decNode.size.width > span * 0.8f,
                "December node width ${decNode.size.width} should span the row ($span), not the last cell",
            )
            // The cold profile earns the same advice in all four seasons, so it shows once, in
            // the General card only — the season cards do not repeat it.
            onAllNodesWithText("Take a thick jacket with you.").assertCountEquals(1)
            // Rain moved out of the graph into the four season card headers (40 mm each).
            onAllNodesWithContentDescription("Rain, 40 mm").assertCountEquals(4)

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            onNodeWithText("Winter").assertDoesNotExist()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `graph and rain indicators still render in a dark scheme`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        LocationGuide(profile = coldProfile, expanded = true, onToggleExpanded = {})
                    }
                }
            }
            waitForIdle()

            onNodeWithText("January").assertIsDisplayed()
            onNodeWithText("July").assertIsDisplayed()
            onNodeWithText("-5°").assertIsDisplayed()
            onAllNodesWithContentDescription("Rain, 40 mm").assertCountEquals(4)
        }
}
