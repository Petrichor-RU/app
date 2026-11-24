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
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.recommendation.RecommendationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ExplorationPolicyTest {
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)
        }

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
    fun `slot 1 never explores and unsafe extreme outfits are excluded from slot 3 exploration`() {
        val baseWardrobe = SurveyHistoryFixtures.standardWardrobe()
        val tee = baseWardrobe[0]
        val shorts = baseWardrobe[5]
        val newFleece = SurveyHistoryFixtures.piece(7L, SurveyHistoryFixtures.jacketType.id, Material.POLYESTER)
        val history = SurveyHistoryFixtures.buildRichHistory(baseWardrobe)
        val modelState = BayesianModelTrainer.train(TrainingSnapshot(history, SurveyHistoryFixtures.types, 1L, 1L))!!

        val input =
            RecommendationInput(
                date = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 20)),
                today = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 20)),
                tempC = 4.0,
                isRainy = false,
                wardrobePieces = baseWardrobe + newFleece,
                clothingTypes = SurveyHistoryFixtures.types,
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
        val baseWardrobe = SurveyHistoryFixtures.standardWardrobe()
        val newCardigan = SurveyHistoryFixtures.piece(7L, SurveyHistoryFixtures.sweaterType.id, Material.COTTON)
        val history = SurveyHistoryFixtures.buildRichHistory(baseWardrobe)
        val modelState = BayesianModelTrainer.train(TrainingSnapshot(history, SurveyHistoryFixtures.types, 4L, 100L))!!

        val input =
            RecommendationInput(
                date = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 25)),
                today = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 25)),
                tempC = 10.0,
                isRainy = false,
                wardrobePieces = baseWardrobe + newCardigan,
                clothingTypes = SurveyHistoryFixtures.types,
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
                    types.value = SurveyHistoryFixtures.types
                    clothing.value = SurveyHistoryFixtures.standardWardrobe()
                }
            val history = SurveyHistoryFixtures.buildRichHistory(SurveyHistoryFixtures.standardWardrobe())
            val outfitDao = FakeOutfitDao().apply { entries.value = history }
            outfitDao.insertModelState(
                BayesianModelTrainer.train(
                    TrainingSnapshot(history, SurveyHistoryFixtures.types, 3L, 1_800_000_000_000L),
                )!!,
            )

            val coordinator = RecommendationTrainingCoordinator(outfitDao, clothingDao, Dispatchers.Unconfined, this)
            val wardrobeRepo = WardrobeRepository(clothingDao, outfitDao, Dispatchers.Unconfined, coordinator, imageDeleter = {})
            val outfitRepo = OutfitLogRepository(outfitDao, clothingDao, Dispatchers.Unconfined, {}, this, coordinator)
            val today = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 20))
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

    @Test
    fun `ignored slate suggestions produce zero negative posterior updates`() {
        val wardrobe = SurveyHistoryFixtures.standardWardrobe()
        val baseHistory = SurveyHistoryFixtures.buildRichHistory(wardrobe)

        val input =
            RecommendationInput(
                date = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 22)),
                today = SurveyHistoryFixtures.baseDate.plus(DatePeriod(days = 22)),
                tempC = 14.0,
                isRainy = false,
                wardrobePieces = wardrobe,
                clothingTypes = SurveyHistoryFixtures.types,
                entries = baseHistory,
                surveyedCount = baseHistory.size,
                modelState = BayesianModelTrainer.train(TrainingSnapshot(baseHistory, SurveyHistoryFixtures.types, 1L, 1L)),
                nowEpochMs = 1_800_000_000_000L,
            )
        val slate = RecommendationEngine.recommendSlate(input)
        assertTrue(slate.candidates.size >= 2)

        val chosen = slate.candidates[0]
        val wornEntry = SurveyHistoryFixtures.surveyedEntry(99L, 22, 14.0, chosen.pieces, 5, WarmthVerdict.PERFECT)
        val stateWithImpression =
            BayesianModelTrainer.train(
                TrainingSnapshot(baseHistory + wornEntry, SurveyHistoryFixtures.types, dataRevision = 2L, trainedAtEpochMs = 2L),
            )!!
        val stateWithoutIgnoredCandidates =
            BayesianModelTrainer.train(
                TrainingSnapshot(baseHistory + wornEntry, SurveyHistoryFixtures.types, dataRevision = 2L, trainedAtEpochMs = 2L),
            )!!

        assertEquals(stateWithImpression.artifactJson, stateWithoutIgnoredCandidates.artifactJson)
    }

    @Test
    fun `constrained Thompson simulation discovers hidden warm garment safely with lower regret than greedy`() {
        val (greedy, thompson) = ActiveLearningAndExplorationSimulation.simulateBanditSlates(rounds = 18)

        assertEquals(0, thompson.unsafeExplorations)
        assertTrue(thompson.discoveredHiddenGem, "Constrained Thompson should surface the unseen warm fleece via Slot 3")
        assertTrue(thompson.cumulativeRegret <= greedy.cumulativeRegret)
        assertTrue(thompson.complaintRate <= greedy.complaintRate)
        assertTrue(thompson.hajekIpsReward in 1.0..5.0)
    }
}
