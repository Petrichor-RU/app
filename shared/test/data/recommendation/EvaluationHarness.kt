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

internal data class EvaluationReport(
    val policyName: String,
    val overall: SliceMetrics,
    val sparseHistory: SliceMetrics,
    val richHistory: SliceMetrics,
    val repeatedOutfits: SliceMetrics,
    val unseenOutfits: SliceMetrics,
    val inRangeTemps: SliceMetrics,
    val outOfRangeTemps: SliceMetrics,
    val weatherReversalAccuracy: Double,
    val updateLatencyP50Ms: Double,
    val updateLatencyP95Ms: Double,
    val rankingLatencyP50Ms: Double,
    val rankingLatencyP95Ms: Double,
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

internal enum class EvaluationPolicy {
    HEURISTIC,
    ADDITIVE_LINEAR_V1,
    INTERACTION_RIDGE_V2,
}

/**
 * Pure common chronological (prequential) evaluation harness: sorts logged entries by wear/survey
 * timestamp and scores observation `t` strictly from entries `[0 until t]`.
 */
internal object EvaluationHarness {
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
        val updateLatenciesMs = mutableListOf<Double>()
        val rankingLatenciesMs = mutableListOf<Double>()

        for (t in sorted.indices) {
            val current = sorted[t]
            val targetScore = current.entry.surveyFeelingScore!!.toDouble()
            val tempC = entryTemperature(current.entry)!!
            val history = sorted.subList(0, t)

            val (modelRef, updateDuration) = fitPolicyTimed(policy, history, typeById)
            if (history.isNotEmpty()) updateLatenciesMs += durationMs(updateDuration)

            val (predicted, rankDuration) =
                predictTimed(policy, modelRef, dataset, history, current, tempC, typeById)
            rankingLatenciesMs += durationMs(rankDuration)
            check(predicted.isFinite()) { "Non-finite prediction in $policy at step $t: $predicted" }

            val seenOutfits = history.map { h -> h.pieces.map { it.id }.sorted() }.toSet()
            val isRepeated = current.pieces.map { it.id }.sorted() in seenOutfits
            val historyTemps = history.mapNotNull { entryTemperature(it.entry) }
            val inRange =
                historyTemps.isNotEmpty() &&
                    tempC >= (historyTemps.min() - TEMP_MARGIN_C) &&
                    tempC <= (historyTemps.max() + TEMP_MARGIN_C)
            records +=
                ObservationError(
                    error = abs(predicted - targetScore),
                    squaredError = (predicted - targetScore) * (predicted - targetScore),
                    historyCount = history.size,
                    isRepeated = isRepeated,
                    inRange = inRange,
                )
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
            weatherReversalAccuracy = reversalAccuracy,
            updateLatencyP50Ms = percentile(updateLatenciesMs, 0.50),
            updateLatencyP95Ms = percentile(updateLatenciesMs, 0.95),
            rankingLatencyP50Ms = percentile(rankingLatenciesMs, 0.50),
            rankingLatencyP95Ms = percentile(rankingLatenciesMs, 0.95),
        )
    }

    private fun fitPolicyTimed(
        policy: EvaluationPolicy,
        history: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
    ): Pair<Any?, Duration> {
        val mark = TimeSource.Monotonic.markNow()
        val fitted: Any? =
            when {
                history.isEmpty() -> null
                policy == EvaluationPolicy.HEURISTIC -> null
                policy == EvaluationPolicy.ADDITIVE_LINEAR_V1 -> LegacyAdditiveModel.fit(history, typeById)
                else -> fitRidgeV2(history, typeById)
            }
        return fitted to mark.elapsedNow()
    }

    private fun fitRidgeV2(
        history: List<OutfitEntryWithPieces>,
        typeById: Map<Long, ClothingType>,
    ): DecodedModel? {
        val samples =
            history.mapNotNull { item ->
                val temp = entryTemperature(item.entry) ?: return@mapNotNull null
                val score = item.entry.surveyFeelingScore?.toDouble() ?: return@mapNotNull null
                FeatureExtractor.extractRaw(temp, item.entry.isRainy, item.pieces, typeById) to score
            }
        if (samples.isEmpty()) return null
        val artifact = RidgeRatingModel.fit(samples)
        val state =
            ModelState(
                schemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
                dataRevision = 1L,
                trainedAtEpochMs = 0L,
                usableSampleCount = samples.size,
                artifactJson = Json.encodeToString(artifact),
            )
        return DecodedModel.decode(state)
    }

    @Suppress("LongParameterList") // benchmark helper forwards context slices directly
    private fun predictTimed(
        policy: EvaluationPolicy,
        modelRef: Any?,
        dataset: SyntheticDataset,
        history: List<OutfitEntryWithPieces>,
        current: OutfitEntryWithPieces,
        tempC: Double,
        typeById: Map<Long, ClothingType>,
    ): Pair<Double, Duration> {
        val mark = TimeSource.Monotonic.markNow()
        val predicted =
            scoreCandidateForPolicy(
                policy = policy,
                modelRef = modelRef,
                dataset = dataset,
                history = history,
                pieces = current.pieces,
                tempC = tempC,
                isRainy = current.entry.isRainy,
                date = current.entry.wornOnDate,
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
    ): Double =
        when (policy) {
            EvaluationPolicy.HEURISTIC -> {
                val input =
                    RecommendationInput(
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
                val candidate = OutfitCandidate(null, pieces, null, null, RecommendationSource.HEURISTIC)
                Scoring.heuristicScore(input, candidate, typeById)
            }

            EvaluationPolicy.ADDITIVE_LINEAR_V1 -> {
                val coeffs = modelRef as? DoubleArray
                if (coeffs == null) {
                    val raw = FeatureExtractor.extractRaw(tempC, isRainy, pieces, typeById)
                    RidgeRatingModel.priorPrediction(raw)
                } else {
                    LegacyAdditiveModel.predict(coeffs, tempC, isRainy, pieces, typeById)
                }
            }

            EvaluationPolicy.INTERACTION_RIDGE_V2 -> {
                val model = modelRef as? DecodedModel
                if (model == null) {
                    val raw = FeatureExtractor.extractRaw(tempC, isRainy, pieces, typeById)
                    RidgeRatingModel.priorPrediction(raw)
                } else {
                    RidgeRatingModel.predict(model, tempC, isRainy, pieces, typeById)
                }
            }
        }

    private fun measureWeatherReversal(
        policy: EvaluationPolicy,
        history: List<OutfitEntryWithPieces>,
        dataset: SyntheticDataset,
        typeById: Map<Long, ClothingType>,
    ): Double {
        if (history.isEmpty()) return 0.0
        val (modelRef, _) = fitPolicyTimed(policy, history, typeById)
        val (winterOutfit, summerOutfit) = dataset.coldPairTest
        val probeDate = history.last().entry.wornOnDate
        val winterAt4 = scoreCandidateForPolicy(policy, modelRef, dataset, history, winterOutfit, 4.0, false, probeDate, typeById)
        val summerAt4 = scoreCandidateForPolicy(policy, modelRef, dataset, history, summerOutfit, 4.0, false, probeDate, typeById)
        val winterAt26 = scoreCandidateForPolicy(policy, modelRef, dataset, history, winterOutfit, 26.0, false, probeDate, typeById)
        val summerAt26 = scoreCandidateForPolicy(policy, modelRef, dataset, history, summerOutfit, 26.0, false, probeDate, typeById)
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

private data class ObservationError(
    val error: Double,
    val squaredError: Double,
    val historyCount: Int,
    val isRepeated: Boolean,
    val inRange: Boolean,
)

/**
 * Legacy 8-feature additive linear regression retained strictly inside test code to measure
 * ablation gains of the Phase 2 interaction ridge baseline.
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
