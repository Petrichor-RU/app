package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.launch
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository

import nl.petrichor.app.ui.BoxMessage

/**
 * The main wardrobe screen showing clothing items in a 2x2 grid
 */
class WardrobeViewModel : ViewModel() {
    private val repository = WardrobeRepository()
    val allClothing = repository.getAllClothing()
    
    private val _snackbarMessage = mutableStateOf<String?>(null)
    val snackbarMessage: String? get() = _snackbarMessage.value
    
    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }
    
    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
    
    fun addClothing(clothing: ClothingPiece) = viewModelScope.launch {
        repository.addClothing(clothing)
        showSnackbar("Clothing item added!")
    }

    fun deleteClothing(clothing: ClothingPiece) = viewModelScope.launch {
        repository.deleteClothing(clothing)
        showSnackbar("Clothing item deleted!")
    }
}

@Composable
fun WardrobeScreen(
    modifier: Modifier = Modifier
) {
    val viewModel = remember { WardrobeViewModel() }
    val allClothing by viewModel.allClothing.collectAsStateWithLifecycle(emptyList())
    var selectedType by remember { mutableStateOf<ClothingType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Show snackbar messages
    viewModel.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                MaterialSymbolsRenderingScope {
                    MaterialSymbol(
                        iconName = MaterialSymbols.ADD,
                        contentDescription = "Add clothing"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter bar
            WardrobeFilterBar(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )

            // Clothing grid
            if (allClothing.isEmpty()) {
                BoxMessage(
                    message = "Your wardrobe is empty. Add your first clothing item!",
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                val filteredClothing = if (selectedType == null) {
                    allClothing
                } else {
                    allClothing.filter { it.type == selectedType }
                }

                if (filteredClothing.isEmpty()) {
                    BoxMessage(
                        message = "No items found for this type.",
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredClothing) { clothing ->
                            ClothingCard(
                                clothing = clothing,
                                onClick = {
                                    // TODO: Show detail/edit dialog
                                },
                                modifier = Modifier.aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }

        // Add clothing dialog
        if (showAddDialog) {
            AddClothingDialog(
                onDismiss = { showAddDialog = false },
                onSave = { clothing ->
                    viewModel.addClothing(clothing)
                    showAddDialog = false
                }
            )
        }
    }
}