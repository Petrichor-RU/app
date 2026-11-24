package nl.petrichor.app.data.recommendation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.SurveyContextQuestion
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

internal enum class QueryStrategy {
    NONE,
    RANDOM,
    SELECTIVE,
}

internal data class ActiveLearningResult(
    val strategy: QueryStrategy,
    val questionsAsked: Int,
    val holdoutMae: Double,
    val holdoutLogLoss: Double,
    val maeImprovementPerQuestion: Double,
    val logLossImprovementPerQuestion: Double,
)

internal data class BanditSimulationResult(
    val policyName: String,
    val cumulativeRegret: Double,
    val complaintRate: Double,
    val unsafeExplorations: Int,
    val hajekIpsReward: Double,
    val discoveredHiddenGem: Boolean,
)

/**
 * Deterministic offline simulations comparing:
 * 1. Random vs selective active querying under a fixed question budget.
 * 2. Greedy vs constrained Thompson slates (measuring regret, complaint rate, safety, and IPS).
 */
internal object ActiveLearningAndExplorationSimulation {
    private val baseDate = LocalDate(2026, 9, 1)
    private val teeType = ClothingType(id = 1L, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val shirtType = ClothingType(id = 2L, name = "Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 3L, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 4L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 5L, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val pantsType = ClothingType(id = 6L, name = "Pants", supertype = ClothingSupertype.LOWER_BODY)
    val types = listOf(teeType, shirtType, sweaterType, jacketType, jeansType, pantsType)
    private val typeById = types.associateBy { it.id }

    fun simulateActiveQuerying(maxQuestions: Int = 8): Map<QueryStrategy, ActiveLearningResult> {
        val tee = piece(1L, teeType.id, Material.COTTON)
        val shirt = piece(2L, shirtType.id, Material.COTTON)
        val sweater = piece(3L, sweaterType.id, Material.WOOL)
        val cottonJacket = piece(4L, jacketType.id, Material.COTTON, WeatherProtection.NONE)
        val rainShell = piece(5L, jacketType.id, Material.POLYESTER, WeatherProtection.WATERPROOF)
        val jeans = piece(6L, jeansType.id, Material.COTTON)
        val scenario =
            ActiveScenario(
                wardrobe = listOf(tee, shirt, sweater, cottonJacket, rainShell, jeans),
                seedEntries = buildSeedEntries(tee, sweater, rainShell, jeans),
                streamSpecs = buildContextStreamSpecs(listOf(tee, shirt, sweater, cottonJacket, rainShell, jeans)),
                holdout = buildContextHoldout(listOf(tee, shirt, sweater, cottonJacket, rainShell, jeans)),
            )

        val baselineEval = evaluateWithStrategy(QueryStrategy.NONE, scenario, maxQuestions)
        val results = linkedMapOf(QueryStrategy.NONE to baselineEval)
        for (strategy in listOf(QueryStrategy.RANDOM, QueryStrategy.SELECTIVE)) {
            val raw = evaluateWithStrategy(strategy, scenario, maxQuestions)
            val denom = raw.questionsAsked.coerceAtLeast(1).toDouble()
            results[strategy] =
                raw.copy(
                    maeImprovementPerQuestion = (baselineEval.holdoutMae - raw.holdoutMae) / denom,
                    logLossImprovementPerQuestion = (baselineEval.holdoutLogLoss - raw.holdoutLogLoss) / denom,
                )
        }
        return results
    }

    private fun evaluateWithStrategy(
        strategy: QueryStrategy,
        scenario: ActiveScenario,
        maxQuestions: Int,
    ): ActiveLearningResult {
        val history = scenario.seedEntries.toMutableList()
        var currentState = BayesianModelTrainer.train(TrainingSnapshot(history, types, 1L, 1L))
        var questionsAsked = 0

        scenario.streamSpecs.forEachIndexed { idx, spec ->
            val model = DecodedModel.decode(currentState)
            val (answeredEntry, asked) =
                applyQueryStrategy(strategy, idx, spec, model, history, remainingBudget = maxQuestions - questionsAsked)
            if (asked) questionsAsked++
            history += answeredEntry
            currentState =
                BayesianModelTrainer.train(
                    TrainingSnapshot(
                        entries = history,
                        clothingTypes = types,
                        dataRevision = (idx + 2).toLong(),
                        trainedAtEpochMs = (idx + 2).toLong(),
                        previousState = currentState,
                    ),
                )
        }

        val report =
            EvaluationHarness.evaluateHoldout(
                trainHistory = history,
                holdoutEntries = scenario.holdout,
                types = types,
                wardrobe = scenario.wardrobe,
                policy = EvaluationPolicy.BAYESIAN_COMBINED_V3,
            )
        return ActiveLearningResult(
            strategy = strategy,
            questionsAsked = questionsAsked,
            holdoutMae = report.rating.mae,
            holdoutLogLoss = report.thermal.ordinalLogLoss,
            maeImprovementPerQuestion = 0.0,
            logLossImprovementPerQuestion = 0.0,
        )
    }

    @Suppress("LongParameterList") // dispatches NONE/RANDOM/SELECTIVE query choice with remaining budget
    private fun applyQueryStrategy(
        strategy: QueryStrategy,
        idx: Int,
        spec: ContextStreamSpec,
        model: DecodedModel?,
        history: List<OutfitEntryWithPieces>,
        remainingBudget: Int,
    ): Pair<OutfitEntryWithPieces, Boolean> {
        val rawEntry = spec.baseEntry
        return when (strategy) {
            QueryStrategy.NONE -> {
                rawEntry to false
            }

            QueryStrategy.RANDOM -> {
                if (remainingBudget > 0 && idx % 3 == 0) {
                    applyGroundTruthContext(rawEntry, SurveyContextQuestion.entries[idx % 3], spec) to true
                } else {
                    rawEntry to false
                }
            }

            QueryStrategy.SELECTIVE -> {
                val question =
                    if (remainingBudget > 0) {
                        ActiveQuerySelector.selectFollowUpQuestion(rawEntry, model, types, history)
                    } else {
                        null
                    }
                if (question != null) {
                    applyGroundTruthContext(rawEntry, question, spec) to true
                } else {
                    rawEntry to false
                }
            }
        }
    }

    fun simulateBanditSlates(rounds: Int = 18): Pair<BanditSimulationResult, BanditSimulationResult> {
        val greedy = runBanditSimulation(exploreSlot3 = false, rounds = rounds)
        val thompson = runBanditSimulation(exploreSlot3 = true, rounds = rounds)
        return greedy to thompson
    }

    private fun runBanditSimulation(
        exploreSlot3: Boolean,
        rounds: Int,
    ): BanditSimulationResult {
        val linenTee = piece(1L, teeType.id, Material.LINEN)
        val shirt = piece(2L, shirtType.id, Material.COTTON)
        val sweater = piece(3L, sweaterType.id, Material.WOOL)
        val standardJacket = piece(4L, jacketType.id, Material.COTTON)
        val thinShell = piece(5L, shirtType.id, Material.LINEN)
        val hiddenWarmFleece = piece(6L, jacketType.id, Material.POLYESTER)
        val jeans = piece(7L, jeansType.id, Material.COTTON)
        val linenPants = piece(8L, pantsType.id, Material.LINEN)
        val wardrobe = listOf(linenTee, shirt, sweater, standardJacket, thinShell, hiddenWarmFleece, jeans, linenPants)
        val poolCandidates =
            listOf(
                listOf(linenTee, linenPants),
                listOf(shirt, jeans),
                listOf(sweater, jeans),
                listOf(standardJacket, shirt, jeans),
                listOf(thinShell, sweater, jeans),
                listOf(hiddenWarmFleece, sweater, jeans),
                listOf(standardJacket, sweater, jeans),
            )

        val history = buildBanditSeedHistory(wardrobe).toMutableList()
        var modelState = BayesianModelTrainer.train(TrainingSnapshot(history, types, 1L, 1L))!!
        val stats = BanditAccumulator()

        for (day in 0 until rounds) {
            modelState =
                stepBanditRound(
                    day = day,
                    exploreSlot3 = exploreSlot3,
                    wardrobe = wardrobe,
                    poolCandidates = poolCandidates,
                    unsafePair = linenTee.id to linenPants.id,
                    hiddenFleeceId = hiddenWarmFleece.id,
                    history = history,
                    currentState = modelState,
                    stats = stats,
                )
        }

        val ipsEstimate = if (stats.propensityWeightSum > 0.0) stats.weightedRewardSum / stats.propensityWeightSum else 0.0
        return BanditSimulationResult(
            policyName = if (exploreSlot3) ExplorationPolicy.POLICY_CONSTRAINED_THOMPSON else ExplorationPolicy.POLICY_GREEDY,
            cumulativeRegret = stats.cumulativeRegret,
            complaintRate = stats.complaints.toDouble() / rounds,
            unsafeExplorations = stats.unsafeExplorations,
            hajekIpsReward = ipsEstimate,
            discoveredHiddenGem = stats.discoveredHiddenGem,
        )
    }

    @Suppress("LongParameterList") // single simulation step aggregating regret, safety, and IPS metrics
    private fun stepBanditRound(
        day: Int,
        exploreSlot3: Boolean,
        wardrobe: List<ClothingPiece>,
        poolCandidates: List<List<ClothingPiece>>,
        unsafePair: Pair<Long, Long>,
        hiddenFleeceId: Long,
        history: MutableList<OutfitEntryWithPieces>,
        currentState: nl.petrichor.app.data.outfit.ModelState,
        stats: BanditAccumulator,
    ): nl.petrichor.app.data.outfit.ModelState {
        val targetDate = baseDate.plus(DatePeriod(days = 20 + day))
        val tempC = 7.0 + (day % 3)
        val input =
            RecommendationInput(
                date = targetDate,
                today = targetDate,
                tempC = tempC,
                isRainy = false,
                wardrobePieces = wardrobe,
                clothingTypes = types,
                entries = history,
                surveyedCount = history.size,
                modelState = currentState,
                nowEpochMs = 1_800_000_000_000L,
                random = Random(100 + day),
            )
        val slate = buildControlledSlate(input, currentState, poolCandidates, exploreSlot3)
        if (slate.slots.any { it.isExploration && unsafePair.first in it.pieceIds && unsafePair.second in it.pieceIds }) {
            stats.unsafeExplorations++
        }
        val oracleReward = poolCandidates.maxOf { trueBanditComfortScore(it, tempC, hiddenFleeceId).first }
        // Wearer selects the highest true-comfort candidate presented in the 3-slot slate.
        val chosenSlotIdx =
            slate.candidates.indices.maxBy { idx ->
                trueBanditComfortScore(slate.candidates[idx].pieces, tempC, hiddenFleeceId).first
            }
        val chosenCandidate = slate.candidates[chosenSlotIdx]
        val chosenSlot = slate.slots[chosenSlotIdx]
        if (chosenCandidate.pieces.any { it.id == hiddenFleeceId }) stats.discoveredHiddenGem = true

        val (rewardScore, verdict) = trueBanditComfortScore(chosenCandidate.pieces, tempC, hiddenFleeceId)
        stats.cumulativeRegret += (oracleReward - rewardScore).coerceAtLeast(0.0)
        if (verdict != WarmthVerdict.PERFECT) stats.complaints++
        val ipsWeight = 1.0 / chosenSlot.propensity.coerceAtLeast(1e-3)
        stats.weightedRewardSum += ipsWeight * rewardScore
        stats.propensityWeightSum += ipsWeight

        history +=
            entryWithPieces(
                id = 500L + day,
                dayOffset = 20 + day,
                tempC = tempC,
                pieces = chosenCandidate.pieces,
                score = rewardScore.roundToInt(),
                verdict = verdict,
            )
        return BayesianModelTrainer.train(
            TrainingSnapshot(
                entries = history,
                clothingTypes = types,
                dataRevision = (day + 2).toLong(),
                trainedAtEpochMs = (day + 2).toLong(),
                previousState = currentState,
            ),
        )!!
    }

    private fun buildControlledSlate(
        input: RecommendationInput,
        modelState: nl.petrichor.app.data.outfit.ModelState,
        poolCandidates: List<List<ClothingPiece>>,
        exploreSlot3: Boolean,
    ): RankedSlate {
        val decoded = DecodedModel.decode(modelState)!!
        val evaluated =
            poolCandidates
                .map { pieces ->
                    val raw = FeatureExtractor.extractRaw(input.tempC!!, input.isRainy, pieces, typeById)
                    val features = FeatureExtractor.buildFeatures(raw, decoded.scaler, pieces, decoded.activePieceIndexById)
                    val unseenIds = pieces.map { it.id }.distinct().filter { it !in decoded.activePieceIndexById }
                    val comfort =
                        BayesianComfortModel.predict(
                            decoded.ratingPosterior,
                            decoded.thermalPosterior,
                            raw,
                            features,
                            unseenPieceCount = unseenIds.size,
                        )
                    EvaluatedCandidate(
                        candidate = OutfitCandidate(null, pieces, null, null, RecommendationSource.MODEL),
                        baseScore = comfort.rating.mean,
                        tempPenalty = 0.0,
                        raw = raw,
                        features = features,
                        unseenPieceIds = unseenIds,
                        comfort = comfort,
                    )
                }.sortedByDescending { it.baseScore }

        if (!exploreSlot3) {
            val top3 = evaluated.take(3)
            return RankedSlate(
                candidates = top3.map { it.candidate },
                slots =
                    top3.mapIndexed { idx, eval ->
                        CandidateImpressionSlot(
                            slotIndex = idx,
                            pieceIds = eval.candidate.pieces.map { it.id },
                            source = RecommendationSource.MODEL,
                            isExploration = false,
                            propensity = 1.0,
                            predictedRating = eval.comfort!!.rating.mean,
                            pTooCold = eval.comfort.thermal.tooCold,
                            pPerfect = eval.comfort.thermal.perfect,
                            pTooWarm = eval.comfort.thermal.tooWarm,
                            ratingEpistemicStd = eval.comfort.rating.epistemicStd,
                            thermalEpistemicStd = eval.comfort.thermal.epistemicStd,
                        )
                    },
                policyName = ExplorationPolicy.POLICY_GREEDY,
                isExplorationSlate = false,
            )
        }
        val context = RecommendationScoringContext(input, input.wardrobePieces, typeById, RecommendationMode.MODEL, decoded)
        return ExplorationPolicy.selectSlate(context, evaluated, input.random)
    }

    private fun trueBanditComfortScore(
        pieces: List<ClothingPiece>,
        tempC: Double,
        hiddenFleeceId: Long,
    ): Pair<Double, WarmthVerdict> {
        val warmth = outfitWarmth(pieces, typeById) + if (pieces.any { it.id == hiddenFleeceId }) 0.85 else 0.0
        val ideal = (2.7 - 0.07 * tempC).coerceIn(0.3, 3.0)
        val diff = warmth - ideal
        val score = (5.0 - 2.3 * abs(diff)).coerceIn(1.0, 5.0)
        val verdict =
            when {
                diff < -0.35 -> WarmthVerdict.TOO_COLD
                diff > 0.35 -> WarmthVerdict.TOO_WARM
                else -> WarmthVerdict.PERFECT
            }
        return score to verdict
    }

    private fun buildBanditSeedHistory(wardrobe: List<ClothingPiece>): List<OutfitEntryWithPieces> {
        val linenTee = wardrobe[0]
        val shirt = wardrobe[1]
        val sweater = wardrobe[2]
        val standardJacket = wardrobe[3]
        val thinShell = wardrobe[4]
        val jeans = wardrobe[6]
        val linenPants = wardrobe[7]
        return listOf(
            entryWithPieces(1L, 0, 2.0, listOf(linenTee, linenPants), 1, WarmthVerdict.TOO_COLD),
            entryWithPieces(2L, 1, 8.0, listOf(shirt, jeans), 3, WarmthVerdict.TOO_COLD),
            entryWithPieces(3L, 2, 8.0, listOf(sweater, jeans), 4, WarmthVerdict.PERFECT),
            entryWithPieces(4L, 3, 7.0, listOf(sweater, jeans), 4, WarmthVerdict.PERFECT),
            entryWithPieces(5L, 4, 8.0, listOf(standardJacket, shirt, jeans), 4, WarmthVerdict.PERFECT),
            entryWithPieces(6L, 5, 7.0, listOf(standardJacket, shirt, jeans), 4, WarmthVerdict.PERFECT),
            entryWithPieces(7L, 6, 8.0, listOf(thinShell, sweater, jeans), 4, WarmthVerdict.PERFECT),
            entryWithPieces(8L, 7, 19.0, listOf(shirt, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(9L, 8, 22.0, listOf(linenTee, linenPants), 5, WarmthVerdict.PERFECT),
            entryWithPieces(10L, 9, 10.0, listOf(standardJacket, sweater, jeans), 3, WarmthVerdict.TOO_WARM),
        )
    }

    private fun applyGroundTruthContext(
        item: OutfitEntryWithPieces,
        question: SurveyContextQuestion,
        spec: ContextStreamSpec,
    ): OutfitEntryWithPieces {
        val entry = item.entry
        val updated =
            when (question) {
                SurveyContextQuestion.GOT_WET -> entry.copy(gotWet = spec.actualGotWet)
                SurveyContextQuestion.MOSTLY_INDOORS -> entry.copy(mostlyIndoors = spec.actualIndoors)
                SurveyContextQuestion.HIGH_ACTIVITY -> entry.copy(highActivity = spec.actualHighActivity)
            }
        return item.copy(entry = updated)
    }

    private fun buildSeedEntries(
        tee: ClothingPiece,
        sweater: ClothingPiece,
        rainShell: ClothingPiece,
        jeans: ClothingPiece,
    ): List<OutfitEntryWithPieces> =
        listOf(
            entryWithPieces(1L, 0, 22.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(2L, 1, 21.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(3L, 2, 12.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(4L, 3, 11.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(5L, 4, 10.0, listOf(rainShell, jeans), 5, WarmthVerdict.PERFECT, isRainy = true, pop = 0.9, rainMm = 6.0),
            entryWithPieces(6L, 5, 11.0, listOf(rainShell, jeans), 5, WarmthVerdict.PERFECT, isRainy = true, pop = 0.9, rainMm = 6.0),
            entryWithPieces(7L, 6, 8.0, listOf(tee, jeans), 1, WarmthVerdict.TOO_COLD),
            entryWithPieces(8L, 7, 13.0, listOf(tee, jeans), 2, WarmthVerdict.TOO_COLD),
        )

    private fun buildContextStreamSpecs(wardrobe: List<ClothingPiece>): List<ContextStreamSpec> {
        val templates = buildContextTemplates(wardrobe)
        val items = mutableListOf<ContextStreamSpec>()
        repeat(3) { cycle ->
            templates.forEachIndexed { idx, spec ->
                val dayOffset = 10 + cycle * templates.size + idx
                val shiftedEntry =
                    spec.baseEntry.entry.copy(
                        id = 100L + dayOffset,
                        wornOnDate = baseDate.plus(DatePeriod(days = dayOffset)),
                    )
                items += spec.copy(baseEntry = spec.baseEntry.copy(entry = shiftedEntry))
            }
        }
        return items
    }

    private fun buildContextTemplates(wardrobe: List<ClothingPiece>): List<ContextStreamSpec> {
        val tee = wardrobe[0]
        val shirt = wardrobe[1]
        val sweater = wardrobe[2]
        val cottonJacket = wardrobe[3]
        val rainShell = wardrobe[4]
        val jeans = wardrobe[5]
        return listOf(
            ContextStreamSpec(
                entryWithPieces(10L, 10, 5.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
                actualIndoors = true,
                actualHighActivity = false,
                actualGotWet = false,
            ),
            rainyTemplate(11L, 11, 13.0, listOf(cottonJacket, jeans), 5, WarmthVerdict.PERFECT, gotWet = false),
            rainyTemplate(12L, 12, 12.0, listOf(cottonJacket, jeans), 1, WarmthVerdict.TOO_COLD, gotWet = true),
            ContextStreamSpec(
                entryWithPieces(13L, 13, 6.0, listOf(shirt, jeans), 5, WarmthVerdict.PERFECT),
                actualIndoors = true,
                actualHighActivity = false,
                actualGotWet = false,
            ),
            rainyTemplate(14L, 14, 14.0, listOf(rainShell, jeans), 5, WarmthVerdict.PERFECT, gotWet = true),
            ContextStreamSpec(
                entryWithPieces(15L, 15, 16.0, listOf(sweater, jeans), 4, WarmthVerdict.PERFECT),
                actualIndoors = false,
                actualHighActivity = false,
                actualGotWet = false,
            ),
        )
    }

    @Suppress("LongParameterList") // concise rainy-day simulation template builder
    private fun rainyTemplate(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        pieces: List<ClothingPiece>,
        score: Int,
        verdict: WarmthVerdict,
        gotWet: Boolean,
    ): ContextStreamSpec =
        ContextStreamSpec(
            entryWithPieces(id, dayOffset, tempC, pieces, score, verdict, isRainy = true, pop = 0.85, rainMm = 5.0),
            actualIndoors = false,
            actualHighActivity = false,
            actualGotWet = gotWet,
        )

    private fun buildContextHoldout(wardrobe: List<ClothingPiece>): List<OutfitEntryWithPieces> {
        val tee = wardrobe[0]
        val shirt = wardrobe[1]
        val sweater = wardrobe[2]
        val cottonJacket = wardrobe[3]
        val rainShell = wardrobe[4]
        val jeans = wardrobe[5]
        return listOf(
            entryWithPieces(901L, 60, 6.0, listOf(tee, jeans), 1, WarmthVerdict.TOO_COLD),
            entryWithPieces(902L, 61, 7.0, listOf(shirt, jeans), 2, WarmthVerdict.TOO_COLD),
            entryWithPieces(
                id = 903L,
                dayOffset = 62,
                tempC = 12.0,
                pieces = listOf(cottonJacket, jeans),
                score = 1,
                verdict = WarmthVerdict.TOO_COLD,
                isRainy = true,
                pop = 0.9,
                rainMm = 6.0,
            ),
            entryWithPieces(904L, 63, 12.0, listOf(rainShell, jeans), 5, WarmthVerdict.PERFECT, isRainy = true, pop = 0.9, rainMm = 6.0),
            entryWithPieces(905L, 64, 13.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            entryWithPieces(906L, 65, 20.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
        )
    }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material,
        protection: WeatherProtection = WeatherProtection.NONE,
    ) = ClothingPiece(
        id = id,
        name = "p$id",
        typeId = typeId,
        material = material,
        weatherProtection = protection,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    @Suppress("LongParameterList") // concise observation builder for offline simulation
    private fun entryWithPieces(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        pieces: List<ClothingPiece>,
        score: Int,
        verdict: WarmthVerdict,
        isRainy: Boolean = false,
        pop: Double? = null,
        rainMm: Double? = null,
    ): OutfitEntryWithPieces {
        val ts = 1_790_000_000_000L + dayOffset * 86_400_000L
        return OutfitEntryWithPieces(
            entry =
                OutfitEntry(
                    id = id,
                    wornOnDate = baseDate.plus(DatePeriod(days = dayOffset)),
                    photoPath = null,
                    notes = null,
                    temperatureC = tempC,
                    feelsLikeC = tempC,
                    minTempC = tempC - 2.0,
                    maxTempC = tempC + 2.0,
                    weatherDescription = if (isRainy) "Rain" else "Clear",
                    weatherIcon = if (isRainy) "10d" else "01d",
                    isRainy = isRainy,
                    precipitationProbability = pop,
                    rainVolumeMm = rainMm,
                    surveyFeelingScore = score,
                    surveyWarmthVerdict = verdict,
                    surveyedAtEpochMs = ts,
                    createdAtEpochMs = ts,
                ),
            pieces = pieces,
        )
    }
}

private data class ActiveScenario(
    val wardrobe: List<ClothingPiece>,
    val seedEntries: List<OutfitEntryWithPieces>,
    val streamSpecs: List<ContextStreamSpec>,
    val holdout: List<OutfitEntryWithPieces>,
)

private data class ContextStreamSpec(
    val baseEntry: OutfitEntryWithPieces,
    val actualIndoors: Boolean,
    val actualHighActivity: Boolean,
    val actualGotWet: Boolean,
)

private data class BanditAccumulator(
    var cumulativeRegret: Double = 0.0,
    var complaints: Int = 0,
    var unsafeExplorations: Int = 0,
    var weightedRewardSum: Double = 0.0,
    var propensityWeightSum: Double = 0.0,
    var discoveredHiddenGem: Boolean = false,
)
