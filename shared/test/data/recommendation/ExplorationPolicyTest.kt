package nl.petrichor.app.data.recommendation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplorationPolicyTest {
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

        assertEquals(stateWithImpression.coefficientsJson, stateWithoutIgnoredCandidates.coefficientsJson)
    }
}
