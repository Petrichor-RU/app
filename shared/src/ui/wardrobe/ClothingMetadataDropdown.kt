package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols

/**
 * Reusable labelled dropdown row used by [AddClothingDialog] for clothing type, material,
 * and optional weather-protection selection.
 */
@Composable
internal fun <T> ClothingMetadataDropdown(
    label: String,
    selectedText: String,
    iconContentDescription: String,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Text(
                text = selectedText,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { expanded = true }) {
                MaterialSymbol(
                    iconName = MaterialSymbols.ARROW_DROP_DOWN,
                    contentDescription = iconContentDescription,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, displayText) ->
                    DropdownMenuItem(
                        text = { Text(displayText) },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
