package nl.petrichor.app.ui.weather

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlin.test.Test

class LocationHeaderTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `header shows the location and toggles the picker`() =
        runDesktopComposeUiTest {
            var pickerOpen by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationHeader(
                            locationName = "Amsterdam",
                            pickerOpen = pickerOpen,
                            onTogglePicker = { pickerOpen = !pickerOpen },
                        )
                    }
                }
            }

            onNodeWithText("Amsterdam").assertIsDisplayed()
            onNodeWithContentDescription("Search locations").performClick()
            waitForIdle()
            onNodeWithContentDescription("Close location picker").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `header animates to a newly selected location`() =
        runDesktopComposeUiTest {
            var locationName by mutableStateOf("Amsterdam")
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        LocationHeader(locationName = locationName, pickerOpen = false, onTogglePicker = {})
                    }
                }
            }

            locationName = "Groningen"
            waitForIdle()

            onNodeWithText("Groningen").assertIsDisplayed()
        }
}
