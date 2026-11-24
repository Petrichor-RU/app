package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.SurveyContextAnswers
import nl.petrichor.app.data.outfit.SurveyContextQuestion
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveQuerySelectorTest {
    @Test
    fun `active query selector asks gotWet only in rain and skips routine matching dry days`() {
        val wardrobe = SurveyHistoryFixtures.standardWardrobe()
        val tee = wardrobe[0]
        val sweater = wardrobe[2]
        val jeans = wardrobe[4]
        val history = SurveyHistoryFixtures.buildRichHistory(wardrobe)
        val model = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(history, SurveyHistoryFixtures.types, 1L, 1L))!!)!!

        val routineDrySweater = SurveyHistoryFixtures.surveyedEntry(30L, 20, 12.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT)
        assertNull(ActiveQuerySelector.selectFollowUpQuestion(routineDrySweater, model, SurveyHistoryFixtures.types, history))

        val rainyDay =
            SurveyHistoryFixtures.surveyedEntry(
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
        assertEquals(
            SurveyContextQuestion.GOT_WET,
            ActiveQuerySelector.selectFollowUpQuestion(rainyDay, model, SurveyHistoryFixtures.types, history),
        )

        val surprisingColdTee = SurveyHistoryFixtures.surveyedEntry(32L, 22, 5.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT)
        val followUp = ActiveQuerySelector.selectFollowUpQuestion(surprisingColdTee, model, SurveyHistoryFixtures.types, history)
        assertNotNull(followUp)
        assertTrue(followUp == SurveyContextQuestion.MOSTLY_INDOORS || followUp == SurveyContextQuestion.HIGH_ACTIVITY)
        assertTrue(ActiveQuerySelector.informationValue(surprisingColdTee, model, SurveyHistoryFixtures.types, history) > 0.3)
    }

    @Test
    fun `indoor context downweights cold outlier and replays cleanly on edit`() =
        runTest {
            val wardrobe = SurveyHistoryFixtures.standardWardrobe()
            val tee = wardrobe[0]
            val shorts = wardrobe[5]
            val history = SurveyHistoryFixtures.buildRichHistory(wardrobe)
            val types = SurveyHistoryFixtures.types

            val indoorOutlier =
                SurveyHistoryFixtures.surveyedEntry(40L, 15, 3.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT, mostlyIndoors = true)
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
            val clothingDao = FakeClothingDao().apply { this.types.value = types }
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
        val jacket = SurveyHistoryFixtures.piece(4L, SurveyHistoryFixtures.jacketType.id, Material.COTTON)
        val jeans = SurveyHistoryFixtures.piece(5L, SurveyHistoryFixtures.jeansType.id, Material.COTTON)
        val rainyEntry =
            SurveyHistoryFixtures
                .surveyedEntry(
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
        val results = ActiveLearningAndExplorationSimulation.simulateActiveQuerying(maxQuestions = 8)
        val none = results.getValue(QueryStrategy.NONE)
        val random = results.getValue(QueryStrategy.RANDOM)
        val selective = results.getValue(QueryStrategy.SELECTIVE)

        assertTrue(selective.questionsAsked in 1..8)
        assertTrue(selective.holdoutMae <= none.holdoutMae)
        assertTrue(selective.holdoutLogLoss < none.holdoutLogLoss)
        assertTrue(selective.maeImprovementPerQuestion > random.maeImprovementPerQuestion)
        assertTrue(selective.logLossImprovementPerQuestion > random.logLossImprovementPerQuestion)
    }
}
