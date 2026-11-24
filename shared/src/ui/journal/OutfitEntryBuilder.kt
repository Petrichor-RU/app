package nl.petrichor.app.ui.journal

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.EntryWeatherSnapshot
import nl.petrichor.app.data.outfit.OutfitEntry

/** Builds a new or updated [OutfitEntry] with the latest form fields and [weatherSnapshot]. */
internal fun buildOutfitEntry(
    existing: OutfitEntry?,
    date: LocalDate,
    photoPath: String?,
    notes: String?,
    weatherSnapshot: EntryWeatherSnapshot,
): OutfitEntry {
    val base =
        existing ?: OutfitEntry(
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
    return base.copy(
        wornOnDate = date,
        photoPath = photoPath,
        notes = notes,
        temperatureC = weatherSnapshot.temperatureC,
        feelsLikeC = weatherSnapshot.feelsLikeC,
        minTempC = weatherSnapshot.minTempC,
        maxTempC = weatherSnapshot.maxTempC,
        weatherDescription = weatherSnapshot.weatherDescription,
        weatherIcon = weatherSnapshot.weatherIcon,
        isRainy = weatherSnapshot.isRainy,
        windSpeedMs = weatherSnapshot.windSpeedMs,
        humidity = weatherSnapshot.humidity,
        precipitationProbability = weatherSnapshot.precipitationProbability,
        rainVolumeMm = weatherSnapshot.rainVolumeMm,
        weatherCapturedAtEpochMs = weatherSnapshot.weatherCapturedAtEpochMs,
        locationName = weatherSnapshot.locationName,
    )
}
