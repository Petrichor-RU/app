package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryPiece
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.wardrobe.Material
import org.jetbrains.kotlinx.multik.ndarray.data.get
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BayesianComfortModelTest {
    private val baseDate = LocalDate(2026, 4, 1)
    private val teeType = ClothingType(id = 1, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 2, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 3, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 4, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val pantsType = ClothingType(id = 5, name = "Pants", supertype = ClothingSupertype.LOWER_BODY)
    private val types = listOf(teeType, sweaterType, jacketType, jeansType, pantsType)
    private val typeById = types.associateBy { it.id }

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

    private fun entry(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        score: Int,
        verdict: WarmthVerdict,
    ): OutfitEntry {
        val ts = 1_780_000_000_000L + dayOffset * 86_400_000L
        return OutfitEntry(
            id = id,
            wornOnDate = baseDate.plus(DatePeriod(days = dayOffset)),
            photoPath = null,
            notes = null,
            temperatureC = tempC,
            feelsLikeC = tempC,
            minTempC = tempC - 2.0,
            maxTempC = tempC + 2.0,
            weatherDescription = null,
            weatherIcon = null,
            isRainy = false,
            surveyFeelingScore = score,
            surveyWarmthVerdict = verdict,
            surveyedAtEpochMs = ts,
            createdAtEpochMs = ts,
        )
    }

    @Test
    fun `zero drift Kalman rating update equals batch Multik ridge within 1e-9`() {
        val dataset = SyntheticPersonas.generate(PersonaKind.COLD_SENSITIVE, seed = 11)
        val samples =
            dataset.entries.take(15).map { item ->
                FeatureExtractor.extractRaw(
                    tempC = entryTemperature(item.entry)!!,
                    isRainy = item.entry.isRainy,
                    pieces = item.pieces,
                    typeById = typeById,
                ) to item.entry.surveyFeelingScore!!.toDouble()
            }

        val priorVariances = BayesianRatingModel.defaultPriorVariances()
        val obsVariance = BayesianRatingModel.DEFAULT_OBSERVATION_VARIANCE
        val precisionDiagonal = DoubleArray(priorVariances.size) { i -> obsVariance / priorVariances[i] }
        val batchArtifact = RidgeRatingModel.fitWithDiagonalPrecision(samples, precisionDiagonal)

        val scaler = FeatureScaler.fit(samples.map { it.first })
        val steps =
            samples.map { (raw, score) ->
                BayesianRatingStep(
                    features = FeatureExtractor.buildFeatures(raw, scaler),
                    residual = score - RidgeRatingModel.priorPrediction(raw),
                    deltaDays = 5.0,
                )
            }
        val kalmanPosterior =
            BayesianRatingModel.fitReplay(
                steps = steps,
                priorVariances = priorVariances,
                observationVariance = obsVariance,
                qOffset = 0.0,
            )

        for (i in 0 until FeatureExtractor.CORE_FEATURE_COUNT) {
            assertEquals(batchArtifact.ridgeWeights[i], kalmanPosterior.mean[i], 1e-9, "Mismatch at weight index $i")
        }
        val probe = samples.first().first
        val kalmanPred = BayesianRatingModel.predict(kalmanPosterior, probe, FeatureExtractor.buildFeatures(probe, scaler)).mean
        val batchPred = RidgeRatingModel.predict(batchArtifact, probe)
        assertEquals(batchPred, kalmanPred, 1e-9)
    }

    @Test
    fun `offset drift is capped at prior variance and covariance diagonal remains strictly positive`() {
        val raw = RawOutfitSignals(10.0, false, 0.8, 0.36, 0.0, 2.0)
        val scaler = FeatureScaler(10.0, 1.0, 0.8, 1.0, 0.36, 1.0, 0.0, 1.0, 2.0, 1.0, 1.16, 1.0)
        val features = FeatureExtractor.buildFeatures(raw, scaler)

        var posterior = BayesianRatingModel.prior()
        repeat(20) {
            posterior = BayesianRatingModel.update(posterior, features, residual = 0.5, deltaDays = 1.0)
        }
        // After an arbitrarily long gap (10,000 days), P[0, 0] never exceeds prior variance.
        val drifted = BayesianRatingModel.update(posterior, features, residual = 0.5, deltaDays = 10_000.0)
        assertTrue(drifted.covariance[0, 0] <= BayesianRatingModel.OFFSET_PRIOR_VARIANCE + 1e-9)
        for (i in 0 until drifted.dimension) {
            assertTrue(drifted.covariance[i, i] > 0.0)
            for (j in 0 until drifted.dimension) {
                assertTrue(drifted.covariance[i, j].isFinite())
                assertEquals(drifted.covariance[i, j], drifted.covariance[j, i], 1e-12)
            }
        }
    }

    @Test
    fun `thermal curve is monotonic unimodal for PERFECT sums to 1 and stays finite at -30C and +50C`() {
        val woolJacket = piece(1, jacketType.id, Material.WOOL)
        val woolSweater = piece(2, sweaterType.id, Material.WOOL)
        val woolPants = piece(3, pantsType.id, Material.WOOL)
        val winterCoatOutfit = listOf(woolJacket, woolSweater, woolPants)

        val dataset = SyntheticPersonas.generate(PersonaKind.RAIN_AVERSE, seed = 21)
        val snapshot = TrainingSnapshot(dataset.entries, dataset.types, dataRevision = 1L, trainedAtEpochMs = 1L)
        val trainedState = BayesianModelTrainer.train(snapshot)
        val decoded = DecodedModel.decode(trainedState)
        assertNotNull(decoded)

        val probsPrior =
            (0..30).map { temp ->
                val raw = FeatureExtractor.extractRaw(temp.toDouble(), false, winterCoatOutfit, typeById)
                val features = FeatureExtractor.buildFeatures(raw, decoded.scaler)
                BayesianThermalModel.predict(BayesianThermalModel.prior(), raw, features)
            }
        verifyThermalSweepProperties(probsPrior)

        val probsTrained =
            (0..30).map { temp ->
                BayesianThermalModel.predict(decoded, temp.toDouble(), false, winterCoatOutfit, typeById)
            }
        verifyThermalSweepProperties(probsTrained)

        for (extremeTemp in listOf(-30.0, 50.0)) {
            val p = BayesianThermalModel.predict(decoded, extremeTemp, false, winterCoatOutfit, typeById)
            assertProbabilityDistributionValid(p)
        }

        // Direct check on extreme tail ADF moments beyond |z| > 6 for all 3 verdicts
        for (verdict in WarmthVerdict.entries) {
            for (mu in listOf(-30.0, 30.0)) {
                val (d1, d2) = BayesianThermalModel.truncatedNormalDeltaMoments(verdict, mu, 1.0, BayesianThermalModel.DEFAULT_THRESHOLD)
                assertTrue(d1.isFinite(), "Non-finite delta1 for $verdict at mu=$mu")
                assertTrue(d2.isFinite() && d2 in 0.0..1.0, "Out-of-bounds delta2=$d2 for $verdict at mu=$mu")
            }
        }
    }

    private fun verifyThermalSweepProperties(sweep: List<ThermalProbabilities>) {
        for (p in sweep) {
            assertProbabilityDistributionValid(p)
        }
        for (i in 1 until sweep.size) {
            assertTrue(
                sweep[i].tooCold <= sweep[i - 1].tooCold + 1e-12,
                "P(TOO_COLD) increased at step $i: ${sweep[i - 1].tooCold} -> ${sweep[i].tooCold}",
            )
            assertTrue(
                sweep[i].tooWarm >= sweep[i - 1].tooWarm - 1e-12,
                "P(TOO_WARM) decreased at step $i: ${sweep[i - 1].tooWarm} -> ${sweep[i].tooWarm}",
            )
        }
        // Unimodal PERFECT: after peaking, it must not climb back up meaningfully.
        val peakIndex = sweep.indices.maxBy { sweep[it].perfect }
        assertTrue(sweep[peakIndex].perfect > sweep.first().perfect || sweep[peakIndex].perfect > sweep.last().perfect)
        for (i in 1..peakIndex) {
            assertTrue(sweep[i].perfect >= sweep[i - 1].perfect - 1e-12)
        }
        for (i in (peakIndex + 1) until sweep.size) {
            assertTrue(sweep[i].perfect <= sweep[i - 1].perfect + 1e-12)
        }
    }

    private fun assertProbabilityDistributionValid(p: ThermalProbabilities) {
        assertTrue(p.tooCold.isFinite() && p.tooCold in 0.0..1.0)
        assertTrue(p.perfect.isFinite() && p.perfect in 0.0..1.0)
        assertTrue(p.tooWarm.isFinite() && p.tooWarm in 0.0..1.0)
        assertEquals(1.0, p.tooCold + p.perfect + p.tooWarm, 1e-9)
    }

    @Test
    fun `incremental append matches explicit step update on frozen scaler`() {
        val tee = piece(1, teeType.id, Material.COTTON)
        val sweater = piece(2, sweaterType.id, Material.WOOL)
        val jeans = piece(3, jeansType.id, Material.COTTON)
        val entries = buildSampleHistory(tee, sweater, jeans)

        val prefixState =
            BayesianModelTrainer.train(
                TrainingSnapshot(entries.take(11), types, dataRevision = 1L, trainedAtEpochMs = 1L),
            )
        assertNotNull(prefixState)
        val state11 = DecodedModel.decode(prefixState)!!
        assertEquals(11, state11.observationCount)

        val incremental12 =
            BayesianModelTrainer.train(
                TrainingSnapshot(entries, types, dataRevision = 2L, trainedAtEpochMs = 2L, previousState = prefixState),
            )
        val decodedInc12 = DecodedModel.decode(incremental12)!!
        assertEquals(12, decodedInc12.observationCount)
        assertEquals(state11.scaler, decodedInc12.scaler)

        val lastEntry = entries.last()
        val lastRaw = FeatureExtractor.extractRaw(8.0 + 11 * 1.4, false, lastEntry.pieces, typeById)
        val lastFeatures = FeatureExtractor.buildFeatures(lastRaw, state11.scaler, lastEntry.pieces, state11.activePieceIndexById)
        val lastScore = lastEntry.entry.surveyFeelingScore!!.toDouble()
        val expectedLastRating =
            BayesianRatingModel.update(
                state11.ratingPosterior,
                lastFeatures,
                lastScore - RidgeRatingModel.priorPrediction(lastRaw),
                deltaDays = 1.0,
            )
        for (i in 0 until decodedInc12.dimension) {
            assertEquals(expectedLastRating.mean[i], decodedInc12.ratingPosterior.mean[i], 1e-12)
        }
    }

    @Test
    fun `survey edit triggers clean chronological replay with zero double counting`() =
        runTest {
            val tee = piece(1, teeType.id, Material.COTTON)
            val sweater = piece(2, sweaterType.id, Material.WOOL)
            val jeans = piece(3, jeansType.id, Material.COTTON)
            val entries = buildSampleHistory(tee, sweater, jeans)

            val dispatcher = StandardTestDispatcher(testScheduler)
            val outfitDao = FakeOutfitDao()
            val clothingDao = FakeClothingDao()
            clothingDao.types.value = types
            clothingDao.clothing.value = listOf(tee, sweater, jeans)
            outfitDao.entries.value = entries
            outfitDao.links += entries.flatMap { e -> e.pieces.map { p -> OutfitEntryPiece(e.entry.id, p.id) } }

            val coordinator = RecommendationTrainingCoordinator(outfitDao, clothingDao, dispatcher, appScope = this)
            val repo = OutfitLogRepository(outfitDao, clothingDao, dispatcher, imageDeleter = {}, appScope = this, coordinator)

            val editedThird = entries[2].entry.copy(surveyFeelingScore = 1, surveyWarmthVerdict = WarmthVerdict.TOO_WARM)
            repo.updateOutfit(editedThird, entries[2].pieces)
            advanceUntilIdle()

            val fromCoordinator = DecodedModel.decode(outfitDao.getModelState())!!
            val editedHistory = entries.toMutableList().apply { set(2, OutfitEntryWithPieces(editedThird, entries[2].pieces)) }
            val freshReplay =
                DecodedModel.decode(
                    BayesianModelTrainer.train(TrainingSnapshot(editedHistory, types, dataRevision = 99L, trainedAtEpochMs = 99L)),
                )!!

            assertEquals(freshReplay.dimension, fromCoordinator.dimension)
            for (i in 0 until fromCoordinator.dimension) {
                assertEquals(freshReplay.ratingPosterior.mean[i], fromCoordinator.ratingPosterior.mean[i], 1e-11)
                assertEquals(freshReplay.thermalPosterior.mean[i], fromCoordinator.thermalPosterior.mean[i], 1e-11)
            }
            assertEquals(freshReplay.historyFingerprint, fromCoordinator.historyFingerprint)
        }

    private fun buildSampleHistory(
        tee: ClothingPiece,
        sweater: ClothingPiece,
        jeans: ClothingPiece,
    ): List<OutfitEntryWithPieces> =
        (0 until 12).map { idx ->
            val temp = 8.0 + idx * 1.4
            val pieces = if (idx % 2 == 0) listOf(tee, jeans) else listOf(sweater, jeans)
            val score = if (idx % 3 == 0) 3 else 5
            val verdict = if (idx % 3 == 0) WarmthVerdict.TOO_COLD else WarmthVerdict.PERFECT
            OutfitEntryWithPieces(entry(idx + 1L, idx, temp, score, verdict), pieces)
        }

    @Test
    fun `smooth Bayesian shrinkage applies below 10 surveys without an abrupt jump`() {
        val tee = piece(1, teeType.id, Material.COTTON)
        val sweater = piece(2, sweaterType.id, Material.WOOL)
        val jeans = piece(3, jeansType.id, Material.COTTON)
        val history =
            (0 until 4).map { idx ->
                OutfitEntryWithPieces(entry(idx + 1L, idx, 14.0 + idx, 5, WarmthVerdict.PERFECT), listOf(sweater, jeans))
            }
        val state = BayesianModelTrainer.train(TrainingSnapshot(history, types, 1L, 1L))
        assertNotNull(state)
        val decoded = DecodedModel.decode(state)!!

        val input =
            RecommendationInput(
                date = baseDate,
                today = baseDate,
                tempC = 14.0,
                isRainy = false,
                wardrobePieces = listOf(tee, sweater, jeans),
                clothingTypes = types,
                entries = history,
                surveyedCount = 4,
                modelState = state,
            )
        val candidate = OutfitCandidate(null, listOf(sweater, jeans), null, null, RecommendationSource.HEURISTIC)
        val score = Scoring.scoreCandidate(input, candidate, typeById, RecommendationMode.HEURISTIC, decoded)
        val expectedModelScore = Scoring.modelScore(input, candidate.pieces, typeById, decoded)
        assertEquals(expectedModelScore, score, 1e-12)
    }

    @Test
    fun `Gate B shadow benchmark improves thermal calibration and meets sub-frame latency budgets`() {
        val multiSeason = listOf(PersonaKind.COLD_SENSITIVE, PersonaKind.WARM_RUNNING, PersonaKind.RAIN_AVERSE)
        for (persona in multiSeason) {
            val dataset = SyntheticPersonas.generate(persona, seed = 42)
            val heuristic = EvaluationHarness.evaluate(dataset, EvaluationPolicy.HEURISTIC)
            val ridgeV2 = EvaluationHarness.evaluate(dataset, EvaluationPolicy.INTERACTION_RIDGE_V2)
            val bayesRating = EvaluationHarness.evaluate(dataset, EvaluationPolicy.BAYESIAN_RATING_V3)
            val bayesCombined = EvaluationHarness.evaluate(dataset, EvaluationPolicy.BAYESIAN_COMBINED_V3)

            assertTrue(
                bayesRating.overall.mae < heuristic.overall.mae,
                "$persona: expected Bayesian MAE ${bayesRating.overall.mae} < heuristic ${heuristic.overall.mae}",
            )
            assertTrue(
                bayesRating.overall.mae <= ridgeV2.overall.mae + 0.05,
                "$persona: expected Bayesian MAE ${bayesRating.overall.mae} comparable to ridge ${ridgeV2.overall.mae}",
            )
            assertTrue(
                bayesCombined.thermal.ordinalLogLoss < heuristic.thermal.ordinalLogLoss,
                "$persona: log loss ${bayesCombined.thermal.ordinalLogLoss} vs ${heuristic.thermal.ordinalLogLoss}",
            )
            assertTrue(
                bayesCombined.thermal.brierScore < heuristic.thermal.brierScore,
                "$persona: brier ${bayesCombined.thermal.brierScore} vs ${heuristic.thermal.brierScore}",
            )
            assertTrue(
                bayesCombined.thermal.expectedCalibrationError < 0.25,
                "$persona: expected bounded ECE, got ${bayesCombined.thermal.expectedCalibrationError}",
            )
            assertTrue(
                abs(bayesCombined.weatherReversalAccuracy - 1.0) < 1e-9,
                "$persona: expected 1.0 reversal accuracy, got ${bayesCombined.weatherReversalAccuracy}",
            )
        }

        val sparseDataset = SyntheticPersonas.generate(PersonaKind.SPARSE_COLLINEAR, seed = 42)
        val sparseBayes = EvaluationHarness.evaluate(sparseDataset, EvaluationPolicy.BAYESIAN_COMBINED_V3)
        assertTrue(sparseBayes.overall.mae.isFinite() && sparseBayes.thermal.ordinalLogLoss.isFinite())

        for (dim in listOf(16, 32)) {
            val latency = EvaluationHarness.benchmarkDimensionLatency(dim)
            assertTrue(
                latency.updateP95Ms < 16.0,
                "Expected d=$dim update p95 < 16ms, got ${latency.updateP95Ms}ms",
            )
            assertTrue(
                latency.rankingP95Ms < 16.0,
                "Expected d=$dim 120-candidate ranking p95 < 16ms, got ${latency.rankingP95Ms}ms",
            )
        }
    }
}
