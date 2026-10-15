package nl.petrichor.app.ui.wardrobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingImageStorage
import nl.petrichor.app.data.wardrobe.WardrobeRepository

import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.platform.BackHandler

/**
 * The main wardrobe screen showing clothing items in a 2x2 grid
 */
class WardrobeViewModel : ViewModel() {
    private val repository = WardrobeRepository()
    val allClothing = repository.getAllClothing()
    val clothingTypes = repository.getClothingTypes()

    private val _snackbarMessage = mutableStateOf<String?>(null)
    val snackbarMessage: String? get() = _snackbarMessage.value

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    suspend fun addClothing(clothing: ClothingPiece): Boolean {
        return try {
            repository.addClothing(clothing)
            showSnackbar("Clothing item added!")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not add clothing item: ${exception.message ?: "Unknown error"}")
            false
        }
    }

    fun deleteClothing(clothing: ClothingPiece) = viewModelScope.launch {
        try {
            repository.deleteClothing(clothing)
            ClothingImageStorage.deleteImage(clothing.imagePath)
            showSnackbar("Clothing item deleted!")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not delete clothing item: ${exception.message ?: "Unknown error"}")
        }
    }

    suspend fun updateClothing(previous: ClothingPiece, clothing: ClothingPiece): Boolean {
        return try {
            repository.updateClothing(clothing)
            if (previous.imagePath != clothing.imagePath) {
                ClothingImageStorage.deleteImage(previous.imagePath)
            }
            showSnackbar("Clothing item updated!")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not update clothing item: ${exception.message ?: "Unknown error"}")
            false
        }
    }
}

@Composable
fun WardrobeScreen(
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        BoxMessage(
            message = "Wardrobe preview",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val viewModel = remember { WardrobeViewModel() }
    val allClothing by viewModel.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by viewModel.clothingTypes.collectAsStateWithLifecycle(emptyList())
    var selectedTypeId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedClothingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedClothing = allClothing.firstOrNull { it.id == selectedClothingId }
    var closingClothing by remember { mutableStateOf<ClothingPiece?>(null) }
    var showDetailDialog by rememberSaveable { mutableStateOf(false) }
    var animateDetail by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val detailTransition = remember(selectedClothingId) {
        MutableTransitionState(!animateDetail)
    }
    detailTransition.targetState = showDetailDialog && selectedClothing != null
    LaunchedEffect(
        detailTransition.currentState,
        detailTransition.isIdle,
        selectedClothingId,
        showDetailDialog
    ) {
        if (!detailTransition.isIdle || selectedClothingId == null) {
            return@LaunchedEffect
        }

        if (detailTransition.currentState) {
            animateDetail = false
        } else if (!showDetailDialog) {
            selectedClothingId = null
            closingClothing = null
        }
    }
    // Show snackbar messages
    viewModel.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BackHandler(
            enabled = showDetailDialog && !showEditDialog,
            onBack = {
                showDetailDialog = false
            }
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!showDetailDialog) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true }
                    ) {
                        MaterialSymbol(
                            iconName = MaterialSymbols.ADD,
                            contentDescription = "Add clothing"
                        )
                    }
                }
            }
        ) { paddingValues ->
            val filteredClothing = if (selectedTypeId == null) {
                allClothing
            } else {
                allClothing.filter { it.typeId == selectedTypeId }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WardrobeFilterBar(
                        clothingTypes = clothingTypes,
                        onTypeSelected = { typeId ->
                            selectedTypeId = typeId
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }

                if (allClothing.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BoxMessage(
                            message = "Your wardrobe is empty. Add your first clothing item!",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (filteredClothing.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BoxMessage(
                            message = "No items found for this type.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(filteredClothing) { clothing ->
                        ClothingCard(
                            clothing = clothing,
                            onClick = {
                                selectedClothingId = clothing.id
                                animateDetail = true
                                showDetailDialog = true
                            },
                            modifier = Modifier.aspectRatio(1f)
                        )
                    }
                }
            }
        }

        (selectedClothing ?: closingClothing)?.let { clothing ->
            clothingTypes.firstOrNull { it.id == clothing.typeId }?.let { clothingType ->
                AnimatedVisibility(
                    visibleState = detailTransition,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight }
                    ) + fadeOut()
                ) {
                    ClothingDetailScreen(
                        clothing = clothing,
                        clothingType = clothingType,
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        onDismiss = { showDetailDialog = false },
                        onEdit = { showEditDialog = true },
                        onDelete = { deletedClothing ->
                            closingClothing = deletedClothing
                            viewModel.deleteClothing(deletedClothing)
                            showDetailDialog = false
                        }
                    )
                }
            }
        }

        // Shared add/edit clothing dialog
        if (showAddDialog) {
            AddClothingDialog(
                clothing = null,
                clothingTypes = clothingTypes,
                onDismiss = { showAddDialog = false },
                onSave = { clothing ->
                    if (viewModel.addClothing(clothing)) {
                        showAddDialog = false
                    }
                }
            )
        }

        if (showEditDialog && selectedClothing != null) {
            AddClothingDialog(
                clothing = selectedClothing,
                clothingTypes = clothingTypes,
                onDismiss = {
                    showEditDialog = false
                },
                onSave = { updatedClothing ->
                    if (viewModel.updateClothing(selectedClothing, updatedClothing)) {
                        showEditDialog = false
                    }
                }
            )
        }
    }
}