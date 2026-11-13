package nl.petrichor.app.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.petrichor.app.data.location.SelectedLocationStore
import nl.petrichor.app.data.location.currentPlace
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot

/**
 * Lightweight view model for the Journal tab, created with `viewModel` at the call site
 * (same convention as WardrobeViewModel). Exposes repository flows and suspend actions with
 * error surfacing via [snackbarMessage].
 */
@Suppress("TooGenericExceptionCaught") // repository failures are surfaced as snackbars, so any exception is handled
class JournalViewModel(
    private val repository: OutfitLogRepository = OutfitLogRepository(),
    wardrobeRepository: WardrobeRepository = WardrobeRepository(),
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val locationStore: SelectedLocationStore = SelectedLocationStore(),
) : ViewModel() {
    val entries: Flow<List<OutfitEntryWithPieces>> = repository.entries
    val wardrobePieces: Flow<List<ClothingPiece>> = wardrobeRepository.getAllClothing()
    val clothingTypes: Flow<List<ClothingType>> = wardrobeRepository.getClothingTypes()
    val laundryVersion: Flow<Int> =
        combine(repository.laundryVersion, wardrobeRepository.laundryVersion) { outfit, piece -> outfit + piece }

    private var _snackbarMessage by mutableStateOf<String?>(null)
    val snackbarMessage: String? get() = _snackbarMessage

    fun showSnackbar(message: String) {
        _snackbarMessage = message
    }

    fun clearSnackbar() {
        _snackbarMessage = null
    }

    /** @return the new entry id, or null when saving failed (snackbar set). */
    suspend fun logOutfit(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
    ): Long? =
        try {
            val id = repository.logOutfit(entry, pieces)
            showSnackbar("Outfit logged!")
            id
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not log outfit: ${exception.message ?: "Unknown error"}")
            null
        }

    suspend fun updateOutfit(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
        previousPhotoPath: String?,
    ): Boolean =
        try {
            repository.updateOutfit(entry, pieces)
            if (previousPhotoPath != null && previousPhotoPath != entry.photoPath && previousPhotoPath.isNotEmpty()) {
                MediaStorage.deleteImage(previousPhotoPath)
            }
            showSnackbar("Outfit updated!")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not update outfit: ${exception.message ?: "Unknown error"}")
            false
        }

    suspend fun saveSurvey(
        entryId: Long,
        score: Int,
        verdict: WarmthVerdict,
    ): Boolean =
        try {
            repository.saveSurvey(entryId, score, verdict)
            showSnackbar("Survey saved!")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not save survey: ${exception.message ?: "Unknown error"}")
            false
        }

    fun deleteEntry(entry: OutfitEntry) =
        viewModelScope.launch {
            try {
                repository.deleteEntry(entry)
                showSnackbar("Entry deleted!")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showSnackbar("Could not delete entry: ${exception.message ?: "Unknown error"}")
            }
        }

    fun markOutfitLaundry(
        entryId: Long,
        days: Int,
    ) = viewModelScope.launch {
        try {
            repository.markOutfitLaundry(entryId, days)
            showSnackbar("Marked in laundry for $days day${if (days == 1) "" else "s"}.")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            showSnackbar("Could not mark laundry: ${exception.message ?: "Unknown error"}")
        }
    }

    suspend fun mostRecentUnsurveyedToday(): OutfitEntryWithPieces? =
        withContext(kotlinx.coroutines.Dispatchers.Default) { repository.mostRecentUnsurveyedToday() }

    suspend fun entryById(id: Long): OutfitEntryWithPieces? = repository.getEntry(id)

    /** Current weather for the shared selected place (null selection = current location). */
    suspend fun weatherForSelectedPlace(): WeatherSnapshot? {
        val place = locationStore.selectedPlace.first() ?: currentPlace()
        return weatherRepository.load(place).getOrNull()
    }
}
