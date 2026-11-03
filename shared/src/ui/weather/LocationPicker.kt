package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.location.LocationResult
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.ui.BoxMessage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LocationPicker(
    onLocationSelected: (Place) -> Unit,
    onCurrentLocationSelected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationPickerViewModel = viewModel { LocationPickerViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedKeys = remember(state.saved) { state.saved.mapTo(HashSet()) { it.key } }
    val haptics = LocalHapticFeedback.current

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a city or area") },
            leadingIcon = { MaterialSymbol(iconName = MaterialSymbols.SEARCH, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) {
                        MaterialSymbol(iconName = MaterialSymbols.CLOSE, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (state.query.isBlank()) {
                item(key = "current-location") {
                    LocationListItem(
                        title = "Use current location",
                        icon = MaterialSymbols.MY_LOCATION,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onCurrentLocationSelected,
                        modifier = Modifier.animateItem(),
                    )
                }

                savedLocations(
                    saved = state.saved,
                    reorderState = reorderState,
                    haptics = haptics,
                    onSelect = { location -> onLocationSelected(location.toPlace()) },
                    onRemove = viewModel::remove,
                    onDragStopped = viewModel::commitOrder,
                )
            } else {
                searchResults(
                    search = state.search,
                    savedKeys = savedKeys,
                    onSelect = { location ->
                        viewModel.search("")
                        onLocationSelected(location.toPlace())
                    },
                    onAdd = viewModel::add,
                    onRemove = viewModel::remove,
                )
            }
        }
    }
}

private fun LazyListScope.savedLocations(
    saved: List<LocationResult>,
    reorderState: ReorderableLazyListState,
    haptics: HapticFeedback,
    onSelect: (LocationResult) -> Unit,
    onRemove: (LocationResult) -> Unit,
    onDragStopped: () -> Unit,
) {
    item(key = "saved-header") {
        Text(
            text = "Saved locations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .animateItem()
                .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
    }

    if (saved.isEmpty()) {
        item(key = "saved-empty") {
            BoxMessage("Search for a city above to save it here.", Modifier.animateItem())
        }
    }

    items(saved, key = { it.key }) { location ->
        ReorderableItem(reorderState, key = location.key) { isDragging ->
            LocationListItem(
                title = location.name,
                subtitle = location.region.takeIf { region -> region.isNotBlank() },
                highlighted = isDragging,
                onClick = { onSelect(location) },
                modifier = Modifier.longPressDraggableHandle(
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStopped = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        onDragStopped()
                    },
                ),
                trailingAction = {
                    IconButton(onClick = { onRemove(location) }) {
                        MaterialSymbol(iconName = MaterialSymbols.CLOSE, contentDescription = "Remove ${location.name}")
                    }
                }
            )
        }
    }
}

private fun LazyListScope.searchResults(
    search: LocationSearchState,
    savedKeys: Set<String>,
    onSelect: (LocationResult) -> Unit,
    onAdd: (LocationResult) -> Unit,
    onRemove: (LocationResult) -> Unit,
) {
    when (search) {
        LocationSearchState.Idle -> Unit
        LocationSearchState.Loading -> item(key = "search-loading") {
            Column(
                Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Searching locations...")
            }
        }
        is LocationSearchState.Error -> item(key = "search-error") {
            BoxMessage(search.message, Modifier.animateItem())
        }
        is LocationSearchState.Ready -> {
            if (search.results.isEmpty()) {
                item(key = "search-empty") { BoxMessage("No locations found.", Modifier.animateItem()) }
            }
            items(search.results, key = { result -> "result-${result.key}" }) { location ->
                val isSaved = location.key in savedKeys

                LocationListItem(
                    title = location.name,
                    subtitle = location.region.takeIf { region -> region.isNotBlank() },
                    iconTint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSelect(location) },
                    modifier = Modifier.animateItem(),
                    trailingAction = {
                        IconButton(
                            onClick = {
                                if (isSaved) onRemove(location) else onAdd(location)
                            }
                        ) {
                            MaterialSymbol(
                                iconName = if (isSaved) MaterialSymbols.CHECK else MaterialSymbols.ADD,
                                contentDescription = if (isSaved) "Remove ${location.name} from saved" else "Save ${location.name}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                )
            }
        }
    }
}
