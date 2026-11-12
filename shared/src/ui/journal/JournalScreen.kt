package nl.petrichor.app.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.buildEntryWeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.epochMsToLocalDate
import nl.petrichor.app.ui.longLabel
import nl.petrichor.app.ui.shortLabel
import kotlin.time.Clock

/**
 * Journal tab (plan section 5.1): lists logged outfits newest-first, FAB to log an outfit,
 * tap opens the detail sheet, and consumes [pendingAction] from the JournalActionBus
 * (notification deep links).
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // screen owns all journal state per project convention
@Composable
fun JournalScreen(
    modifier: Modifier = Modifier,
    pendingAction: JournalPendingAction? = null,
    onActionConsumed: () -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        BoxMessage(message = "Journal preview", modifier = modifier.fillMaxSize())
        return
    }

    val viewModel = viewModel { JournalViewModel() }
    val entries by viewModel.entries.collectAsStateWithLifecycle(emptyList())
    val wardrobePieces by viewModel.wardrobePieces.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by viewModel.clothingTypes.collectAsStateWithLifecycle(emptyList())
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingOutfit by remember { mutableStateOf<OutfitEntryWithPieces?>(null) }
    var selectedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedEntry = entries.firstOrNull { it.entry.id == selectedEntryId }
    var closingEntry by remember { mutableStateOf<OutfitEntryWithPieces?>(null) }
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var animateDetail by rememberSaveable { mutableStateOf(false) }
    var showSurvey by remember { mutableStateOf(false) }
    var surveyTarget by remember { mutableStateOf<OutfitEntryWithPieces?>(null) }
    var surveyEmptyState by remember { mutableStateOf(false) }
    var surveyAfterLog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val detailTransition = remember(selectedEntryId) { MutableTransitionState(!animateDetail) }
    detailTransition.targetState = showDetail && selectedEntry != null
    LaunchedEffect(
        detailTransition.currentState,
        detailTransition.isIdle,
        selectedEntryId,
        showDetail,
    ) {
        if (!detailTransition.isIdle || selectedEntryId == null) return@LaunchedEffect
        if (detailTransition.currentState) {
            animateDetail = false
        } else if (!showDetail) {
            selectedEntryId = null
            closingEntry = null
        }
    }

    viewModel.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    // Notification deep links: open the corresponding dialog once.
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            JournalPendingAction.ADD_OUTFIT -> {
                editingOutfit = null
                showAddDialog = true
            }

            JournalPendingAction.SURVEY -> {
                val target = viewModel.mostRecentUnsurveyedToday()
                if (target != null) {
                    surveyTarget = target
                    surveyEmptyState = false
                } else {
                    surveyTarget = null
                    surveyEmptyState = true
                }
                showSurvey = true
            }

            null -> {}
        }
    }

    fun consumeActionIfPending() {
        if (pendingAction != null) onActionConsumed()
    }

    Box(modifier = modifier.fillMaxSize()) {
        BackHandler(
            enabled = showDetail && !showAddDialog && !showSurvey,
            onBack = { showDetail = false },
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!showDetail) {
                    FloatingActionButton(onClick = {
                        editingOutfit = null
                        showAddDialog = true
                    }) {
                        MaterialSymbol(iconName = MaterialSymbols.ADD, contentDescription = "Log outfit")
                    }
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entries.isEmpty()) {
                    item {
                        BoxMessage(
                            message = "No outfits logged yet. Tap + to log your first outfit!",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    items(entries, key = { it.entry.id }) { entryWithPieces ->
                        JournalEntryRow(
                            entryWithPieces = entryWithPieces,
                            onClick = {
                                selectedEntryId = entryWithPieces.entry.id
                                animateDetail = true
                                showDetail = true
                            },
                            onSurvey = {
                                surveyTarget = entryWithPieces
                                surveyEmptyState = false
                                showSurvey = true
                            },
                        )
                    }
                }
            }
        }

        // Detail sheet overlay (exit animation completes before selection clears)
        (selectedEntry ?: closingEntry)?.let { entryWithPieces ->
            AnimatedVisibility(
                visibleState = detailTransition,
                modifier = Modifier.fillMaxSize().zIndex(1f),
                enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
            ) {
                OutfitDetailSheet(
                    entryWithPieces = entryWithPieces,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    actions =
                        OutfitDetailActions(
                            onDismiss = { showDetail = false },
                            onEdit = { editing ->
                                showDetail = false
                                editingOutfit = editing
                                showAddDialog = true
                            },
                            onDelete = { deleted ->
                                closingEntry = deleted
                                viewModel.deleteEntry(deleted.entry)
                                showDetail = false
                            },
                            onSurvey = { target ->
                                surveyTarget = target
                                surveyEmptyState = false
                                showSurvey = true
                            },
                            onMarkLaundry = { target, days ->
                                viewModel.markOutfitLaundry(target.entry.id, days)
                            },
                        ),
                )
            }
        }

        if (showAddDialog) {
            AddOutfitDialog(
                params =
                    AddOutfitDialogParams(
                        outfit = editingOutfit,
                        clothingTypes = clothingTypes,
                        wardrobePieces = wardrobePieces,
                        today = today,
                        onDismiss = {
                            showAddDialog = false
                            consumeActionIfPending()
                        },
                        onSave = { date, photoPath, notes, selectedPieces ->
                            val editing = editingOutfit
                            val weather = viewModel.weatherForSelectedPlace()
                            val snapshot = buildEntryWeatherSnapshot(weather, date, today)
                            val base =
                                editing?.entry ?: OutfitEntry(
                                    id = 0,
                                    wornOnDate = date,
                                    photoPath = null,
                                    notes = null,
                                    temperatureC = null,
                                    feelsLikeC = null,
                                    minTempC = null,
                                    maxTempC = null,
                                    weatherDescription = null,
                                    weatherIcon = null,
                                    isRainy = false,
                                    surveyFeelingScore = null,
                                    surveyWarmthVerdict = null,
                                    surveyedAtEpochMs = null,
                                )
                            val entry =
                                base.copy(
                                    wornOnDate = date,
                                    photoPath = photoPath,
                                    notes = notes,
                                    temperatureC = snapshot.temperatureC,
                                    feelsLikeC = snapshot.feelsLikeC,
                                    minTempC = snapshot.minTempC,
                                    maxTempC = snapshot.maxTempC,
                                    weatherDescription = snapshot.weatherDescription,
                                    weatherIcon = snapshot.weatherIcon,
                                    isRainy = snapshot.isRainy,
                                )
                            val saved =
                                if (editing == null) {
                                    val newId = viewModel.logOutfit(entry, selectedPieces)
                                    if (newId != null && surveyAfterLog) {
                                        // 20:00 flow: after logging an outfit first, reopen the survey for it
                                        surveyTarget = viewModel.entryById(newId)
                                        surveyEmptyState = false
                                        surveyAfterLog = false
                                        showSurvey = true
                                    }
                                    newId != null
                                } else {
                                    viewModel.updateOutfit(entry, selectedPieces, previousPhotoPath = editing.entry.photoPath)
                                }
                            if (saved) {
                                showAddDialog = false
                                consumeActionIfPending()
                            }
                            saved
                        },
                    ),
            )
        }

        if (showSurvey) {
            SurveyDialog(
                entry = if (surveyEmptyState) null else surveyTarget,
                onDismiss = {
                    showSurvey = false
                    consumeActionIfPending()
                },
                onSubmit = { entryId, score, verdict ->
                    viewModel.saveSurvey(entryId, score, verdict)
                },
                onLogOutfitFirst = {
                    showSurvey = false
                    surveyAfterLog = true
                    editingOutfit = null
                    showAddDialog = true
                    consumeActionIfPending()
                },
            )
        }
    }
}

@Composable
private fun JournalEntryRow(
    entryWithPieces: OutfitEntryWithPieces,
    onClick: () -> Unit,
    onSurvey: (OutfitEntryWithPieces) -> Unit,
) {
    val entry = entryWithPieces.entry
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JournalEntryThumbnail(photoPath = entry.photoPath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.wornOnDate.longLabel(), style = MaterialTheme.typography.titleMedium)
                if (entryWithPieces.pieces.isNotEmpty()) {
                    JournalPieceChips(pieces = entryWithPieces.pieces)
                }
                if (entry.laundryUntilEpochMs > 0) {
                    Text(
                        text = "In laundry until ${epochMsToLocalDate(entry.laundryUntilEpochMs).shortLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Survey state/button sits at the end (right side) of the list item.
            if (entry.surveyFeelingScore != null && entry.surveyWarmthVerdict != null) {
                Text(
                    text = surveySummary(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = { onSurvey(entryWithPieces) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHECKLIST_RTL,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Survey")
                }
            }
        }
    }
}

@Composable
private fun JournalEntryThumbnail(photoPath: String?) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        val painter =
            if (photoPath?.isNotEmpty() == true) {
                loadImagePainter(photoPath)
            } else {
                null
            }
        if (painter != null) {
            Image(painter = painter, contentDescription = "Outfit selfie", modifier = Modifier.fillMaxSize())
        } else {
            MaterialSymbol(iconName = MaterialSymbols.IMAGE, contentDescription = null)
        }
    }
}

private fun surveySummary(entry: OutfitEntry): String =
    if (entry.surveyFeelingScore != null && entry.surveyWarmthVerdict != null) {
        "${entry.surveyFeelingScore}/5 - ${entry.surveyWarmthVerdict.verdictLabel()}"
    } else {
        "Survey pending"
    }
