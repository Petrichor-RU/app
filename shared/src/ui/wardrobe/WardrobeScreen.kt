@file:Suppress("MatchingDeclarationName") // hosts the screen and its view model per project convention

package nl.petrichor.app.ui.wardrobe

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.petrichor.app.data.affiliate.AffiliateFeed
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.platform.PlatformBrowser
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.background.AppWeatherState
import nl.petrichor.app.ui.wardrobe.affiliate.AffiliateDetailOverlay
import nl.petrichor.app.ui.wardrobe.affiliate.AffiliateViewModel
import nl.petrichor.app.ui.wardrobe.affiliate.filterSponsoredByType
import nl.petrichor.app.ui.wardrobe.affiliate.mergeSponsoredIntoGrid

/**
 * The main wardrobe screen showing clothing items in an adaptive grid
 */
@Suppress("TooGenericExceptionCaught") // repository failures are surfaced as snackbars, so any exception is handled
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

    suspend fun addClothing(clothing: ClothingPiece): Boolean =
        try {
            repository.addClothing(clothing)
            showSnackbar("Clothing item added!")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not add clothing item: ${exception.message ?: "Unknown error"}")
            false
        }

    fun markLaundry(
        pieceId: Long,
        days: Int,
    ) = viewModelScope.launch {
        try {
            repository.markLaundry(pieceId, days)
            showSnackbar("Marked in laundry for $days day${if (days == 1) "" else "s"}.")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not mark laundry: ${exception.message ?: "Unknown error"}")
        }
    }

    fun deleteClothing(clothing: ClothingPiece) =
        viewModelScope.launch {
            try {
                // Linked pieces are archived silently (image kept for the journal);
                // unlinked pieces are hard-deleted with their image inside the repository.
                repository.deletePieceWithArchive(clothing)
                showSnackbar("Clothing item deleted!")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showSnackbar("Could not delete clothing item: ${exception.message ?: "Unknown error"}")
            }
        }

    suspend fun updateClothing(
        previous: ClothingPiece,
        clothing: ClothingPiece,
    ): Boolean =
        try {
            repository.updateClothing(clothing)
            if (previous.imagePath != clothing.imagePath) {
                MediaStorage.deleteImage(previous.imagePath)
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

/**
 * The main wardrobe screen showing clothing items in an adaptive grid. Owns all wardrobe state
 * per project convention and wires the pieces: [WardrobeContent] renders the grid, the detail
 * overlays and [WardrobeDialogs] render on top of it.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // screen owns all wardrobe state and orchestrates the pieces per project convention
fun WardrobeScreen(
    weatherState: AppWeatherState = AppWeatherState(),
    affiliateFeed: AffiliateFeed = AffiliateFeed(),
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        BoxMessage(
            message = "Wardrobe preview",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val viewModel = viewModel { WardrobeViewModel() }
    // The feed is shared with the app shell, which pre-warms its cache on app load.
    val affiliateViewModel = viewModel { AffiliateViewModel(feed = affiliateFeed) }
    val allClothing by viewModel.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by viewModel.clothingTypes.collectAsStateWithLifecycle(emptyList())
    val sponsoredItems by affiliateViewModel.items.collectAsStateWithLifecycle(emptyList())

    var selectedTypeId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedClothingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedClothing = allClothing.firstOrNull { it.id == selectedClothingId }
    var closingClothing by remember { mutableStateOf<ClothingPiece?>(null) }
    var showDetailDialog by rememberSaveable { mutableStateOf(false) }
    var animateDetail by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // Sponsored (affiliate) detail state, mirroring the clothing detail below. The selection
    // is the item's id (its url — saveable); an AffiliateItem itself is not Bundle-storable.
    var selectedAffiliateId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAffiliate = sponsoredItems.firstOrNull { it.id == selectedAffiliateId }
    // Captured when the detail opens, so a feed refresh mid-open/close cannot make the overlay
    // vanish before its exit animation finishes (mirrors closingClothing for the clothing detail).
    var closingAffiliate by remember { mutableStateOf<AffiliateItem?>(null) }
    val displayedAffiliate = selectedAffiliate ?: closingAffiliate
    var showAffiliateDetail by rememberSaveable { mutableStateOf(false) }
    var animateAffiliateDetail by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Detail overlay transitions. A selection is only cleared once its exit transition has
    // finished, so the closing animation plays in full.
    val detailTransition =
        remember(selectedClothingId) {
            MutableTransitionState(!animateDetail)
        }
    detailTransition.targetState = showDetailDialog && selectedClothing != null
    LaunchedEffect(
        detailTransition.currentState,
        detailTransition.isIdle,
        selectedClothingId,
        showDetailDialog,
    ) {
        settleOverlayTransition(
            transition = detailTransition,
            hasSelection = selectedClothingId != null,
            shown = showDetailDialog,
            onShown = { animateDetail = false },
            onDismissed = {
                selectedClothingId = null
                closingClothing = null
            },
        )
    }
    val affiliateTransition =
        remember(selectedAffiliateId) {
            MutableTransitionState(!animateAffiliateDetail)
        }
    affiliateTransition.targetState = showAffiliateDetail && displayedAffiliate != null
    LaunchedEffect(
        affiliateTransition.currentState,
        affiliateTransition.isIdle,
        selectedAffiliateId,
        showAffiliateDetail,
    ) {
        settleOverlayTransition(
            transition = affiliateTransition,
            hasSelection = selectedAffiliateId != null,
            shown = showAffiliateDetail,
            onShown = { animateAffiliateDetail = false },
            onDismissed = {
                selectedAffiliateId = null
                closingAffiliate = null
            },
        )
    }
    // Fresh affiliate fetch on first composition and whenever the selected place or its
    // weather settle; nothing is persisted between launches.
    LaunchedEffect(weatherState.place, weatherState.weather) {
        // Only fetch once the weather snapshot exists: the shopping context comes from it, and a
        // null-weather fetch must not populate the feed cache (mirrors the app-shell pre-warm).
        val weather = weatherState.weather
        if (weather != null) {
            affiliateViewModel.refresh(weatherState.place, weather)
        }
    }
    // Show snackbar messages
    viewModel.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    // The grid shows the user's items filtered by the selected type, with sponsored cards
    // filtered like the user's own items (via their mapped type) and sprinkled throughout.
    // The merge is remembered so the grid does not reshuffle on unrelated recompositions.
    val filteredClothing = filterClothingByType(allClothing, selectedTypeId)
    val selectedType = clothingTypes.firstOrNull { it.id == selectedTypeId }
    val filteredSponsored = filterSponsoredByType(sponsoredItems, selectedType)
    val gridEntries =
        remember(filteredClothing, filteredSponsored) {
            mergeSponsoredIntoGrid(filteredClothing, filteredSponsored)
        }
    val emptyMessage =
        if (filteredClothing.isEmpty()) {
            if (allClothing.isEmpty()) {
                "Your wardrobe is empty. Add your first clothing item!"
            } else {
                "No items found for this type."
            }
        } else {
            null
        }

    Box(modifier = modifier.fillMaxSize()) {
        BackHandler(
            enabled = backHandlerEnabled(showDetailDialog, showEditDialog, showAffiliateDetail),
            onBack = {
                if (showAffiliateDetail) {
                    showAffiliateDetail = false
                } else {
                    showDetailDialog = false
                }
            },
        )

        WardrobeContent(
            snackbarHostState = snackbarHostState,
            clothingTypes = clothingTypes,
            gridEntries = gridEntries,
            emptyMessage = emptyMessage,
            showFab = !showDetailDialog && !showAffiliateDetail,
            onAddClick = { showAddDialog = true },
            onTypeSelected = { typeId -> selectedTypeId = typeId },
            onClothingClick = { clothingId ->
                selectedClothingId = clothingId
                animateDetail = true
                showDetailDialog = true
            },
            onSponsoredClick = { itemId ->
                closingAffiliate = sponsoredItems.firstOrNull { it.id == itemId }
                selectedAffiliateId = itemId
                animateAffiliateDetail = true
                showAffiliateDetail = true
            },
        )

        ClothingDetailOverlay(
            clothing = selectedClothing ?: closingClothing,
            clothingTypes = clothingTypes,
            transition = detailTransition,
            onDismiss = { showDetailDialog = false },
            onEdit = { showEditDialog = true },
            onDelete = { deletedClothing ->
                closingClothing = deletedClothing
                viewModel.deleteClothing(deletedClothing)
                showDetailDialog = false
            },
            onMarkLaundry = { piece, days -> viewModel.markLaundry(piece.id, days) },
        )

        AffiliateDetailOverlay(
            item = displayedAffiliate,
            transition = affiliateTransition,
            onDismiss = { showAffiliateDetail = false },
            onShop = { affiliate ->
                // The adapter already injected the affiliate code, so the url is ready to go.
                PlatformBrowser.open(affiliate.url)
            },
        )

        WardrobeDialogs(
            showAddDialog = showAddDialog,
            showEditDialog = showEditDialog,
            editingClothing = selectedClothing,
            clothingTypes = clothingTypes,
            onDismissAdd = { showAddDialog = false },
            onDismissEdit = { showEditDialog = false },
            onSaveAdd = { clothing ->
                if (viewModel.addClothing(clothing)) {
                    showAddDialog = false
                }
            },
            onSaveEdit = { updatedClothing ->
                selectedClothing?.let { current ->
                    if (viewModel.updateClothing(current, updatedClothing)) {
                        showEditDialog = false
                    }
                }
            },
        )
    }
}

/** Filters the wardrobe by the selected type; `null` means "All Types". */
private fun filterClothingByType(
    items: List<ClothingPiece>,
    selectedTypeId: Long?,
): List<ClothingPiece> = if (selectedTypeId == null) items else items.filter { it.typeId == selectedTypeId }

/**
 * Settles an overlay transition once it is idle: when the shown state has settled, the one-shot
 * entrance animation flag is cleared; when a hidden dialog's exit has finished, the selection
 * is cleared so the overlay stops rendering.
 */
private fun settleOverlayTransition(
    transition: MutableTransitionState<Boolean>,
    hasSelection: Boolean,
    shown: Boolean,
    onShown: () -> Unit,
    onDismissed: () -> Unit,
) {
    if (!transition.isIdle || !hasSelection) return
    if (transition.currentState) {
        onShown()
    } else if (!shown) {
        onDismissed()
    }
}

/** The back handler covers the detail overlays, except while the edit dialog is on top. */
private fun backHandlerEnabled(
    detailShown: Boolean,
    editShown: Boolean,
    affiliateDetailShown: Boolean,
): Boolean = (detailShown && !editShown) || affiliateDetailShown
