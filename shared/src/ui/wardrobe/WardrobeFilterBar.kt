package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingType

/**
 * Filter bar component for filtering wardrobe items by clothing type
 */
@Composable
fun WardrobeFilterBar(
    selectedType: ClothingType?,
    onTypeSelected: (ClothingType?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        MaterialSymbolsRenderingScope {
            IconButton(onClick = { showMenu = true }) {
                MaterialSymbol(
                    iconName = MaterialSymbols.FILTER_LIST,
                    contentDescription = "Filter"
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // All Types option
                DropdownMenuItem(
                    text = { Text("All Types") },
                    onClick = {
                        onTypeSelected(null)
                        showMenu = false
                    }
                )

                // Individual types
                ClothingType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            onTypeSelected(type)
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}