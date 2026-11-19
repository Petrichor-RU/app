package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces

/**
 * Selects up to [FeatureExtractor.MAX_ACTIVE_PIECES] garment IDs observed at least
 * [FeatureExtractor.MIN_PIECE_OBSERVATIONS] times across usable training history.
 * Keeping the active set bounded ensures the posterior covariance stays within `d <= 38`
 * (`<= 12 KiB`) and unseen or rarely worn pieces inherit a zero-mean residual with full prior
 * uncertainty instead of overfitting.
 */
internal object ActivePieceSelector {
    fun selectActivePieceIds(usableEntries: List<OutfitEntryWithPieces>): List<Long> {
        val counts = HashMap<Long, Int>()
        for (item in usableEntries) {
            for (pieceId in item.pieces.map { it.id }.distinct()) {
                counts[pieceId] = (counts[pieceId] ?: 0) + 1
            }
        }
        return counts.entries
            .asSequence()
            .filter { it.value >= FeatureExtractor.MIN_PIECE_OBSERVATIONS }
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(FeatureExtractor.MAX_ACTIVE_PIECES)
            .map { it.key }
            .sorted()
            .toList()
    }
}
