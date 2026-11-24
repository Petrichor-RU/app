package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.SurveyContextQuestion
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs

/**
 * Selects at most one optional, low-friction survey follow-up question using active learning.
 * Combines reducible epistemic variance (`x^T P x` + unseen garment prior variance), likely
 * future frequency (garment usage + common temperature band), and context relevance/surprise.
 */
internal object ActiveQuerySelector {
    const val RAIN_RELEVANCE_THRESHOLD = 0.2
    const val HIGH_UNCERTAINTY_VAR_THRESHOLD = 0.28
    const val SURPRISE_THRESHOLD = 0.55
    private const val MIN_INFO_VALUE_THRESHOLD = 0.30
    private const val INDOOR_COMFORT_TEMP_C = 20.0
    private const val TEMP_BAND_RADIUS_C = 6.0

    fun informationValue(
        item: OutfitEntryWithPieces,
        model: DecodedModel?,
        clothingTypes: List<ClothingType>,
        historyEntries: List<OutfitEntryWithPieces>,
        candidateScore: Int? = item.entry.surveyFeelingScore,
        candidateVerdict: WarmthVerdict? = item.entry.surveyWarmthVerdict,
    ): Double =
        scoreQuestions(
            item = item,
            model = model,
            clothingTypes = clothingTypes,
            historyEntries = historyEntries,
            candidateScore = candidateScore,
            candidateVerdict = candidateVerdict,
        ).maxOfOrNull { it.second } ?: 0.0

    fun selectFollowUpQuestion(
        item: OutfitEntryWithPieces,
        model: DecodedModel?,
        clothingTypes: List<ClothingType>,
        historyEntries: List<OutfitEntryWithPieces>,
        candidateScore: Int? = item.entry.surveyFeelingScore,
        candidateVerdict: WarmthVerdict? = item.entry.surveyWarmthVerdict,
    ): SurveyContextQuestion? =
        scoreQuestions(
            item = item,
            model = model,
            clothingTypes = clothingTypes,
            historyEntries = historyEntries,
            candidateScore = candidateScore,
            candidateVerdict = candidateVerdict,
        ).filter { it.second >= MIN_INFO_VALUE_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

    @Suppress("LongParameterList") // active-learning scorer needs current item, posterior, and history signals
    private fun scoreQuestions(
        item: OutfitEntryWithPieces,
        model: DecodedModel?,
        clothingTypes: List<ClothingType>,
        historyEntries: List<OutfitEntryWithPieces>,
        candidateScore: Int?,
        candidateVerdict: WarmthVerdict?,
    ): List<Pair<SurveyContextQuestion, Double>> {
        val entry = item.entry
        val tempC = entryTemperature(entry) ?: return emptyList()
        val typeById = clothingTypes.associateBy { it.id }
        val assessment = assessModelState(item, model, tempC, typeById, candidateScore, candidateVerdict)
        val futureFrequency = computeFutureFrequency(item, tempC, historyEntries)
        val candidates = mutableListOf<Pair<SurveyContextQuestion, Double>>()

        val rainExposure = entry.rainExposure
        if (rainExposure > RAIN_RELEVANCE_THRESHOLD) {
            val wetValue =
                rainExposure *
                    (0.5 + 0.5 * assessment.epistemicFactor) *
                    futureFrequency *
                    (1.0 + 0.4 * assessment.surprise)
            candidates += SurveyContextQuestion.GOT_WET to wetValue
        }

        val indoorOrActivityEligible =
            assessment.hasModel &&
                (assessment.reducibleVariance >= HIGH_UNCERTAINTY_VAR_THRESHOLD || assessment.surprise >= SURPRISE_THRESHOLD)
        if (indoorOrActivityEligible) {
            val baseInfo = (0.45 * assessment.epistemicFactor + 0.55 * assessment.surprise) * futureFrequency
            val indoorGap = (abs(tempC - INDOOR_COMFORT_TEMP_C) / 15.0).coerceIn(0.0, 1.0)
            val unexpectedWarmth =
                candidateVerdict == WarmthVerdict.TOO_WARM ||
                    (candidateVerdict == WarmthVerdict.PERFECT && assessment.predictedTooCold > 0.35)
            val indoorScore = baseInfo * (0.75 + 0.45 * indoorGap + if (entry.windExposure > 0.4) 0.15 else 0.0)
            val activityScore = baseInfo * (0.75 + if (unexpectedWarmth) 0.35 else 0.15 * (1.0 - indoorGap))
            candidates += SurveyContextQuestion.MOSTLY_INDOORS to indoorScore
            candidates += SurveyContextQuestion.HIGH_ACTIVITY to activityScore
        }
        return candidates
    }

    @Suppress("LongParameterList") // computes epistemic variance and surprise in one pass
    private fun assessModelState(
        item: OutfitEntryWithPieces,
        model: DecodedModel?,
        tempC: Double,
        typeById: Map<Long, ClothingType>,
        candidateScore: Int?,
        candidateVerdict: WarmthVerdict?,
    ): QueryModelAssessment {
        if (model == null || item.pieces.isEmpty()) {
            return QueryModelAssessment(
                hasModel = false,
                reducibleVariance = 1.0,
                epistemicFactor = 1.0,
                surprise = 0.0,
                predictedTooCold = 0.0,
            )
        }
        val entry = item.entry
        val extracted =
            FeatureExtractor.extractForModel(
                model = model,
                tempC = tempC,
                isRainy = entry.isRainy,
                pieces = item.pieces,
                typeById = typeById,
                windSpeedMs = entry.windSpeedMs,
                precipitationProbability = entry.precipitationProbability,
                rainVolumeMm = entry.rainVolumeMm,
            )
        val comfort =
            BayesianComfortModel.predict(
                model.ratingPosterior,
                model.thermalPosterior,
                extracted.raw,
                extracted.features,
                extracted.unseenPieceIds.size,
            )
        val ratingVar = comfort.rating.epistemicStd * comfort.rating.epistemicStd / BayesianRatingModel.OFFSET_PRIOR_VARIANCE
        val thermalVar = comfort.thermal.epistemicStd * comfort.thermal.epistemicStd
        val reducibleVariance = 0.5 * (ratingVar + thermalVar)
        val epistemicFactor = (reducibleVariance / HIGH_UNCERTAINTY_VAR_THRESHOLD).coerceIn(0.25, 1.5)
        val surprise = computeSurprise(comfort, candidateScore, candidateVerdict)
        return QueryModelAssessment(
            hasModel = true,
            reducibleVariance = reducibleVariance,
            epistemicFactor = epistemicFactor,
            surprise = surprise,
            predictedTooCold = comfort.thermal.tooCold,
        )
    }

    private fun computeSurprise(
        comfort: ComfortPrediction,
        candidateScore: Int?,
        candidateVerdict: WarmthVerdict?,
    ): Double {
        val verdictSurprise =
            when (candidateVerdict) {
                WarmthVerdict.TOO_COLD -> 1.0 - comfort.thermal.tooCold
                WarmthVerdict.PERFECT -> 1.0 - comfort.thermal.perfect
                WarmthVerdict.TOO_WARM -> 1.0 - comfort.thermal.tooWarm
                null -> 0.0
            }.coerceIn(0.0, 1.0)
        val ratingSurprise =
            if (candidateScore != null) {
                (abs(candidateScore.toDouble() - comfort.rating.mean) / 4.0).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        return maxOf(verdictSurprise, ratingSurprise)
    }

    private fun computeFutureFrequency(
        item: OutfitEntryWithPieces,
        tempC: Double,
        historyEntries: List<OutfitEntryWithPieces>,
    ): Double {
        if (historyEntries.isEmpty()) return 0.75
        val pieceIds = item.pieces.map { it.id }.toSet()
        val pieceShare =
            if (pieceIds.isEmpty()) {
                0.5
            } else {
                val matchingEntries = historyEntries.count { h -> h.pieces.any { it.id in pieceIds } }
                (matchingEntries.toDouble() / historyEntries.size).coerceIn(0.35, 1.0)
            }
        val temps = historyEntries.mapNotNull { entryTemperature(it.entry) }
        val bandShare =
            if (temps.isEmpty()) {
                0.75
            } else {
                val inBand = temps.count { abs(it - tempC) <= TEMP_BAND_RADIUS_C }
                (inBand.toDouble() / temps.size).coerceIn(0.35, 1.0)
            }
        return 0.5 * (pieceShare + bandShare)
    }
}

private data class QueryModelAssessment(
    val hasModel: Boolean,
    val reducibleVariance: Double,
    val epistemicFactor: Double,
    val surprise: Double,
    val predictedTooCold: Double,
)
