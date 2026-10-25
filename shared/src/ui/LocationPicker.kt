package nl.petrichor.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.location.LocationResult
import nl.petrichor.app.data.weather.Place
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LocationPicker(
    onLocationSelected: (Place) -> Unit,
    onCurrentLocationSelected: () -> Unit,
    modifier: Modifier = Modifier,
    controller: LocationPickerController = viewModel { LocationPickerController() },
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val savedKeys = remember(state.saved) { state.saved.mapTo(HashSet()) { it.key } }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = state.saved.indexOfFirst { it.key == from.key }
        val toIndex = state.saved.indexOfFirst { it.key == to.key }
        controller.move(fromIndex, toIndex)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = controller::search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a city or area") },
            leadingIcon = { MaterialSymbol(iconName = MaterialSymbols.SEARCH, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { controller.search("") }) {
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
                item {
                    ListItemRow(
                        title = "Use current location",
                        icon = MaterialSymbols.MY_LOCATION,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onCurrentLocationSelected,
                        modifier = Modifier
                            .padding(vertical = 2.dp, horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                savedLocations(
                    saved = state.saved,
                    reorderState = reorderState,
                    controller = controller,
                    onLocationSelected = onLocationSelected
                )
            } else {
                searchResults(
                    search = state.search,
                    savedKeys = savedKeys,
                    onSelect = { location ->
                        controller.search("")
                        onLocationSelected(location.toPlace())
                    },
                    onAdd = controller::add,
                    onRemove = controller::remove,
                )
            }
        }
    }
}

private fun LazyListScope.savedLocations(
    saved: List<LocationResult>,
    reorderState: ReorderableLazyListState,
    controller: LocationPickerController,
    onLocationSelected: (Place) -> Unit
) {
    item {
        Text(
            text = "Saved locations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
    }

    if (saved.isEmpty()) {
        item { BoxMessage("Search for a city above to save it here.") }
    }

    items(saved, key = { it.key }) { location ->
        ReorderableItem(reorderState, key = location.key) { isDragging ->
            val elevation by animateFloatAsState(if (isDragging) 8f else 0f, label = "drag_elevation")

            LocationListItem(
                location = location,
                elevation = elevation,
                onClick = { onLocationSelected(location.toPlace()) },
                modifier = Modifier.longPressDraggableHandle(
                    onDragStopped = { controller.commitOrder() }
                ),
                trailingAction = {
                    IconButton(onClick = { controller.remove(location) }) {
                        MaterialSymbol(iconName = MaterialSymbols.CLOSE, contentDescription = "Remove")
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
        LocationSearchState.Loading -> item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Searching locations...")
            }
        }
        is LocationSearchState.Error -> item { BoxMessage(search.message) }
        is LocationSearchState.Ready -> {
            if (search.results.isEmpty()) {
                item { BoxMessage("No locations found.") }
            }
            items(search.results, key = { result -> "result-${result.key}" }) { location ->
                val isSaved = location.key in savedKeys

                LocationListItem(
                    location = location,
                    icon = MaterialSymbols.LOCATION_ON,
                    iconTint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSelect(location) },
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

@Composable
private fun LocationListItem(
    location: LocationResult,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    elevation: Float = 0f,
    icon: String = MaterialSymbols.LOCATION_ON,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingAction: @Composable (() -> Unit)? = null
) {
    ListItemRow(
        title = location.name,
        subtitle = location.region.takeIf { region -> region.isNotBlank() },
        icon = icon,
        iconTint = iconTint,
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 2.dp, horizontal = 8.dp)
            .shadow(elevation.dp, shape = RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .then(modifier),
        trailingAction = trailingAction
    )
}

@Composable
private fun ListItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailingAction: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(iconName = icon, contentDescription = null, tint = iconTint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingAction != null) {
            trailingAction()
        }
    }
}
