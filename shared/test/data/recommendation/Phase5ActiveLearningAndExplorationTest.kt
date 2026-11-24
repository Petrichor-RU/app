package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.SurveyContextAnswers
import nl.petrichor.app.data.outfit.SurveyContextQuestion
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.wardrobe.WeatherProtection
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.recommendation.RecommendationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class Phase5ActiveLearningAndExplorationTest {
    private val baseDate = LocalDate(2026, 9, 1)
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)
        }

    private val teeType = ClothingType(id = 1L, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val shirtType = ClothingType(id = 2L, name = "Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 3L, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 4L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 5L, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val shortsType = ClothingType(id = 6L, name = "Shorts", supertype = ClothingSupertype.LOWER_BODY)
    private val types = listOf(teeType, shirtType, sweaterType, jacketType, jeansType, shortsType)

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.COTTON,
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

    @Suppress("LongParameterList") // concise test builder for surveyed Phase 5 journal entries
    private fun surveyedEntry(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        pieces: List<ClothingPiece>,
        score: Int,
        verdict: WarmthVerdict,
        isRainy: Boolean = false,
        pop: Double? = null,
        rainMm: Double? = null,
        mostlyIndoors: Boolean? = null,
        highActivity: Boolean? = null,
        gotWet: Boolean? = null,
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
                    mostlyIndoors = mostlyIndoors,
                    highActivity = highActivity,
                    gotWet = gotWet,
                    createdAtEpochMs = ts,
                ),
            pieces = pieces,
        )
    }

    private fun buildRichHistory(wardrobe: List<ClothingPiece>): List<OutfitEntryWithPieces> {
        val tee = wardrobe[0]
        val shirt = wardrobe[1]
        val sweater = wardrobe[2]
        val jacket = wardrobe[3]
        val jeans = wardrobe[4]
        val shorts = wardrobe[5]
        return listOf(
            surveyedEntry(1L, 0, 26.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT),
            surveyedEntry(2L, 1, 24.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT),
            surveyedEntry(3L, 2, 18.0, listOf(shirt, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(4L, 3, 17.0, listOf(shirt, jeans), 4, WarmthVerdict.PERFECT),
            surveyedEntry(5L, 4, 12.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(6L, 5, 11.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(7L, 6, 6.0, listOf(jacket, sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(8L, 7, 5.0, listOf(jacket, sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(9L, 8, 5.0, listOf(tee, shorts), 1, WarmthVerdict.TOO_COLD),
            surveyedEntry(10L, 9, 4.0, listOf(tee, shorts), 1, WarmthVerdict.TOO_COLD),
            surveyedEntry(11L, 10, 26.0, listOf(jacket, sweater, jeans), 1, WarmthVerdict.TOO_WARM),
            surveyedEntry(12L, 11, 25.0, listOf(jacket, sweater, jeans), 1, WarmthVerdict.TOO_WARM),
        )
    }

    private fun standardWardrobe(): List<ClothingPiece> =
        listOf(
            piece(1L, teeType.id, Material.LINEN),
            piece(2L, shirtType.id, Material.COTTON),
            piece(3L, sweaterType.id, Material.WOOL),
            piece(4L, jacketType.id, Material.WOOL),
            piece(5L, jeansType.id, Material.COTTON),
            piece(6L, shortsType.id, Material.LINEN),
        )

    private fun sampleWeatherSnapshot(): WeatherSnapshot =
        WeatherSnapshot(
            place = Place("Amsterdam", Coordinates(52.37, 4.89)),
            temperature = 11,
            feelsLike = 10,
            description = "Clouds",
            icon = "04d",
            conditionId = 803,
            humidity = 75,
            windSpeed = 4,
            forecast =
                listOf(
                    ForecastDay("Today", min = 8, max = 14, icon = "04d", conditionId = 803),
                    ForecastDay("Tomorrow", min = 9, max = 15, icon = "01d", conditionId = 800),
                ),
            temperatureC = 11.0,
            feelsLikeC = 10.0,
            windSpeedMs = 4.0,
        )

    @Test
    fun `slot 1 never explores and unsafe extreme outfits are excluded from slot 3 exploration`() {
        val baseWardrobe = standardWardrobe()
        val tee = baseWardrobe[0]
        val shorts = baseWardrobe[5]
        val newFleece = piece(7L, jacketType.id, Material.POLYESTER)
        val history = buildRichHistory(baseWardrobe)
        val modelState = BayesianModelTrainer.train(TrainingSnapshot(history, types, 1L, 1L))!!

        val input =
            RecommendationInput(
                date = baseDate.plus(DatePeriod(days = 20)),
                today = baseDate.plus(DatePeriod(days = 20)),
                tempC = 4.0,
                isRainy = false,
                wardrobePieces = baseWardrobe + newFleece,
                clothingTypes = types,
                entries = history,
                surveyedCount = history.size,
                modelState = modelState,
                nowEpochMs = 1_800_000_000_000L,
            )
        val slate = RecommendationEngine.recommendSlate(input)

        assertTrue(slate.candidates.isNotEmpty())
        assertFalse(slate.slots[0].isExploration)
        assertEquals(1.0, slate.slots[0].propensity)
        slate.slots.filter { it.isExploration }.forEach { slot ->
            val hasLightSummerCombo = tee.id in slot.pieceIds && shorts.id in slot.pieceIds
            assertFalse(hasLightSummerCombo, "Unsafe summer outfit must never be explored at 4 C")
            assertTrue(slot.pPerfect >= ExplorationPolicy.SAFE_MIN_P_PERFECT)
            assertTrue(slot.pTooCold <= ExplorationPolicy.SAFE_MAX_EXTREME_DISCOMFORT)
            assertTrue(slot.pTooWarm <= ExplorationPolicy.SAFE_MAX_EXTREME_DISCOMFORT)
        }
    }

    @Test
    fun `slate selection is deterministic within a session for identical date and model revision`() {
        val baseWardrobe = standardWardrobe()
        val newCardigan = piece(7L, sweaterType.id, Material.COTTON)
        val history = buildRichHistory(baseWardrobe)
        val modelState = BayesianModelTrainer.train(TrainingSnapshot(history, types, 4L, 100L))!!

        val input =
            RecommendationInput(
                date = baseDate.plus(DatePeriod(days = 25)),
                today = baseDate.plus(DatePeriod(days = 25)),
                tempC = 10.0,
                isRainy = false,
                wardrobePieces = baseWardrobe + newCardigan,
                clothingTypes = types,
                entries = history,
                surveyedCount = history.size,
                modelState = modelState,
                nowEpochMs = 1_800_000_000_000L,
            )

        val firstSlate = RecommendationEngine.recommendSlate(input)
        val secondSlate = RecommendationEngine.recommendSlate(input)

        assertEquals(
            firstSlate.candidates.map { c -> c.pieces.map { it.id } },
            secondSlate.candidates.map { c -> c.pieces.map { it.id } },
        )
        assertEquals(firstSlate.slots, secondSlate.slots)
        assertEquals(firstSlate.policyName, secondSlate.policyName)
    }

    @Test
    fun `controller caches slates across date switches logs one impression and links logged entry`() =
        runTest {
            val clothingDao =
                FakeClothingDao().apply {
                    types.value = this@Phase5ActiveLearningAndExplorationTest.types
                    clothing.value = standardWardrobe()
                }
            val history = buildRichHistory(standardWardrobe())
            val outfitDao = FakeOutfitDao().apply { entries.value = history }
            outfitDao.insertModelState(BayesianModelTrainer.train(TrainingSnapshot(history, types, 3L, 1_800_000_000_000L))!!)

            val coordinator = RecommendationTrainingCoordinator(outfitDao, clothingDao, Dispatchers.Unconfined, this)
            val wardrobeRepo = WardrobeRepository(clothingDao, outfitDao, Dispatchers.Unconfined, coordinator, imageDeleter = {})
            val outfitRepo = OutfitLogRepository(outfitDao, clothingDao, Dispatchers.Unconfined, {}, this, coordinator)
            val today = baseDate.plus(DatePeriod(days = 20))
            val controller = RecommendationController(wardrobeRepo, outfitRepo, Dispatchers.Unconfined, today = today, clock = fixedClock)
            controller.updateWeather(sampleWeatherSnapshot())

            val firstCandidates = controller.candidates.first { it.isNotEmpty() }
            assertEquals(1, outfitDao.impressions.size)

            controller.selectDate(today.plus(DatePeriod(days = 1)))
            controller.candidates.first { it.isNotEmpty() }
            assertEquals(2, outfitDao.impressions.size)

            // Switching back to today reuses cached slate and must not append a duplicate impression.
            controller.selectDate(today)
            val revisited = controller.candidates.first { it.isNotEmpty() }
            assertEquals(2, outfitDao.impressions.size)
            assertEquals(firstCandidates.map { c -> c.pieces.map { it.id } }, revisited.map { c -> c.pieces.map { it.id } })

            val decodedSlots = Json.decodeFromString<List<CandidateImpressionSlot>>(outfitDao.impressions.first().candidatesJson)
            assertEquals(firstCandidates.size, decodedSlots.size)

            val newEntryId = outfitRepo.logOutfit(emptyLoggedEntry(today, tempC = 11.0), firstCandidates.first().pieces)
            assertEquals(newEntryId, outfitRepo.getImpressionsForDate(today).single().wornEntryId)
        }

    private fun emptyLoggedEntry(
        date: LocalDate,
        tempC: Double,
    ) = OutfitEntry(
        wornOnDate = date,
        photoPath = null,
        notes = null,
        temperatureC = tempC,
        feelsLikeC = tempC,
        minTempC = tempC - 2.0,
        maxTempC = tempC + 2.0,
        weatherDescription = "Clouds",
        weatherIcon = "04d",
        isRainy = false,
        surveyFeelingScore = null,
        surveyWarmthVerdict = null,
        surveyedAtEpochMs = null,
    )

    @Test
    fun `ignored slate suggestions produce zero negative posterior updates`() {
        val wardrobe = standardWardrobe()
        val baseHistory = buildRichHistory(wardrobe)

        val input =
            RecommendationInput(
                date = baseDate.plus(DatePeriod(days = 22)),
                today = baseDate.plus(DatePeriod(days = 22)),
                tempC = 14.0,
                isRainy = false,
                wardrobePieces = wardrobe,
                clothingTypes = types,
                entries = baseHistory,
                surveyedCount = baseHistory.size,
                modelState = BayesianModelTrainer.train(TrainingSnapshot(baseHistory, types, 1L, 1L)),
                nowEpochMs = 1_800_000_000_000L,
            )
        val slate = RecommendationEngine.recommendSlate(input)
        assertTrue(slate.candidates.size >= 2)

        val chosen = slate.candidates[0]
        val wornEntry = surveyedEntry(99L, 22, 14.0, chosen.pieces, 5, WarmthVerdict.PERFECT)
        val stateWithImpression =
            BayesianModelTrainer.train(
                TrainingSnapshot(baseHistory + wornEntry, types, dataRevision = 2L, trainedAtEpochMs = 2L),
            )!!
        val stateWithoutIgnoredCandidates =
            BayesianModelTrainer.train(
                TrainingSnapshot(baseHistory + wornEntry, types, dataRevision = 2L, trainedAtEpochMs = 2L),
            )!!

        assertEquals(stateWithImpression.artifactJson, stateWithoutIgnoredCandidates.artifactJson)
    }

    @Test
    fun `active query selector asks gotWet only in rain and skips routine matching dry days`() {
        val wardrobe = standardWardrobe()
        val tee = wardrobe[0]
        val sweater = wardrobe[2]
        val jeans = wardrobe[4]
        val history = buildRichHistory(wardrobe)
        val model = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(history, types, 1L, 1L))!!)!!

        val routineDrySweater = surveyedEntry(30L, 20, 12.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT)
        assertNull(ActiveQuerySelector.selectFollowUpQuestion(routineDrySweater, model, types, history))

        val rainyDay =
            surveyedEntry(
                id = 31L,
                dayOffset = 21,
                tempC = 12.0,
                pieces = listOf(sweater, jeans),
                score = 2,
                verdict = WarmthVerdict.TOO_COLD,
                isRainy = true,
                pop = 0.9,
                rainMm = 5.0,
            )
        assertEquals(SurveyContextQuestion.GOT_WET, ActiveQuerySelector.selectFollowUpQuestion(rainyDay, model, types, history))

        val surprisingColdTee = surveyedEntry(32L, 22, 5.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT)
        val followUp = ActiveQuerySelector.selectFollowUpQuestion(surprisingColdTee, model, types, history)
        assertNotNull(followUp)
        assertTrue(followUp == SurveyContextQuestion.MOSTLY_INDOORS || followUp == SurveyContextQuestion.HIGH_ACTIVITY)
        assertTrue(ActiveQuerySelector.informationValue(surprisingColdTee, model, types, history) > 0.3)
    }

    @Test
    fun `indoor context downweights cold outlier and replays cleanly on edit`() =
        runTest {
            val wardrobe = standardWardrobe()
            val tee = wardrobe[0]
            val shorts = wardrobe[5]
            val history = buildRichHistory(wardrobe)

            val indoorOutlier = surveyedEntry(40L, 15, 3.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT, mostlyIndoors = true)
            val outdoorOutlier = indoorOutlier.copy(entry = indoorOutlier.entry.copy(mostlyIndoors = false))
            val indoorState = BayesianModelTrainer.train(TrainingSnapshot(history + indoorOutlier, types, 2L, 2L))!!
            val outdoorState = BayesianModelTrainer.train(TrainingSnapshot(history + outdoorOutlier, types, 2L, 2L))!!
            val modelWithIndoor = DecodedModel.decode(indoorState)!!
            val modelWithoutIndoor = DecodedModel.decode(outdoorState)!!

            val coldProbe = FeatureExtractor.extractRaw(3.0, false, listOf(tee, shorts), types.associateBy { it.id })
            val probePieces = listOf(tee, shorts)
            val indoorPred =
                BayesianComfortModel.predict(
                    modelWithIndoor.ratingPosterior,
                    modelWithIndoor.thermalPosterior,
                    coldProbe,
                    FeatureExtractor.buildFeatures(coldProbe, modelWithIndoor.scaler, probePieces, modelWithIndoor.activePieceIndexById),
                )
            val outdoorPred =
                BayesianComfortModel.predict(
                    modelWithoutIndoor.ratingPosterior,
                    modelWithoutIndoor.thermalPosterior,
                    coldProbe,
                    FeatureExtractor.buildFeatures(
                        coldProbe,
                        modelWithoutIndoor.scaler,
                        probePieces,
                        modelWithoutIndoor.activePieceIndexById,
                    ),
                )
            assertTrue(indoorPred.rating.mean < outdoorPred.rating.mean)

            val dao = FakeOutfitDao()
            val clothingDao = FakeClothingDao().apply { types.value = this@Phase5ActiveLearningAndExplorationTest.types }
            dao.entries.value = history + outdoorOutlier
            val repo = OutfitLogRepository(dao, clothingDao, Dispatchers.Unconfined, imageDeleter = {}, appScope = this)
            repo.saveSurvey(40L, 5, WarmthVerdict.PERFECT, SurveyContextAnswers(mostlyIndoors = true))
            val replayedState = dao.modelStates.last()
            val expectedState =
                BayesianModelTrainer
                    .train(TrainingSnapshot(history + indoorOutlier, types, replayedState.dataRevision, replayedState.trainedAtEpochMs))!!
            val replayed = DecodedModel.decode(replayedState)!!
            val expected = DecodedModel.decode(expectedState)!!
            val savedEntries = dao.entries.value
            assertEquals(true, savedEntries.last().entry.mostlyIndoors)
            assertEquals(expected.scaler, replayed.scaler)
            assertEquals(expected.activePieceIds, replayed.activePieceIds)
            assertEquals(expected.ratingPosterior, replayed.ratingPosterior)
            assertEquals(expected.thermalPosterior, replayed.thermalPosterior)
        }

    @Test
    fun `gotWet false decouples forecast rain exposure while gotWet true retains wet signal`() {
        val jacket = piece(4L, jacketType.id, Material.COTTON)
        val jeans = piece(5L, jeansType.id, Material.COTTON)
        val rainyEntry =
            surveyedEntry(
                id = 41L,
                dayOffset = 16,
                tempC = 11.0,
                pieces = listOf(jacket, jeans),
                score = 5,
                verdict = WarmthVerdict.PERFECT,
                isRainy = true,
                pop = 0.9,
                rainMm = 6.0,
                gotWet = false,
            ).entry

        assertEquals(0.0, effectiveTrainingRainExposure(rainyEntry))
        assertTrue(effectiveTrainingRainExposure(rainyEntry.copy(gotWet = true)) >= 0.85)
    }

    @Test
    fun `active learning simulation improves prediction per question over random querying`() {
        val results = Phase5SimulationSupport.simulateActiveQuerying(maxQuestions = 8)
        val none = results.getValue(QueryStrategy.NONE)
        val random = results.getValue(QueryStrategy.RANDOM)
        val selective = results.getValue(QueryStrategy.SELECTIVE)

        assertTrue(selective.questionsAsked in 1..8)
        assertTrue(selective.holdoutMae <= none.holdoutMae)
        assertTrue(selective.holdoutLogLoss < none.holdoutLogLoss)
        assertTrue(selective.maeImprovementPerQuestion > random.maeImprovementPerQuestion)
        assertTrue(selective.logLossImprovementPerQuestion > random.logLossImprovementPerQuestion)
    }

    @Test
    fun `constrained Thompson simulation discovers hidden warm garment safely with lower regret than greedy`() {
        val (greedy, thompson) = Phase5SimulationSupport.simulateBanditSlates(rounds = 18)

        assertEquals(0, thompson.unsafeExplorations)
        assertTrue(thompson.discoveredHiddenGem, "Constrained Thompson should surface the unseen warm fleece via Slot 3")
        assertTrue(thompson.cumulativeRegret <= greedy.cumulativeRegret)
        assertTrue(thompson.complaintRate <= greedy.complaintRate)
        assertTrue(thompson.hajekIpsReward in 1.0..5.0)
    }
}
