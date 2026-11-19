package nl.petrichor.app.data.recommendation

import kotlinx.serialization.Serializable
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs

/**
 * Unscaled raw garment and weather signals for one outfit observation or candidate.
 */
internal data class RawOutfitSignals(
    val tempC: Double,
    val isRainy: Boolean,
    val warmthUpper: Double,
    val warmthLower: Double,
    val warmthHeadAndFeet: Double,
    val layerCountUpper: Double,
) {
    val totalWarmth: Double
        get() = warmthUpper + warmthLower
}

/**
 * Means and standard deviations estimated via `kstats` [org.oremif.kstats.descriptive.OnlineStatistics]
 * for continuous inputs in schema v2. Persisted inside [RecommendationModelArtifact] so inference
 * applies the exact same standardisation used during training.
 */
@Serializable
data class FeatureScaler(
    val tempCMean: Double,
    val tempCStd: Double,
    val warmthUpperMean: Double,
    val warmthUpperStd: Double,
    val warmthLowerMean: Double,
    val warmthLowerStd: Double,
    val warmthHeadAndFeetMean: Double,
    val warmthHeadAndFeetStd: Double,
    val layerCountUpperMean: Double,
    val layerCountUpperStd: Double,
    val totalWarmthMean: Double,
    val totalWarmthStd: Double,
) {
    fun isValid(): Boolean =
        listOf(
            tempCMean,
            warmthUpperMean,
            warmthLowerMean,
            warmthHeadAndFeetMean,
            layerCountUpperMean,
            totalWarmthMean,
        ).all { it.isFinite() } &&
            listOf(
                tempCStd,
                warmthUpperStd,
                warmthLowerStd,
                warmthHeadAndFeetStd,
                layerCountUpperStd,
                totalWarmthStd,
            ).all { it.isFinite() && it > 0.0 }

    internal fun standardiseTemp(tempC: Double): Double = (tempC - tempCMean) / tempCStd

    internal fun standardiseUpper(warmthUpper: Double): Double = (warmthUpper - warmthUpperMean) / warmthUpperStd

    internal fun standardiseLower(warmthLower: Double): Double = (warmthLower - warmthLowerMean) / warmthLowerStd

    internal fun standardiseHeadAndFeet(warmth: Double): Double = (warmth - warmthHeadAndFeetMean) / warmthHeadAndFeetStd

    internal fun standardiseLayerCountUpper(layers: Double): Double = (layers - layerCountUpperMean) / layerCountUpperStd

    internal fun standardiseTotalWarmth(totalWarmth: Double): Double = (totalWarmth - totalWarmthMean) / totalWarmthStd

    companion object {
        internal fun fit(samples: List<RawOutfitSignals>): FeatureScaler {
            val (tempMean, tempStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].tempC })
            val (upperMean, upperStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].warmthUpper })
            val (lowerMean, lowerStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].warmthLower })
            val (extMean, extStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].warmthHeadAndFeet })
            val (layersMean, layersStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].layerCountUpper })
            val (totalMean, totalStd) = StatsFacade.meanAndStd(DoubleArray(samples.size) { samples[it].totalWarmth })
            return FeatureScaler(
                tempCMean = tempMean,
                tempCStd = tempStd,
                warmthUpperMean = upperMean,
                warmthUpperStd = upperStd,
                warmthLowerMean = lowerMean,
                warmthLowerStd = lowerStd,
                warmthHeadAndFeetMean = extMean,
                warmthHeadAndFeetStd = extStd,
                layerCountUpperMean = layersMean,
                layerCountUpperStd = layersStd,
                totalWarmthMean = totalMean,
                totalWarmthStd = totalStd,
            )
        }
    }
}

/**
 * Schema v2 feature extraction (`d = 10` features): standardised warmth & weather signals plus
 * temperature-warmth, quadratic-warmth, and rain-upper interactions.
 */
internal object FeatureExtractor {
    const val FEATURE_SCHEMA_VERSION = 2
    const val FEATURE_COUNT = 10

    fun extractRaw(
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): RawOutfitSignals =
        RawOutfitSignals(
            tempC = tempC,
            isRainy = isRainy,
            warmthUpper = WarmthEncoding.zoneWarmth(pieces, typeById, CoverageZone.UPPER),
            warmthLower = WarmthEncoding.zoneWarmth(pieces, typeById, CoverageZone.LOWER),
            warmthHeadAndFeet =
                WarmthEncoding.zoneWarmth(pieces, typeById, CoverageZone.HEAD) +
                    WarmthEncoding.zoneWarmth(pieces, typeById, CoverageZone.FEET),
            layerCountUpper = WarmthEncoding.layerCount(pieces, typeById, CoverageZone.UPPER).toDouble(),
        )

    /**
     * Standardised 10-element feature vector:
     * `[1.0, temp_std, isRainy, upper_std, lower_std, headFeet_std, layers_std,
     *   temp_std * total_std, total_std^2, isRainy * upper_std]`
     */
    fun buildFeatures(
        raw: RawOutfitSignals,
        scaler: FeatureScaler,
    ): DoubleArray {
        val tempStd = scaler.standardiseTemp(raw.tempC)
        val upperStd = scaler.standardiseUpper(raw.warmthUpper)
        val lowerStd = scaler.standardiseLower(raw.warmthLower)
        val headFeetStd = scaler.standardiseHeadAndFeet(raw.warmthHeadAndFeet)
        val layersStd = scaler.standardiseLayerCountUpper(raw.layerCountUpper)
        val totalStd = scaler.standardiseTotalWarmth(raw.totalWarmth)
        val rainyFlag = if (raw.isRainy) 1.0 else 0.0
        val tempDiff = abs(tempStd)
        check(tempDiff.isFinite()) // retains deterministic failure only on corrupt non-finite temperatures
        return doubleArrayOf(
            1.0,
            tempStd,
            rainyFlag,
            upperStd,
            lowerStd,
            headFeetStd,
            layersStd,
            tempStd * totalStd,
            totalStd * totalStd,
            rainyFlag * upperStd,
        )
    }
}
