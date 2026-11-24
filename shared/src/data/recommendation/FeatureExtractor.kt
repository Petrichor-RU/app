package nl.petrichor.app.data.recommendation

import kotlinx.serialization.Serializable
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WeatherProtection
import kotlin.math.abs

/**
 * Unscaled raw garment and weather signals for one outfit observation or candidate.
 * Uses air temperature (`tempC`) plus explicit [windExposure] and [rainExposure] without
 * double-counting `feelsLikeC`.
 */
internal data class RawOutfitSignals(
    val tempC: Double,
    val isRainy: Boolean,
    val warmthUpper: Double,
    val warmthLower: Double,
    val warmthHeadAndFeet: Double,
    val layerCountUpper: Double,
    val windExposure: Double = 0.0,
    val rainExposure: Double = if (isRainy) RAINY_FLAG_BASE_EXPOSURE else 0.0,
    val rainProtectionScore: Double = 0.0,
    val windProtectionScore: Double = 0.0,
) {
    val totalWarmth: Double
        get() = warmthUpper + warmthLower

    val hasRainShell: Boolean
        get() = rainProtectionScore > 0.0

    val hasWindShell: Boolean
        get() = windProtectionScore > 0.0
}

internal data class ExtractedModelFeatures(
    val raw: RawOutfitSignals,
    val features: DoubleArray,
    val unseenPieceIds: List<Long>,
)

/**
 * Means and standard deviations estimated via `kstats` [org.oremif.kstats.descriptive.OnlineStatistics]
 * for continuous inputs in schema v3. Persisted inside [RecommendationModelArtifact] so inference
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
 * Schema v3 feature extraction (`CORE_FEATURE_COUNT = 14` core features + up to `MAX_ACTIVE_PIECES = 24`
 * per-garment dry-warmth residuals):
 *  0. `1.0` (unpenalised / wide-prior personal intercept)
 *  1. `temp_std`
 *  2. `rainExposure`
 *  3. `upper_std`
 *  4. `lower_std`
 *  5. `headFeet_std`
 *  6. `layers_std`
 *  7. `temp_std * total_std`
 *  8. `total_std^2`
 *  9. `rainExposure * upper_std`
 * 10. `rainExposure * (1.0 - rainProtectionScore)`
 * 11. `rainExposure * rainProtectionScore`
 * 12. `windExposure * (1.0 - windProtectionScore)`
 * 13. `windExposure * windProtectionScore`
 * 14..(14+M-1). Active garment indicators (`1.0` if present, else `0.0`)
 */
internal object FeatureExtractor {
    const val FEATURE_SCHEMA_VERSION = 3
    const val CORE_FEATURE_COUNT = 14
    const val MIN_PIECE_OBSERVATIONS = 2
    const val MAX_ACTIVE_PIECES = 24
    const val MAX_TOTAL_FEATURES = CORE_FEATURE_COUNT + MAX_ACTIVE_PIECES

    fun extractRaw(
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double? = null,
        precipitationProbability: Double? = null,
        rainVolumeMm: Double? = null,
        rainExposureOverride: Double? = null,
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
            windExposure = computeWindExposure(windSpeedMs),
            rainExposure = rainExposureOverride ?: computeRainExposure(isRainy, precipitationProbability, rainVolumeMm),
            rainProtectionScore = maxShellProtection(pieces, typeById) { it.rainProtectionScore },
            windProtectionScore = maxShellProtection(pieces, typeById) { it.windProtectionScore },
        )

    private fun maxShellProtection(
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        scoreSelector: (WeatherProtection) -> Double,
    ): Double =
        pieces
            .filter { piece ->
                val supertype = typeById[piece.typeId]?.supertype
                supertype != null && CoverageZone.UPPER in WarmthEncoding.zonesFor(supertype)
            }.maxOfOrNull { scoreSelector(it.weatherProtection) }
            ?: 0.0

    fun buildCoreFeatures(
        raw: RawOutfitSignals,
        scaler: FeatureScaler,
    ): DoubleArray {
        val tempStd = (raw.tempC - scaler.tempCMean) / scaler.tempCStd
        val upperStd = (raw.warmthUpper - scaler.warmthUpperMean) / scaler.warmthUpperStd
        val lowerStd = (raw.warmthLower - scaler.warmthLowerMean) / scaler.warmthLowerStd
        val headFeetStd = (raw.warmthHeadAndFeet - scaler.warmthHeadAndFeetMean) / scaler.warmthHeadAndFeetStd
        val layersStd = (raw.layerCountUpper - scaler.layerCountUpperMean) / scaler.layerCountUpperStd
        val totalStd = (raw.totalWarmth - scaler.totalWarmthMean) / scaler.totalWarmthStd
        check(abs(tempStd).isFinite()) // retains deterministic failure only on corrupt non-finite temperatures
        return doubleArrayOf(
            1.0,
            tempStd,
            raw.rainExposure,
            upperStd,
            lowerStd,
            headFeetStd,
            layersStd,
            tempStd * totalStd,
            totalStd * totalStd,
            raw.rainExposure * upperStd,
            raw.rainExposure * (1.0 - raw.rainProtectionScore),
            raw.rainExposure * raw.rainProtectionScore,
            raw.windExposure * (1.0 - raw.windProtectionScore),
            raw.windExposure * raw.windProtectionScore,
        )
    }

    fun buildFeatures(
        raw: RawOutfitSignals,
        scaler: FeatureScaler,
        pieces: List<ClothingPiece> = emptyList(),
        activePieceIndexById: Map<Long, Int> = emptyMap(),
    ): DoubleArray {
        val core = buildCoreFeatures(raw, scaler)
        if (activePieceIndexById.isEmpty()) return core
        val full = DoubleArray(CORE_FEATURE_COUNT + activePieceIndexById.size)
        core.copyInto(full)
        for (piece in pieces) {
            val slot = activePieceIndexById[piece.id]
            if (slot != null) {
                full[CORE_FEATURE_COUNT + slot] = 1.0
            }
        }
        return full
    }

    fun extractForModel(
        model: DecodedModel,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double? = null,
        precipitationProbability: Double? = null,
        rainVolumeMm: Double? = null,
    ): ExtractedModelFeatures {
        val raw =
            extractRaw(
                tempC = tempC,
                isRainy = isRainy,
                pieces = pieces,
                typeById = typeById,
                windSpeedMs = windSpeedMs,
                precipitationProbability = precipitationProbability,
                rainVolumeMm = rainVolumeMm,
            )
        val features = buildFeatures(raw, model.scaler, pieces, model.activePieceIndexById)
        val unseenPieceIds =
            pieces
                .asSequence()
                .map { it.id }
                .distinct()
                .filter { it !in model.activePieceIndexById }
                .toList()
        return ExtractedModelFeatures(raw = raw, features = features, unseenPieceIds = unseenPieceIds)
    }

    /**
     * Selects up to [MAX_ACTIVE_PIECES] garment IDs observed at least [MIN_PIECE_OBSERVATIONS]
     * times across usable training history so covariance stays bounded (`d <= 38`) and rarely worn
     * pieces retain zero residual mean with full prior uncertainty.
     */
    fun selectActivePieceIds(usableEntries: List<OutfitEntryWithPieces>): List<Long> {
        val counts = HashMap<Long, Int>()
        for (item in usableEntries) {
            for (pieceId in item.pieces.map { it.id }.distinct()) {
                counts[pieceId] = (counts[pieceId] ?: 0) + 1
            }
        }
        return counts.entries
            .asSequence()
            .filter { it.value >= MIN_PIECE_OBSERVATIONS }
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(MAX_ACTIVE_PIECES)
            .map { it.key }
            .sorted()
            .toList()
    }
}
