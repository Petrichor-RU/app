package nl.petrichor.app.data.guide

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.petrichor.app.data.weather.Place

/**
 * Business boundary for the location guide, mirroring `WeatherRepository`: the single entry point
 * for climate data. Holds no cache by decision — every call for a newly selected place triggers a
 * new fetch. Mutations/fetches are dispatched off the UI thread.
 */
class GuideRepository(
    private val client: ClimateClient = ClimateClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Climate profile for [place], or null when the fetch fails — the expanded guide then shows
     * its "no climate data available" message (the view model clears any stale profile on a null
     * result), so there is no separate error surface. [currentHumidity] is the Weather tab's live
     * snapshot humidity in percent, attached to every month; pass 0 when it is unknown.
     */
    suspend fun climateFor(
        place: Place,
        currentHumidity: Int = 0,
    ): ClimateProfile? =
        withContext(ioDispatcher) {
            client
                .fetchMonths(place, currentHumidity)
                .map { months -> ClimateProfile(place, months) }
                .getOrNull()
        }
}
