package nl.petrichor.app.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.buildEntryWeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage
import kotlin.time.Clock

/**
 * Journal tab: lists logged outfits newest-first, FAB to log an outfit,
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
                surveyTarget = target
                surveyEmptyState = target == null
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
            JournalEntryList(
                entries = entries,
                onEntryClick = { entryWithPieces ->
                    selectedEntryId = entryWithPieces.entry.id
                    animateDetail = true
                    showDetail = true
                },
                onSurveyClick = { entryWithPieces ->
                    surveyTarget = entryWithPieces
                    surveyEmptyState = false
                    showSurvey = true
                },
                modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding(),
            )
        }

        // Detail sheet overlay (exit animation completes before selection clears)
        (selectedEntry ?: closingEntry)?.let { entryWithPieces ->
            JournalDetailOverlay(
                entryWithPieces = entryWithPieces,
                detailTransition = detailTransition,
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
                            val entry = buildOutfitEntry(editing?.entry, date, photoPath, notes, snapshot)
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
private fun JournalEntryList(
    entries: List<OutfitEntryWithPieces>,
    onEntryClick: (OutfitEntryWithPieces) -> Unit,
    onSurveyClick: (OutfitEntryWithPieces) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
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
                    onClick = { onEntryClick(entryWithPieces) },
                    onSurvey = onSurveyClick,
                )
            }
        }
    }
}

@Composable
private fun JournalDetailOverlay(
    entryWithPieces: OutfitEntryWithPieces,
    detailTransition: MutableTransitionState<Boolean>,
    actions: OutfitDetailActions,
) {
    AnimatedVisibility(
        visibleState = detailTransition,
        modifier = Modifier.fillMaxSize().zIndex(1f),
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
    ) {
        OutfitDetailSheet(
            entryWithPieces = entryWithPieces,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            actions = actions,
        )
    }
}
