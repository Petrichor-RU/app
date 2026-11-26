package nl.petrichor.app.data.recommendation

import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.sqrt

internal data class ComfortPrediction(
    val rating: RatingPrediction,
    val thermal: ThermalProbabilities,
    val combinedScore: Double,
)

/**
 * Combines [BayesianRatingModel] and [BayesianThermalModel] into a unified comfort predictor and
 * provides [BayesianModelTrainer] with deterministic chronological replay on edits and single-step
 * incremental updates on monotonic appends.
 */
internal object BayesianComfortModel {
    internal const val ALPHA_THERMAL_WEIGHT = 0.45
    internal const val RATING_SPAN = 4.0
    private const val BETA_RISK_PENALTY = 0.15

    fun predict(
        ratingPosterior: RatingPosterior,
        thermalPosterior: ThermalPosterior,
        raw: RawOutfitSignals,
        features: DoubleArray,
        unseenPieceCount: Int = 0,
    ): ComfortPrediction {
        val rating = BayesianRatingModel.predict(ratingPosterior, raw, features, unseenPieceCount = unseenPieceCount)
        val thermal = BayesianThermalModel.predict(thermalPosterior, raw, features, unseenPieceCount = unseenPieceCount)
        return ComfortPrediction(rating = rating, thermal = thermal, combinedScore = combineScores(rating, thermal))
    }

    fun predict(
        model: DecodedModel,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): ComfortPrediction {
        val extracted = FeatureExtractor.extractForModel(model, tempC, isRainy, pieces, typeById)
        return predict(
            model.ratingPosterior,
            model.thermalPosterior,
            extracted.raw,
            extracted.features,
            unseenPieceCount = extracted.unseenPieceIds.size,
        )
    }

    fun combineScores(
        rating: RatingPrediction,
        thermal: ThermalProbabilities,
    ): Double {
        val normalisedRating = ((rating.mean - 1.0) / RATING_SPAN).coerceIn(0.0, 1.0)
        val ratingRisk = rating.epistemicStd / 2.0
        val thermalRisk = thermal.epistemicStd
        val sigmaRisk = sqrt(0.5 * (ratingRisk * ratingRisk + thermalRisk * thermalRisk))
        val unitScore =
            ALPHA_THERMAL_WEIGHT * thermal.perfect +
                (1.0 - ALPHA_THERMAL_WEIGHT) * normalisedRating -
                BETA_RISK_PENALTY * sigmaRisk
        return (1.0 + RATING_SPAN * unitScore).coerceIn(1.0, 5.0)
    }
}

internal object BayesianModelTrainer {
    private const val MIN_FROZEN_SCALER_OBSERVATIONS = 10
    private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325
    private const val FNV_PRIME = 1099511628211L

    private val chronologicalOrder =
        compareBy<OutfitEntryWithPieces>(
            { it.entry.wornOnDate },
            { it.entry.surveyedAtEpochMs ?: Long.MAX_VALUE },
            { it.entry.createdAtEpochMs },
            { it.entry.id },
        )

    fun isUsableTrainingEntry(item: OutfitEntryWithPieces): Boolean =
        item.hasKnownPieces &&
            entryTemperature(item.entry) != null &&
            item.entry.surveyFeelingScore != null

    fun train(snapshot: TrainingSnapshot): ModelState? {
        val typeById = snapshot.clothingTypes.associateBy { it.id }
        val usable = snapshot.entries.filter(::isUsableTrainingEntry).sortedWith(chronologicalOrder)
        if (usable.isEmpty()) return null

        val activePieceIds = FeatureExtractor.selectActivePieceIds(usable)
        val previousDecoded = DecodedModel.decode(snapshot.previousState)
        val artifact =
            if (canApplyIncrementalStep(previousDecoded, usable, typeById, activePieceIds)) {
                updateIncrementally(previousDecoded!!, usable, typeById, snapshot.dataRevision)
            } else {
                replayFromPrior(usable, typeById, activePieceIds, snapshot.dataRevision)
            }
        return ModelState(
            coefficientsJson = Json.encodeToString(artifact),
            trainedAtEpochMs = snapshot.trainedAtEpochMs,
            sampleCount = usable.size,
        )
    }

    private fun canApplyIncrementalStep(
        previous: DecodedModel?,
        usable: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
        activePieceIds: List<Long>,
    ): Boolean =
        previous != null &&
            previous.observationCount >= MIN_FROZEN_SCALER_OBSERVATIONS &&
            usable.size == previous.observationCount + 1 &&
            usable.last().entry.id != previous.lastObservationEntryId &&
            previous.activePieceIds == activePieceIds &&
            computeHistoryFingerprint(usable.dropLast(1), typeById) == previous.historyFingerprint

    private fun updateIncrementally(
        previous: DecodedModel,
        usable: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
        dataRevision: Long,
    ): RecommendationModelArtifact {
        val newest = usable.last()
        val raw = extractTrainingRaw(newest, typeById)
        val deltaDays = (newest.entry.wornOnDate.toEpochDays() - previous.lastObservationEpochDays).coerceAtLeast(0L).toDouble()
        val (nextRating, nextThermal) =
            applyObservation(
                rating = previous.ratingPosterior,
                thermal = previous.thermalPosterior,
                item = newest,
                raw = raw,
                scaler = previous.scaler,
                activePieceIndexById = previous.activePieceIndexById,
                deltaDays = deltaDays,
            )
        return buildArtifact(previous.scaler, nextRating, nextThermal, previous.activePieceIds, usable, typeById, dataRevision)
    }

    private fun replayFromPrior(
        usable: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
        activePieceIds: List<Long>,
        dataRevision: Long,
    ): RecommendationModelArtifact {
        val activePieceIndexById = activePieceIds.withIndex().associate { (index, pieceId) -> pieceId to index }
        val rawList = usable.map { item -> extractTrainingRaw(item, typeById) }
        val scaler = FeatureScaler.fit(rawList)
        val dimension = FeatureExtractor.CORE_FEATURE_COUNT + activePieceIds.size
        var rating = BayesianRatingModel.prior(dimension)
        var thermal = BayesianThermalModel.prior(dimension)

        var previousDays: Long? = null
        usable.forEachIndexed { index, item ->
            val currentDays = item.entry.wornOnDate.toEpochDays()
            val deltaDays = previousDays?.let { (currentDays - it).coerceAtLeast(0L).toDouble() } ?: 0.0
            previousDays = currentDays
            val (nextRating, nextThermal) =
                applyObservation(rating, thermal, item, rawList[index], scaler, activePieceIndexById, deltaDays)
            rating = nextRating
            thermal = nextThermal
        }

        return buildArtifact(scaler, rating, thermal, activePieceIds, usable, typeById, dataRevision)
    }

    @Suppress("LongParameterList") // single observation update shared by replay and incremental paths
    private fun applyObservation(
        rating: RatingPosterior,
        thermal: ThermalPosterior,
        item: OutfitEntryWithPieces,
        raw: RawOutfitSignals,
        scaler: FeatureScaler,
        activePieceIndexById: Map<Long, Int>,
        deltaDays: Double,
    ): Pair<RatingPosterior, ThermalPosterior> {
        val entry = item.entry
        val features = FeatureExtractor.buildFeatures(raw, scaler, item.pieces, activePieceIndexById)
        val residual = entry.surveyFeelingScore!!.toDouble() - BayesianRatingModel.priorPrediction(raw)
        val nextRating =
            BayesianRatingModel.update(
                posterior = rating,
                features = features,
                residual = residual,
                deltaDays = deltaDays,
            )
        val nextThermal =
            entry.surveyWarmthVerdict?.let { verdict ->
                BayesianThermalModel.update(
                    posterior = thermal,
                    raw = raw,
                    features = features,
                    verdict = verdict,
                )
            } ?: thermal
        return nextRating to nextThermal
    }

    private fun extractTrainingRaw(
        item: OutfitEntryWithPieces,
        typeById: Map<Long, ClothingType>,
    ): RawOutfitSignals {
        val entry = item.entry
        return FeatureExtractor.extractRaw(
            tempC = entryTemperature(entry)!!,
            isRainy = entry.isRainy,
            pieces = item.pieces,
            typeById = typeById,
        )
    }

    @Suppress("LongParameterList") // artefact builder aggregates posterior state and replay metadata
    private fun buildArtifact(
        scaler: FeatureScaler,
        rating: RatingPosterior,
        thermal: ThermalPosterior,
        activePieceIds: List<Long>,
        usable: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
        dataRevision: Long,
    ): RecommendationModelArtifact {
        val last = usable.last()
        return RecommendationModelArtifact(
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            dataRevision = dataRevision,
            scaler = scaler,
            ratingMean = DecodedModel.flattenVector(rating.mean),
            ratingCovariance = DecodedModel.flattenMatrix(rating.covariance),
            thermalMean = DecodedModel.flattenVector(thermal.mean),
            thermalCovariance = DecodedModel.flattenMatrix(thermal.covariance),
            thermalThreshold = thermal.thresholdTau,
            activePieceIds = activePieceIds,
            observationCount = usable.size,
            lastObservationEpochDays = last.entry.wornOnDate.toEpochDays(),
            lastObservationEntryId = last.entry.id,
            historyFingerprint = computeHistoryFingerprint(usable, typeById),
        )
    }

    internal fun computeHistoryFingerprint(
        entries: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
    ): Long {
        var hash = FNV_OFFSET_BASIS

        fun mix(value: Long) {
            hash = (hash xor value) * FNV_PRIME
        }
        for (item in entries) {
            val e = item.entry
            mix(e.id)
            mix(e.wornOnDate.toEpochDays())
            mix(e.surveyedAtEpochMs ?: -1L)
            mix(entryTemperature(e)?.toBits() ?: 0L)
            mix(if (e.isRainy) 1L else 0L)
            mix((e.surveyFeelingScore ?: -1).toLong())
            mix((e.surveyWarmthVerdict?.ordinal ?: -1).toLong())
            for (piece in item.pieces.sortedBy { it.id }) {
                mix(piece.id)
                mix(piece.typeId)
                mix(piece.material.ordinal.toLong())
                mix((typeById[piece.typeId]?.supertype?.ordinal ?: -1).toLong())
            }
        }
        return hash
    }
}
