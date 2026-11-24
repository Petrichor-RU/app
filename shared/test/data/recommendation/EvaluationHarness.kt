package nl.petrichor.app.data.recommendation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

internal enum class PersonaKind {
    COLD_SENSITIVE,
    WARM_RUNNING,
    RAIN_AVERSE,
    SPARSE_COLLINEAR,
}

internal data class SliceMetrics(
    val count: Int,
    val mae: Double,
    val rmse: Double,
)

internal data class ThermalCalibrationMetrics(
    val count: Int,
    val ordinalLogLoss: Double,
    val brierScore: Double,
    val expectedCalibrationError: Double,
    val misclassificationRate: Double,
    val extremePolarityErrorRate: Double,
)

internal data class DimensionLatencyReport(
    val dimension: Int,
    val updateP50Ms: Double,
    val updateP95Ms: Double,
    val rankingP50Ms: Double,
    val rankingP95Ms: Double,
)

internal data class EvaluationReport(
    val policyName: String,
    val overall: SliceMetrics,
    val sparseHistory: SliceMetrics,
    val richHistory: SliceMetrics,
    val repeatedOutfits: SliceMetrics,
    val unseenOutfits: SliceMetrics,
    val inRangeTemps: SliceMetrics,
    val outOfRangeTemps: SliceMetrics,
    val thermal: ThermalCalibrationMetrics,
    val weatherReversalAccuracy: Double,
    val updateLatencyP50Ms: Double,
    val updateLatencyP95Ms: Double,
    val rankingLatencyP50Ms: Double,
    val rankingLatencyP95Ms: Double,
)

internal data class HoldoutEvaluationReport(
    val policyName: String,
    val rating: SliceMetrics,
    val thermal: ThermalCalibrationMetrics,
)

internal enum class EvaluationPolicy {
    HEURISTIC,
    ADDITIVE_LINEAR_V1,
    INTERACTION_RIDGE_V2,
    BAYESIAN_RATING_V3,
    BAYESIAN_COMBINED_V3,
}

/**
 * Pure common chronological (prequential) evaluation harness: sorts logged entries by wear/survey
 * timestamp and scores observation `t` strictly from entries `[0 until t]`.
 */
@Suppress("TooManyFunctions") // cohesive prequential evaluation + calibration + latency suite
internal object EvaluationHarness {
    private const val LATENCY_CANDIDATE_COUNT = 120
    private const val BENCHMARK_UPDATES = 40
    private const val BENCHMARK_RANK_RUNS = 20
    private const val ECE_BINS = 5
    private const val MIN_PROB_CLAMP = 1e-12

    private val entryOrder =
        compareBy<OutfitEntryWithPieces>(
            { it.entry.wornOnDate },
            { it.entry.surveyedAtEpochMs ?: Long.MAX_VALUE },
            { it.entry.createdAtEpochMs },
            { it.entry.id },
        )

    fun evaluate(
        dataset: SyntheticDataset,
        policy: EvaluationPolicy,
    ): EvaluationReport {
        val sorted =
            dataset.entries
                .filter { it.entry.surveyFeelingScore != null && entryTemperature(it.entry) != null }
                .sortedWith(entryOrder)
        val typeById = dataset.types.associateBy { it.id }
        val records = mutableListOf<ObservationError>()
        val thermalRecords = mutableListOf<ThermalObservationError>()
        val updateLatenciesMs = mutableListOf<Double>()
        val rankingLatenciesMs = mutableListOf<Double>()

        for (t in sorted.indices) {
            val current = sorted[t]
            val targetScore = current.entry.surveyFeelingScore!!.toDouble()
            val tempC = entryTemperature(current.entry)!!
            val history = sorted.subList(0, t)

            val (modelRef, updateDuration) = fitPolicyTimed(policy, history, dataset.types, typeById)
            if (history.isNotEmpty()) updateLatenciesMs += durationMs(updateDuration)

            val (stepPred, rankDuration) =
                predictStepTimed(policy, modelRef, dataset, history, current, tempC, typeById)
            rankingLatenciesMs += durationMs(rankDuration)
            check(stepPred.score.isFinite()) { "Non-finite prediction in $policy at step $t: ${stepPred.score}" }

            records += buildObservationError(history, current, tempC, stepPred.ratingEstimate, targetScore)
            current.entry.surveyWarmthVerdict?.let { actualVerdict ->
                thermalRecords += buildThermalError(stepPred.thermal, actualVerdict)
            }
        }

        val reversalAccuracy = measureWeatherReversal(policy, sorted, dataset, typeById)
        return EvaluationReport(
            policyName = policy.name,
            overall = aggregate(records),
            sparseHistory = aggregate(records.filter { it.historyCount < RecommendationEngine.MODEL_MIN_SURVEYED }),
            richHistory = aggregate(records.filter { it.historyCount >= RecommendationEngine.MODEL_MIN_SURVEYED }),
            repeatedOutfits = aggregate(records.filter { it.isRepeated }),
            unseenOutfits = aggregate(records.filter { !it.isRepeated }),
            inRangeTemps = aggregate(records.filter { it.inRange }),
            outOfRangeTemps = aggregate(records.filter { !it.inRange }),
            thermal = aggregateThermal(thermalRecords),
            weatherReversalAccuracy = reversalAccuracy,
            updateLatencyP50Ms = percentile(updateLatenciesMs, 0.50),
            updateLatencyP95Ms = percentile(updateLatenciesMs, 0.95),
            rankingLatencyP50Ms = percentile(rankingLatenciesMs, 0.50),
            rankingLatencyP95Ms = percentile(rankingLatenciesMs, 0.95),
        )
    }

    fun evaluateHoldout(
        trainHistory: List<OutfitEntryWithPieces>,
        holdoutEntries: List<OutfitEntryWithPieces>,
        types: List<ClothingType>,
        wardrobe: List<ClothingPiece>,
        policy: EvaluationPolicy,
    ): HoldoutEvaluationReport {
        val sortedTrain =
            trainHistory
                .filter { it.entry.surveyFeelingScore != null && entryTemperature(it.entry) != null }
                .sortedWith(entryOrder)
        val typeById = types.associateBy { it.id }
        val (modelRef, _) = fitPolicyTimed(policy, sortedTrain, types, typeById)
        val dataset = SyntheticDataset(wardrobe, types, sortedTrain, emptyList<ClothingPiece>() to emptyList())
        val ratingRecords = mutableListOf<ObservationError>()
        val thermalRecords = mutableListOf<ThermalObservationError>()

        val validHoldout =
            holdoutEntries.filter { it.entry.surveyFeelingScore != null && entryTemperature(it.entry) != null }
        for (item in validHoldout) {
            val e = item.entry
            val tempC = entryTemperature(e)!!
            val targetScore = e.surveyFeelingScore!!.toDouble()
            val pred =
                scoreCandidateForPolicy(
                    policy = policy,
                    modelRef = modelRef,
                    dataset = dataset,
                    history = sortedTrain,
                    pieces = item.pieces,
                    tempC = tempC,
                    isRainy = e.isRainy,
                    date = e.wornOnDate,
                    typeById = typeById,
                    windSpeedMs = e.windSpeedMs,
                    precipitationProbability = e.precipitationProbability,
                    rainVolumeMm = e.rainVolumeMm,
                )
            check(pred.score.isFinite()) { "Non-finite prediction in $policy for holdout entry ${e.id}" }
            ratingRecords += buildObservationError(sortedTrain, item, tempC, pred.ratingEstimate, targetScore)
            e.surveyWarmthVerdict?.let { verdict ->
                thermalRecords += buildThermalError(pred.thermal, verdict)
            }
        }
        return HoldoutEvaluationReport(
            policyName = policy.name,
            rating = aggregate(ratingRecords),
            thermal = aggregateThermal(thermalRecords),
        )
    }

    /**
     * Measures Kalman + ADF model update latency and 120-candidate scoring latency at arbitrary
     * feature dimension `d` (used for runtime budget verification at `d = 16` and `d = 32`).
     */
    fun benchmarkDimensionLatency(
        dimension: Int,
        seed: Int = 7,
    ): DimensionLatencyReport {
        val rng = Random(seed)
        var rating = BayesianRatingModel.prior(dimension)
        var thermal = BayesianThermalModel.prior(dimension)
        val raw = RawOutfitSignals(12.0, false, 0.8, 0.4, 0.1, 2.0)
        val sampleFeatures = List(BENCHMARK_UPDATES) { DoubleArray(dimension) { idx -> if (idx == 0) 1.0 else rng.nextDouble(-1.5, 1.5) } }
        val verdicts = WarmthVerdict.entries
        val updateMs = mutableListOf<Double>()
        sampleFeatures.forEachIndexed { idx, features ->
            val mark = TimeSource.Monotonic.markNow()
            rating = BayesianRatingModel.update(rating, features, rng.nextDouble(-1.0, 1.0), deltaDays = 1.0)
            thermal = BayesianThermalModel.update(thermal, raw, features, verdicts[idx % verdicts.size])
            updateMs += durationMs(mark.elapsedNow())
        }

        val candidates = List(LATENCY_CANDIDATE_COUNT) { idx -> sampleFeatures[idx % sampleFeatures.size] }
        val rankingMs = mutableListOf<Double>()
        repeat(BENCHMARK_RANK_RUNS) {
            val mark = TimeSource.Monotonic.markNow()
            for (features in candidates) {
                val pred = BayesianComfortModel.predict(rating, thermal, raw, features)
                check(pred.combinedScore.isFinite())
            }
            rankingMs += durationMs(mark.elapsedNow())
        }
        return DimensionLatencyReport(
            dimension = dimension,
            updateP50Ms = percentile(updateMs, 0.50),
            updateP95Ms = percentile(updateMs, 0.95),
            rankingP50Ms = percentile(rankingMs, 0.50),
            rankingP95Ms = percentile(rankingMs, 0.95),
        )
    }

    private fun buildObservationError(
        history: List<OutfitEntryWithPieces>,
        current: OutfitEntryWithPieces,
        tempC: Double,
        predictedRating: Double,
        targetScore: Double,
    ): ObservationError {
        val seenOutfits = history.map { h -> h.pieces.map { it.id }.sorted() }.toSet()
        val isRepeated = current.pieces.map { it.id }.sorted() in seenOutfits
        val historyTemps = history.mapNotNull { entryTemperature(it.entry) }
        val inRange =
            historyTemps.isNotEmpty() &&
                tempC >= (historyTemps.min() - TEMP_MARGIN_C) &&
                tempC <= (historyTemps.max() + TEMP_MARGIN_C)
        return ObservationError(
            error = abs(predictedRating - targetScore),
            squaredError = (predictedRating - targetScore) * (predictedRating - targetScore),
            historyCount = history.size,
            isRepeated = isRepeated,
            inRange = inRange,
        )
    }

    private fun buildThermalError(
        probs: ThermalProbabilities,
        actual: WarmthVerdict,
    ): ThermalObservationError {
        val pActual =
            when (actual) {
                WarmthVerdict.TOO_COLD -> probs.tooCold
                WarmthVerdict.PERFECT -> probs.perfect
                WarmthVerdict.TOO_WARM -> probs.tooWarm
            }.coerceAtLeast(MIN_PROB_CLAMP)
        val brier =
            squaredIndicatorDiff(probs.tooCold, actual == WarmthVerdict.TOO_COLD) +
                squaredIndicatorDiff(probs.perfect, actual == WarmthVerdict.PERFECT) +
                squaredIndicatorDiff(probs.tooWarm, actual == WarmthVerdict.TOO_WARM)
        val predicted =
            when {
                probs.tooCold >= probs.perfect && probs.tooCold >= probs.tooWarm -> WarmthVerdict.TOO_COLD
                probs.tooWarm >= probs.perfect && probs.tooWarm >= probs.tooCold -> WarmthVerdict.TOO_WARM
                else -> WarmthVerdict.PERFECT
            }
        val confidence = maxOf(probs.tooCold, probs.perfect, probs.tooWarm)
        val polarityFlip =
            (predicted == WarmthVerdict.TOO_COLD && actual == WarmthVerdict.TOO_WARM) ||
                (predicted == WarmthVerdict.TOO_WARM && actual == WarmthVerdict.TOO_COLD)
        return ThermalObservationError(
            logLoss = -kotlin.math.ln(pActual),
            brier = brier,
            correct = predicted == actual,
            confidence = confidence,
            polarityFlip = polarityFlip,
        )
    }

    private fun squaredIndicatorDiff(
        probability: Double,
        indicator: Boolean,
    ): Double {
        val target = if (indicator) 1.0 else 0.0
        val diff = probability - target
        return diff * diff
    }

    private fun fitPolicyTimed(
        policy: EvaluationPolicy,
        history: List<OutfitEntryWithPieces>,
        clothingTypes: List<ClothingType>,
        typeById: Map<Long, ClothingType>,
    ): Pair<Any?, Duration> {
        val mark = TimeSource.Monotonic.markNow()
        val fitted: Any? =
            when {
                history.isEmpty() -> null
                policy == EvaluationPolicy.HEURISTIC -> null
                policy == EvaluationPolicy.ADDITIVE_LINEAR_V1 -> LegacyAdditiveModel.fit(history, typeById)
                policy == EvaluationPolicy.INTERACTION_RIDGE_V2 -> fitRidgeV2(history, typeById)
                else -> fitBayesianV3(history, clothingTypes)
            }
        return fitted to mark.elapsedNow()
    }

    private fun fitBayesianV3(
        history: List<OutfitEntryWithPieces>,
        clothingTypes: List<ClothingType>,
    ): DecodedModel? {
        val snapshot =
            TrainingSnapshot(
                entries = history,
                clothingTypes = clothingTypes,
                dataRevision = history.size.toLong(),
                trainedAtEpochMs = history.lastOrNull()?.entry?.surveyedAtEpochMs ?: 0L,
            )
        return DecodedModel.decode(BayesianModelTrainer.train(snapshot))
    }

    private fun fitRidgeV2(
        history: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
    ): DecodedModel? {
        val samples =
            history.mapNotNull { item ->
                val e = item.entry
                val temp = entryTemperature(e) ?: return@mapNotNull null
                val score = e.surveyFeelingScore?.toDouble() ?: return@mapNotNull null
                FeatureExtractor.extractRaw(
                    tempC = temp,
                    isRainy = e.isRainy,
                    pieces = item.pieces,
                    typeById = typeById,
                    windSpeedMs = e.windSpeedMs,
                    precipitationProbability = e.precipitationProbability,
                    rainVolumeMm = e.rainVolumeMm,
                ) to score
            }
        if (samples.isEmpty()) return null
        val artifact = RidgeRatingModel.fit(samples)
        val state =
            ModelState(
                schemaVersion = ModelState.CURRENT_SCHEMA_VERSION,
                dataRevision = 1L,
                trainedAtEpochMs = 0L,
                usableSampleCount = samples.size,
                artifactJson = Json.encodeToString(artifact),
            )
        return DecodedModel.decode(state)
    }

    @Suppress("LongParameterList") // benchmark helper forwards context slices directly
    private fun predictStepTimed(
        policy: EvaluationPolicy,
        modelRef: Any?,
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        current: OutfitEntryWithPieces,
        tempC: Double,
        typeById: Map<Long, ClothingType>,
    ): Pair<StepPrediction, Duration> {
        val mark = TimeSource.Monotonic.markNow()
        val e = current.entry
        val predicted =
            scoreCandidateForPolicy(
                policy = policy,
                modelRef = modelRef,
                dataset = dataset,
                history = history,
                pieces = current.pieces,
                tempC = tempC,
                isRainy = e.isRainy,
                windSpeedMs = e.windSpeedMs,
                precipitationProbability = e.precipitationProbability,
                rainVolumeMm = e.rainVolumeMm,
                date = e.wornOnDate,
                typeById = typeById,
            )
        return predicted to mark.elapsedNow()
    }

    @Suppress("LongParameterList") // shared scoring helper across step evaluation and reversal probe
    private fun scoreCandidateForPolicy(
        policy: EvaluationPolicy,
        modelRef: Any?,
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        date: LocalDate,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double? = null,
        precipitationProbability: Double? = null,
        rainVolumeMm: Double? = null,
    ): StepPrediction {
        val raw =
            FeatureExtractor.extractRaw(
                tempC = tempC,
                isRainy = isRainy,
                pieces = pieces,
                typeById = typeById,
                windSpeedMs = windSpeedMs,
                precipitationProbability = precipitationProbability,
                rainVolumeMm = rainVolumeMm,
            )
        return when (policy) {
            EvaluationPolicy.HEURISTIC,
            EvaluationPolicy.ADDITIVE_LINEAR_V1,
            -> {
                heuristicAndAdditiveStepPrediction(policy, modelRef, dataset, history, raw, pieces, tempC, isRainy, date, typeById)
            }

            EvaluationPolicy.INTERACTION_RIDGE_V2 -> {
                interactionRidgeStepPrediction(
                    model = modelRef as? DecodedModel,
                    dataset = dataset,
                    history = history,
                    raw = raw,
                    pieces = pieces,
                    tempC = tempC,
                    isRainy = isRainy,
                    date = date,
                    typeById = typeById,
                    windSpeedMs = windSpeedMs,
                    precipitationProbability = precipitationProbability,
                    rainVolumeMm = rainVolumeMm,
                )
            }

            EvaluationPolicy.BAYESIAN_RATING_V3,
            EvaluationPolicy.BAYESIAN_COMBINED_V3,
            -> {
                bayesianStepPrediction(
                    policy = policy,
                    model = modelRef as? DecodedModel,
                    raw = raw,
                    pieces = pieces,
                    tempC = tempC,
                    isRainy = isRainy,
                    typeById = typeById,
                    windSpeedMs = windSpeedMs,
                    precipitationProbability = precipitationProbability,
                    rainVolumeMm = rainVolumeMm,
                )
            }
        }
    }

    @Suppress("LongParameterList") // dispatches legacy and heuristic policies
    private fun heuristicAndAdditiveStepPrediction(
        policy: EvaluationPolicy,
        modelRef: Any?,
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        raw: RawOutfitSignals,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        date: LocalDate,
        typeById: Map<Long, ClothingType>,
    ): StepPrediction {
        val score =
            if (policy == EvaluationPolicy.HEURISTIC) {
                val input = heuristicInput(dataset, history, tempC, isRainy, date)
                val candidate = OutfitCandidate(null, pieces, null, null, RecommendationSource.HEURISTIC)
                Scoring.heuristicScore(input, candidate, typeById)
            } else {
                val coeffs = modelRef as? DoubleArray
                if (coeffs == null) {
                    RidgeRatingModel.priorPrediction(raw)
                } else {
                    LegacyAdditiveModel.predict(coeffs, tempC, isRainy, pieces, typeById)
                }
            }
        val probs = heuristicThermalBaseline(dataset, history, pieces, tempC, isRainy, date, typeById)
        return StepPrediction(score = score, ratingEstimate = score, thermal = probs)
    }

    @Suppress("LongParameterList") // forwards weather context to RidgeRatingModel
    private fun interactionRidgeStepPrediction(
        model: DecodedModel?,
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        raw: RawOutfitSignals,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        date: LocalDate,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double?,
        precipitationProbability: Double?,
        rainVolumeMm: Double?,
    ): StepPrediction {
        val score =
            if (model == null) {
                RidgeRatingModel.priorPrediction(raw)
            } else {
                RidgeRatingModel.predict(
                    model = model,
                    tempC = tempC,
                    isRainy = isRainy,
                    pieces = pieces,
                    typeById = typeById,
                    windSpeedMs = windSpeedMs,
                    precipitationProbability = precipitationProbability,
                    rainVolumeMm = rainVolumeMm,
                )
            }
        val probs = heuristicThermalBaseline(dataset, history, pieces, tempC, isRainy, date, typeById)
        return StepPrediction(score = score, ratingEstimate = score, thermal = probs)
    }

    @Suppress("LongParameterList") // forwards weather context to BayesianComfortModel
    private fun bayesianStepPrediction(
        policy: EvaluationPolicy,
        model: DecodedModel?,
        raw: RawOutfitSignals,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double?,
        precipitationProbability: Double?,
        rainVolumeMm: Double?,
    ): StepPrediction {
        val comfort =
            model?.let {
                BayesianComfortModel.predict(
                    model = it,
                    tempC = tempC,
                    isRainy = isRainy,
                    pieces = pieces,
                    typeById = typeById,
                    windSpeedMs = windSpeedMs,
                    precipitationProbability = precipitationProbability,
                    rainVolumeMm = rainVolumeMm,
                )
            } ?: priorComfortPrediction(raw)
        val rankingScore =
            if (policy == EvaluationPolicy.BAYESIAN_COMBINED_V3) comfort.combinedScore else comfort.rating.mean
        return StepPrediction(score = rankingScore, ratingEstimate = comfort.rating.mean, thermal = comfort.thermal)
    }

    private fun priorComfortPrediction(raw: RawOutfitSignals): ComfortPrediction {
        val scaler =
            FeatureScaler(
                tempCMean = raw.tempC,
                tempCStd = 1.0,
                warmthUpperMean = raw.warmthUpper,
                warmthUpperStd = 1.0,
                warmthLowerMean = raw.warmthLower,
                warmthLowerStd = 1.0,
                warmthHeadAndFeetMean = raw.warmthHeadAndFeet,
                warmthHeadAndFeetStd = 1.0,
                layerCountUpperMean = raw.layerCountUpper,
                layerCountUpperStd = 1.0,
                totalWarmthMean = raw.totalWarmth,
                totalWarmthStd = 1.0,
            )
        val features = FeatureExtractor.buildCoreFeatures(raw, scaler)
        return BayesianComfortModel.predict(
            ratingPosterior = BayesianRatingModel.prior(),
            thermalPosterior = BayesianThermalModel.prior(),
            raw = raw,
            features = features,
        )
    }

    @Suppress("LongParameterList") // baseline thermal calibration helper
    private fun heuristicThermalBaseline(
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        date: LocalDate,
        typeById: Map<Long, ClothingType>,
    ): ThermalProbabilities {
        val ideal = Scoring.idealWarmth(heuristicInput(dataset, history, tempC, isRainy, date), typeById)
        val mu = 2.0 * (outfitWarmth(pieces, typeById) - ideal)
        val tau = BayesianThermalModel.DEFAULT_THRESHOLD
        val cdfCold = StatsFacade.standardNormalCdf(-tau - mu).coerceIn(0.0, 1.0)
        val cdfWarm = StatsFacade.standardNormalCdf(tau - mu).coerceIn(cdfCold, 1.0)
        return ThermalProbabilities(
            tooCold = cdfCold,
            perfect = (cdfWarm - cdfCold).coerceIn(0.0, 1.0),
            tooWarm = (1.0 - cdfWarm).coerceIn(0.0, 1.0),
            latentMean = mu,
            latentStd = 1.0,
            epistemicStd = 0.0,
        )
    }

    private fun heuristicInput(
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        tempC: Double,
        isRainy: Boolean,
        date: LocalDate,
    ) = RecommendationInput(
        date = date,
        today = date,
        tempC = tempC,
        isRainy = isRainy,
        wardrobePieces = dataset.wardrobe,
        clothingTypes = dataset.types,
        entries = history,
        surveyedCount = history.size,
        modelState = null,
    )

    private fun measureWeatherReversal(
        policy: EvaluationPolicy,
        history: List<OutfitEntryWithPieces>,
        dataset: SyntheticDataset,
        typeById: Map<Long, ClothingType>,
    ): Double {
        if (history.isEmpty()) return 0.0
        val (modelRef, _) = fitPolicyTimed(policy, history, dataset.types, typeById)
        val (winterOutfit, summerOutfit) = dataset.coldPairTest
        val probeDate = history.last().entry.wornOnDate
        val winterAt4 = scoreCandidateForPolicy(policy, modelRef, dataset, history, winterOutfit, 4.0, false, probeDate, typeById).score
        val summerAt4 = scoreCandidateForPolicy(policy, modelRef, dataset, history, summerOutfit, 4.0, false, probeDate, typeById).score
        val winterAt26 = scoreCandidateForPolicy(policy, modelRef, dataset, history, winterOutfit, 26.0, false, probeDate, typeById).score
        val summerAt26 = scoreCandidateForPolicy(policy, modelRef, dataset, history, summerOutfit, 26.0, false, probeDate, typeById).score
        val coldCorrect = if (winterAt4 > summerAt4) 1.0 else 0.0
        val warmCorrect = if (summerAt26 > winterAt26) 1.0 else 0.0
        return (coldCorrect + warmCorrect) / 2.0
    }

    private fun aggregate(items: List<ObservationError>): SliceMetrics {
        if (items.isEmpty()) return SliceMetrics(count = 0, mae = 0.0, rmse = 0.0)
        val mae = items.sumOf { it.error } / items.size
        val rmse = sqrt(items.sumOf { it.squaredError } / items.size)
        return SliceMetrics(count = items.size, mae = mae, rmse = rmse)
    }

    private fun aggregateThermal(items: List<ThermalObservationError>): ThermalCalibrationMetrics {
        if (items.isEmpty()) {
            return ThermalCalibrationMetrics(0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val bins = Array(ECE_BINS) { mutableListOf<ThermalObservationError>() }
        for (item in items) {
            val binIdx = (item.confidence * ECE_BINS).toInt().coerceIn(0, ECE_BINS - 1)
            bins[binIdx].add(item)
        }
        val ece =
            bins.sumOf { bin ->
                if (bin.isEmpty()) {
                    0.0
                } else {
                    val acc = bin.count { it.correct }.toDouble() / bin.size
                    val conf = bin.sumOf { it.confidence } / bin.size
                    (bin.size.toDouble() / items.size) * abs(acc - conf)
                }
            }
        return ThermalCalibrationMetrics(
            count = items.size,
            ordinalLogLoss = items.sumOf { it.logLoss } / items.size,
            brierScore = items.sumOf { it.brier } / items.size,
            expectedCalibrationError = ece,
            misclassificationRate = items.count { !it.correct }.toDouble() / items.size,
            extremePolarityErrorRate = items.count { it.polarityFlip }.toDouble() / items.size,
        )
    }

    private fun percentile(
        values: List<Double>,
        quantile: Double,
    ): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * quantile).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun durationMs(duration: Duration): Double = duration.inWholeNanoseconds / 1_000_000.0
}

private data class StepPrediction(
    val score: Double,
    val ratingEstimate: Double,
    val thermal: ThermalProbabilities,
)

private data class ObservationError(
    val error: Double,
    val squaredError: Double,
    val historyCount: Int,
    val isRepeated: Boolean,
    val inRange: Boolean,
)

private data class ThermalObservationError(
    val logLoss: Double,
    val brier: Double,
    val correct: Boolean,
    val confidence: Double,
    val polarityFlip: Boolean,
)

internal data class SyntheticDataset(
    val wardrobe: List<ClothingPiece>,
    val types: List<ClothingType>,
    val entries: List<OutfitEntryWithPieces>,
    val coldPairTest: Pair<List<ClothingPiece>, List<ClothingPiece>>,
)

internal object SyntheticPersonas {
    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val shirtType = ClothingType(id = 11, name = "Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 12, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 13, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 14, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val pantsType = ClothingType(id = 15, name = "Pants", supertype = ClothingSupertype.LOWER_BODY)
    private val types = listOf(teeType, shirtType, sweaterType, jacketType, jeansType, pantsType)
    private val typeById = types.associateBy { it.id }
    private val baseDate = LocalDate(2026, 3, 1)

    fun generate(
        kind: PersonaKind,
        seed: Int = 42,
    ): SyntheticDataset {
        val rng = Random(seed)
        val linenTee = piece(1L, teeType.id, Material.LINEN)
        val cottonTee = piece(2L, teeType.id, Material.COTTON)
        val cottonShirt = piece(3L, shirtType.id, Material.COTTON)
        val woolSweater = piece(4L, sweaterType.id, Material.WOOL)
        val woolJacket = piece(5L, jacketType.id, Material.WOOL)
        val linenPants = piece(6L, pantsType.id, Material.LINEN)
        val jeans = piece(7L, jeansType.id, Material.COTTON)
        val woolPants = piece(8L, pantsType.id, Material.WOOL)
        val wardrobe = listOf(linenTee, cottonTee, cottonShirt, woolSweater, woolJacket, linenPants, jeans, woolPants)

        val outfits = candidateOutfits(kind, linenTee, cottonTee, cottonShirt, woolSweater, woolJacket, linenPants, jeans, woolPants)
        val temps = buildTemperatures(kind, rng)
        val entries =
            temps.mapIndexed { idx, tempC ->
                val chosen = if (kind == PersonaKind.SPARSE_COLLINEAR) outfits[idx] else outfits[rng.nextInt(outfits.size)]
                buildSyntheticEntry(kind, idx, tempC, chosen, rng)
            }
        return SyntheticDataset(
            wardrobe = wardrobe,
            types = types,
            entries = entries,
            coldPairTest = listOf(woolJacket, woolSweater, woolPants) to listOf(linenTee, linenPants),
        )
    }

    @Suppress("LongParameterList") // concise test fixture factory over wardrobe items
    private fun candidateOutfits(
        kind: PersonaKind,
        linenTee: ClothingPiece,
        cottonTee: ClothingPiece,
        cottonShirt: ClothingPiece,
        woolSweater: ClothingPiece,
        woolJacket: ClothingPiece,
        linenPants: ClothingPiece,
        jeans: ClothingPiece,
        woolPants: ClothingPiece,
    ): List<List<ClothingPiece>> =
        if (kind == PersonaKind.SPARSE_COLLINEAR) {
            listOf(
                listOf(cottonTee, jeans),
                listOf(cottonTee, jeans),
                listOf(woolJacket, jeans),
                listOf(woolJacket, jeans),
            )
        } else {
            listOf(
                listOf(linenTee, linenPants),
                listOf(cottonTee, linenPants),
                listOf(cottonShirt, jeans),
                listOf(woolSweater, jeans),
                listOf(woolJacket, jeans),
                listOf(cottonTee, woolSweater, jeans),
                listOf(cottonTee, woolJacket, jeans),
                listOf(woolJacket, woolSweater, woolPants),
            )
        }

    private fun buildTemperatures(
        kind: PersonaKind,
        rng: Random,
    ): List<Double> =
        if (kind == PersonaKind.SPARSE_COLLINEAR) {
            listOf(12.0, 12.0, 6.0, 6.0)
        } else {
            List(40) { idx ->
                val base = 2.0 + (idx % 10) * 2.6 + rng.nextDouble(-1.0, 1.0)
                base.coerceIn(0.0, 28.0)
            }
        }

    private fun buildSyntheticEntry(
        kind: PersonaKind,
        idx: Int,
        tempC: Double,
        chosenPieces: List<ClothingPiece>,
        rng: Random,
    ): OutfitEntryWithPieces {
        val isRainy = kind != PersonaKind.SPARSE_COLLINEAR && (idx % 4 == 1)
        val (score, verdict) = simulateOutcome(kind, chosenPieces, tempC, isRainy, rng)
        val date = baseDate.plus(DatePeriod(days = idx))
        val timestamp = 1_770_000_000_000L + idx * 86_400_000L
        return OutfitEntryWithPieces(
            OutfitEntry(
                id = idx + 1L,
                wornOnDate = date,
                photoPath = null,
                notes = null,
                temperatureC = tempC,
                feelsLikeC = tempC,
                minTempC = tempC - 2.0,
                maxTempC = tempC + 2.0,
                weatherDescription = if (isRainy) "Light rain" else "Clear",
                weatherIcon = if (isRainy) "10d" else "01d",
                isRainy = isRainy,
                surveyFeelingScore = score,
                surveyWarmthVerdict = verdict,
                surveyedAtEpochMs = timestamp,
                createdAtEpochMs = timestamp,
            ),
            chosenPieces,
        )
    }

    private fun simulateOutcome(
        kind: PersonaKind,
        pieces: List<ClothingPiece>,
        tempC: Double,
        isRainy: Boolean,
        rng: Random,
    ): Pair<Int, WarmthVerdict> {
        val warmth = outfitWarmth(pieces, typeById)
        val hasJacket = pieces.any { it.typeId == jacketType.id }
        val shift =
            when (kind) {
                PersonaKind.COLD_SENSITIVE -> 0.35
                PersonaKind.WARM_RUNNING -> -0.35
                PersonaKind.RAIN_AVERSE -> 0.10
                PersonaKind.SPARSE_COLLINEAR -> 0.0
            }
        val ideal = (2.35 - 0.08 * tempC + shift + if (isRainy) 0.25 else 0.0).coerceIn(0.1, 3.0)
        val diff = warmth - ideal
        val rainPenalty = if (kind == PersonaKind.RAIN_AVERSE && isRainy && !hasJacket) 1.5 else 0.0
        val noise = if (kind == PersonaKind.SPARSE_COLLINEAR) 0.0 else rng.nextDouble(-0.15, 0.15)
        val rating = (5.0 - 2.2 * abs(diff) - rainPenalty + noise).coerceIn(1.0, 5.0).roundToInt()
        val verdict =
            when {
                diff < -0.35 -> WarmthVerdict.TOO_COLD
                diff > 0.35 -> WarmthVerdict.TOO_WARM
                else -> WarmthVerdict.PERFECT
            }
        return rating to verdict
    }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material,
    ) = ClothingPiece(
        id = id,
        name = "p$id",
        typeId = typeId,
        material = material,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )
}

/**
 * Legacy 8-feature additive linear regression retained strictly inside test code to measure
 * ablation gains of the interaction ridge baseline.
 */
private object LegacyAdditiveModel {
    private const val LAMBDA = 0.5

    fun fit(
        history: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
    ): DoubleArray? {
        val samples =
            history.mapNotNull { item ->
                val temp = entryTemperature(item.entry) ?: return@mapNotNull null
                val score = item.entry.surveyFeelingScore?.toDouble() ?: return@mapNotNull null
                WarmthEncoding.featureVector(temp, item.entry.isRainy, item.pieces, typeById) to score
            }
        if (samples.isEmpty()) return null
        val n = samples.size
        val d = samples.first().first.size
        val xMatrix = mk.zeros<Double>(n, d)
        val yVec = DoubleArray(n)
        samples.forEachIndexed { row, (features, target) ->
            yVec[row] = target
            for (col in 0 until d) {
                xMatrix[row, col] = features[col]
            }
        }
        val xt = xMatrix.transpose()
        val normal = StatsFacade.matMul(xt, xMatrix)
        for (i in 1 until d) {
            normal[i, i] = normal[i, i] + LAMBDA * n
        }
        val rhs = StatsFacade.matVec(xt, mk.ndarray(yVec))
        val solved = StatsFacade.solveLinearSystem(normal, rhs)
        return DoubleArray(d) { i -> solved[i] }
    }

    fun predict(
        coefficients: DoubleArray,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): Double {
        val features = WarmthEncoding.featureVector(tempC, isRainy, pieces, typeById)
        val raw = features.indices.sumOf { i -> coefficients[i] * features[i] }
        return raw.coerceIn(1.0, 5.0)
    }
}
