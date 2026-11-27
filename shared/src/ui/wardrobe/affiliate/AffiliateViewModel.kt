package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.petrichor.app.data.affiliate.AffiliateFeed
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot

/**
 * Affiliate state adjacent to [nl.petrichor.app.ui.wardrobe.WardrobeViewModel]: the sponsored items for the wardrobe grid.
 * Nothing is persisted — the feed caches in memory only — and all failures degrade silently to
 * an empty list. The shared feed is pre-warmed by the app shell on load, so opening the Wardrobe
 * tab usually hits the cache instead of fetching.
 */
class AffiliateViewModel(
    private val feed: AffiliateFeed = AffiliateFeed(),
) : ViewModel() {
    private val _items = MutableStateFlow<List<AffiliateItem>>(emptyList())
    val items: StateFlow<List<AffiliateItem>> = _items.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * Refresh for [place]/[weather]; cancels any in-flight refresh. Never throws to the UI.
     * [place] is the selected location (drives store selection via the user's real country,
     * falling back to [place]); [weather] is that location's snapshot (drives the queries).
     * The feed caches adverts per place+country for about an hour; on a cache miss the stale
     * items are cleared immediately and shown again only once the fresh fetch completes.
     */
    fun refresh(
        place: Place?,
        weather: WeatherSnapshot?,
    ) {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                _items.value = feed.fetch(place, weather) { _items.value = emptyList() }
            }
    }
}
