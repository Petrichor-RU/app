@file:Suppress("MatchingDeclarationName") // hosts the grid entry type and its merge/filter helpers

package nl.petrichor.app.ui.wardrobe.affiliate

import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.affiliate.MAX_AFFILIATE_ITEMS
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.random.Random

/** One cell of the wardrobe grid: either the user's own item or a sponsored card. */
sealed interface WardrobeGridEntry {
    data class UserItem(
        val piece: ClothingPiece,
    ) : WardrobeGridEntry

    data class SponsoredItem(
        val item: AffiliateItem,
    ) : WardrobeGridEntry
}

/** Sponsored-card count rule: `(wardrobe count) / 4`, clamped to [MIN_SPONSORED]..[MAX_SPONSORED]. */
private const val MIN_SPONSORED = 1
private const val MAX_SPONSORED = 5

/**
 * Merges [sponsored] into [userItems] at randomly sprinkled positions. The number of sponsored
 * cards is `(userItems.size / 4)` clamped to 1..5, plus one more when that would make the total
 * grid count odd (so a two-column grid fills evenly); never more than [MAX_AFFILIATE_ITEMS] or
 * than [sponsored] has. Both lists are already filtered by WardrobeScreen; user items are never
 * shifted or reordered to make room. [random] is injectable for deterministic tests.
 */
fun mergeSponsoredIntoGrid(
    userItems: List<ClothingPiece>,
    sponsored: List<AffiliateItem>,
    random: Random = Random.Default,
): List<WardrobeGridEntry> {
    val base = (userItems.size / 4).coerceIn(MIN_SPONSORED, MAX_SPONSORED)
    val target = if ((userItems.size + base) % 2 == 1) base + 1 else base
    val count = minOf(target, sponsored.size, MAX_AFFILIATE_ITEMS)
    // Random distinct 1-based grid positions for the sponsored cards.
    val positions = (1..(userItems.size + count)).shuffled(random).take(count).toSet()
    val result = mutableListOf<WardrobeGridEntry>()
    var sponsoredIndex = 0
    for (piece in userItems) {
        while (sponsoredIndex < count && (result.size + 1) in positions) {
            result += WardrobeGridEntry.SponsoredItem(sponsored[sponsoredIndex++])
        }
        result += WardrobeGridEntry.UserItem(piece)
    }
    // Positions past the last user item (small wardrobes) land here.
    while (sponsoredIndex < count) {
        result += WardrobeGridEntry.SponsoredItem(sponsored[sponsoredIndex++])
    }
    return result
}

/**
 * Applies the wardrobe type filter to sponsored cards: with no filter everything passes;
 * otherwise the card's mapped type name must equal the selected type's name, exactly like the
 * user's own items are matched by type id.
 */
internal fun filterSponsoredByType(
    items: List<AffiliateItem>,
    selectedType: ClothingType?,
): List<AffiliateItem> = if (selectedType == null) items else items.filter { it.typeName == selectedType.name }
