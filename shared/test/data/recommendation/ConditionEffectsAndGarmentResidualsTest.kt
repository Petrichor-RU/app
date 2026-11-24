package nl.petrichor.app.data.recommendation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection
import org.jetbrains.kotlinx.multik.ndarray.data.get
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConditionEffectsAndGarmentResidualsTest {
    private val baseDate = LocalDate(2026, 5, 1)
    private val teeType = ClothingType(id = 1L, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val shirtType = ClothingType(id = 2L, name = "Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 3L, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 4L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 5L, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val pantsType = ClothingType(id = 6L, name = "Pants", supertype = ClothingSupertype.LOWER_BODY)
    private val types = listOf(teeType, shirtType, sweaterType, jacketType, jeansType, pantsType)
    private val typeById = types.associateBy { it.id }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.COTTON,
        protection: WeatherProtection = WeatherProtection.NONE,
    ) = ClothingPiece(
        id = id,
        name = "piece-$id",
        typeId = typeId,
        material = material,
        weatherProtection = protection,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    @Suppress("LongParameterList") // concise observation builder for condition tests
    private fun observation(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        pieces: List<ClothingPiece>,
        score: Int,
        verdict: WarmthVerdict,
        isRainy: Boolean = false,
        windSpeedMs: Double? = null,
        precipitationProbability: Double? = null,
        rainVolumeMm: Double? = null,
    ): OutfitEntryWithPieces {
        val ts = 1_785_000_000_000L + dayOffset * 86_400_000L
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
                    windSpeedMs = windSpeedMs,
                    precipitationProbability = precipitationProbability,
                    rainVolumeMm = rainVolumeMm,
                    surveyFeelingScore = score,
                    surveyWarmthVerdict = verdict,
                    surveyedAtEpochMs = ts,
                    createdAtEpochMs = ts,
                ),
            pieces = pieces,
        )
    }

    @Test
    fun `single rainy experience updates pooled wet terms while preserving dry warmth predictions`() {
        val tee = piece(1L, teeType.id, Material.COTTON)
        val cottonJacket = piece(2L, jacketType.id, Material.COTTON, WeatherProtection.NONE)
        val rainShell = piece(3L, jacketType.id, Material.POLYESTER, WeatherProtection.WATERPROOF)
        val jeans = piece(4L, jeansType.id, Material.COTTON)

        val dryHistory =
            listOf(
                observation(1L, 0, 14.0, listOf(cottonJacket, jeans), 5, WarmthVerdict.PERFECT),
                observation(2L, 1, 15.0, listOf(cottonJacket, jeans), 5, WarmthVerdict.PERFECT),
                observation(3L, 2, 13.0, listOf(cottonJacket, jeans), 5, WarmthVerdict.PERFECT),
                observation(4L, 3, 21.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
                observation(5L, 4, 22.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
                observation(6L, 5, 19.0, listOf(tee, jeans), 5, WarmthVerdict.PERFECT),
                observation(7L, 6, 12.0, listOf(rainShell, tee, jeans), 5, WarmthVerdict.PERFECT),
                observation(8L, 7, 13.0, listOf(rainShell, tee, jeans), 5, WarmthVerdict.PERFECT),
            )
        val beforeModel = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(dryHistory, types, 1L, 1L)))!!
        val dryBefore = BayesianComfortModel.predict(beforeModel, 14.0, false, listOf(cottonJacket, jeans), typeById)

        val withRain =
            dryHistory +
                observation(
                    id = 9L,
                    dayOffset = 8,
                    tempC = 14.0,
                    pieces = listOf(cottonJacket, jeans),
                    score = 1,
                    verdict = WarmthVerdict.TOO_COLD,
                    isRainy = true,
                    precipitationProbability = 0.9,
                    rainVolumeMm = 6.0,
                )
        val afterModel = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(withRain, types, 2L, 2L)))!!
        val dryAfter = BayesianComfortModel.predict(afterModel, 14.0, false, listOf(cottonJacket, jeans), typeById)
        val rainyUnprotected =
            BayesianComfortModel.predict(
                afterModel,
                14.0,
                true,
                listOf(cottonJacket, jeans),
                typeById,
                precipitationProbability = 0.9,
                rainVolumeMm = 6.0,
            )
        val rainyProtected =
            BayesianComfortModel.predict(
                afterModel,
                14.0,
                true,
                listOf(rainShell, jeans),
                typeById,
                precipitationProbability = 0.9,
                rainVolumeMm = 6.0,
            )

        // Index 10 is `rainExposure * (1.0 - rainProtectionScore)`.
        assertTrue(afterModel.ratingPosterior.mean[10] < beforeModel.ratingPosterior.mean[10] - 0.15)
        assertTrue(afterModel.thermalPosterior.mean[10] < beforeModel.thermalPosterior.mean[10] - 0.1)
        assertTrue(abs(dryAfter.rating.mean - dryBefore.rating.mean) < 0.3)
        assertTrue(dryAfter.rating.mean > rainyUnprotected.rating.mean + 0.5)
        assertTrue(rainyProtected.rating.mean > rainyUnprotected.rating.mean + 0.3)
    }

    @Test
    fun `always co-worn garments retain higher posterior variance and negative covariance than varied pairs`() {
        val coSweater = piece(10L, sweaterType.id, Material.WOOL)
        val coPants = piece(11L, pantsType.id, Material.WOOL)
        val variedSweater = piece(12L, sweaterType.id, Material.WOOL)
        val altSweater = piece(13L, sweaterType.id, Material.COTTON)
        val tee = piece(16L, teeType.id, Material.COTTON)
        val jacket = piece(17L, jacketType.id, Material.COTTON)
        val variedJeans = piece(14L, jeansType.id, Material.COTTON)
        val variedPants = piece(15L, pantsType.id, Material.WOOL)

        val entries = mutableListOf<OutfitEntryWithPieces>()
        repeat(6) { idx ->
            entries += observation(idx + 1L, idx, 11.0 + idx, listOf(coSweater, coPants), 4, WarmthVerdict.PERFECT)
        }
        val variedOutfits =
            listOf(
                listOf(variedSweater, variedJeans),
                listOf(variedSweater, variedPants),
                listOf(variedSweater, jacket, variedJeans),
                listOf(variedSweater, variedJeans),
                listOf(variedSweater, variedPants),
                listOf(variedSweater, jacket, variedPants),
                listOf(altSweater, variedJeans),
                listOf(altSweater, variedPants),
                listOf(tee, jacket, variedJeans),
                listOf(tee, jacket, variedPants),
                listOf(tee, variedJeans),
                listOf(tee, variedPants),
            )
        variedOutfits.forEachIndexed { idx, outfit ->
            entries += observation(20L + idx, 10 + idx, 11.0 + (idx % 6), outfit, 5, WarmthVerdict.PERFECT)
        }

        val model = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(entries, types, 1L, 1L)))!!
        val core = FeatureExtractor.CORE_FEATURE_COUNT
        val coSweaterCol = core + model.activePieceIndexById.getValue(coSweater.id)
        val coPantsCol = core + model.activePieceIndexById.getValue(coPants.id)
        val variedSweaterCol = core + model.activePieceIndexById.getValue(variedSweater.id)

        val coRatingVar = model.ratingPosterior.covariance[coSweaterCol, coSweaterCol]
        val variedRatingVar = model.ratingPosterior.covariance[variedSweaterCol, variedSweaterCol]
        val coThermalVar = model.thermalPosterior.covariance[coSweaterCol, coSweaterCol]
        val variedThermalVar = model.thermalPosterior.covariance[variedSweaterCol, variedSweaterCol]
        val coRatingCov = model.ratingPosterior.covariance[coSweaterCol, coPantsCol]
        val coThermalCov = model.thermalPosterior.covariance[coSweaterCol, coPantsCol]

        assertTrue(coRatingVar > variedRatingVar, "Expected coRatingVar=$coRatingVar > variedRatingVar=$variedRatingVar")
        assertTrue(coThermalVar > variedThermalVar, "Expected coThermalVar=$coThermalVar > variedThermalVar=$variedThermalVar")
        assertTrue(coRatingCov < 0.0, "Expected coRatingCov=$coRatingCov < 0")
        assertTrue(coThermalCov < 0.0, "Expected coThermalCov=$coThermalCov < 0")
    }

    @Test
    fun `unseen and once-worn pieces inherit zero residual mean with full prior variance while capping active pieces`() {
        val baseJeans = piece(100L, jeansType.id, Material.COTTON)
        val repeatedTees = (1L..28L).map { id -> piece(id, teeType.id, Material.COTTON) }
        val onceWornTee = piece(200L, teeType.id, Material.COTTON)
        val unseenTee = piece(201L, teeType.id, Material.COTTON)

        val entries = mutableListOf<OutfitEntryWithPieces>()
        var nextId = 1L
        repeatedTees.forEachIndexed { idx, tee ->
            repeat(2) { rep ->
                val temp = 15.0 + ((idx + rep) % 8)
                entries += observation(nextId, nextId.toInt(), temp, listOf(tee, baseJeans), 5, WarmthVerdict.PERFECT)
                nextId++
            }
        }
        entries += observation(nextId, nextId.toInt(), 18.0, listOf(onceWornTee, baseJeans), 4, WarmthVerdict.PERFECT)

        val model = DecodedModel.decode(BayesianModelTrainer.train(TrainingSnapshot(entries, types, 1L, 1L)))
        assertNotNull(model)
        assertEquals(FeatureExtractor.MAX_ACTIVE_PIECES, model.activePieceIds.size)
        assertEquals(FeatureExtractor.MAX_TOTAL_FEATURES, model.dimension)
        assertFalse(onceWornTee.id in model.activePieceIndexById)
        assertFalse(unseenTee.id in model.activePieceIndexById)

        val activeTee = repeatedTees.first { it.id in model.activePieceIndexById }
        val activePred = BayesianComfortModel.predict(model, 18.0, false, listOf(activeTee, baseJeans), typeById)
        val unseenPred = BayesianComfortModel.predict(model, 18.0, false, listOf(unseenTee, baseJeans), typeById)
        val rarePred = BayesianComfortModel.predict(model, 18.0, false, listOf(onceWornTee, baseJeans), typeById)

        assertTrue(unseenPred.rating.epistemicStd > activePred.rating.epistemicStd)
        assertTrue(unseenPred.thermal.epistemicStd > activePred.thermal.epistemicStd)
        assertTrue(rarePred.rating.epistemicStd > activePred.rating.epistemicStd)

        val unseenRaw = FeatureExtractor.extractRaw(18.0, false, listOf(unseenTee, baseJeans), typeById)
        val unseenFeatures =
            FeatureExtractor.buildFeatures(unseenRaw, model.scaler, listOf(unseenTee, baseJeans), model.activePieceIndexById)
        val baseJeansSlot = FeatureExtractor.CORE_FEATURE_COUNT + model.activePieceIndexById.getValue(baseJeans.id)
        for (slot in FeatureExtractor.CORE_FEATURE_COUNT until model.dimension) {
            if (slot != baseJeansSlot) {
                assertEquals(0.0, unseenFeatures[slot])
            }
        }
    }

    @Test
    fun `held-out evaluation improves log loss and brier score on rainy windy days and unseen combinations`() {
        val suite = buildConditionBenchmarkSuite()
        assertBayesianBeatsHeuristic("Rain/wind", suite.trainEntries, suite.rainyWindyHoldout, suite.wardrobe)
        assertBayesianBeatsHeuristic("Unseen", suite.trainEntries, suite.unseenComboHoldout, suite.wardrobe)

        val maxFeatureLatency = EvaluationHarness.benchmarkDimensionLatency(FeatureExtractor.MAX_TOTAL_FEATURES)
        assertTrue(maxFeatureLatency.updateP95Ms < 16.0, "d=38 update p95=${maxFeatureLatency.updateP95Ms}ms")
        assertTrue(maxFeatureLatency.rankingP95Ms < 16.0, "d=38 ranking p95=${maxFeatureLatency.rankingP95Ms}ms")
    }

    private fun assertBayesianBeatsHeuristic(
        sliceName: String,
        trainEntries: List<OutfitEntryWithPieces>,
        holdoutEntries: List<OutfitEntryWithPieces>,
        wardrobe: List<ClothingPiece>,
    ) {
        val heuristic = EvaluationHarness.evaluateHoldout(trainEntries, holdoutEntries, types, wardrobe, EvaluationPolicy.HEURISTIC)
        val bayes =
            EvaluationHarness.evaluateHoldout(trainEntries, holdoutEntries, types, wardrobe, EvaluationPolicy.BAYESIAN_COMBINED_V3)
        assertTrue(
            bayes.thermal.ordinalLogLoss < heuristic.thermal.ordinalLogLoss,
            "$sliceName logLoss: bayes=${bayes.thermal.ordinalLogLoss} vs heuristic=${heuristic.thermal.ordinalLogLoss}",
        )
        assertTrue(
            bayes.thermal.brierScore < heuristic.thermal.brierScore,
            "$sliceName brier: bayes=${bayes.thermal.brierScore} vs heuristic=${heuristic.thermal.brierScore}",
        )
        assertTrue(
            bayes.rating.mae < heuristic.rating.mae,
            "$sliceName MAE: bayes=${bayes.rating.mae} vs heuristic=${heuristic.rating.mae}",
        )
    }

    private fun buildConditionBenchmarkSuite(): ConditionBenchmarkSuite {
        val tee = piece(1L, teeType.id, Material.COTTON)
        val shirt = piece(2L, shirtType.id, Material.COTTON)
        val sweater = piece(3L, sweaterType.id, Material.WOOL)
        val windShell = piece(4L, jacketType.id, Material.POLYESTER, WeatherProtection.WIND_RESISTANT)
        val rainShell = piece(5L, jacketType.id, Material.POLYESTER, WeatherProtection.WATERPROOF)
        val cottonJacket = piece(6L, jacketType.id, Material.COTTON, WeatherProtection.NONE)
        val jeans = piece(7L, jeansType.id, Material.COTTON)
        val woolPants = piece(8L, pantsType.id, Material.WOOL)
        val wardrobe = listOf(tee, shirt, sweater, windShell, rainShell, cottonJacket, jeans, woolPants)

        val trainScenarios =
            listOf(
                ScenarioSpec(listOf(tee, jeans), 22.0, isRainy = false, windMs = 2.0, pop = 0.0, rainMm = 0.0),
                ScenarioSpec(listOf(tee, windShell, jeans), 14.0, isRainy = false, windMs = 11.0, pop = 0.1, rainMm = 0.0),
                ScenarioSpec(listOf(tee, rainShell, jeans), 13.0, isRainy = true, windMs = 9.0, pop = 0.85, rainMm = 5.0),
                ScenarioSpec(listOf(shirt, cottonJacket, jeans), 12.0, isRainy = true, windMs = 10.0, pop = 0.85, rainMm = 5.0),
                ScenarioSpec(listOf(shirt, cottonJacket, jeans), 13.0, isRainy = false, windMs = 2.0, pop = 0.0, rainMm = 0.0),
                ScenarioSpec(listOf(sweater, woolPants), 7.0, isRainy = false, windMs = 11.0, pop = 0.1, rainMm = 0.0),
                ScenarioSpec(listOf(sweater, rainShell, woolPants), 6.0, isRainy = true, windMs = 10.0, pop = 0.9, rainMm = 6.0),
                ScenarioSpec(listOf(shirt, jeans), 19.0, isRainy = false, windMs = 12.0, pop = 0.0, rainMm = 0.0),
                ScenarioSpec(listOf(sweater, jeans), 10.0, isRainy = true, windMs = 8.0, pop = 0.8, rainMm = 4.0),
                ScenarioSpec(listOf(tee, cottonJacket, jeans), 11.0, isRainy = false, windMs = 10.0, pop = 0.0, rainMm = 0.0),
            )
        val trainEntries =
            (0 until 40).map { idx ->
                val spec = trainScenarios[idx % trainScenarios.size]
                val temp = spec.tempC + (idx % 3 - 1) * 0.8
                simulatedConditionEntry(idx + 1L, idx, spec.copy(tempC = temp))
            }
        val rainyWindyHoldout =
            listOf(
                ScenarioSpec(listOf(tee, rainShell, jeans), 12.5, isRainy = true, windMs = 11.0, pop = 0.9, rainMm = 6.0),
                ScenarioSpec(listOf(shirt, cottonJacket, jeans), 12.5, isRainy = true, windMs = 11.0, pop = 0.9, rainMm = 6.0),
                ScenarioSpec(listOf(tee, windShell, jeans), 13.0, isRainy = false, windMs = 12.5, pop = 0.1, rainMm = 0.0),
                ScenarioSpec(listOf(sweater, jeans), 11.0, isRainy = true, windMs = 10.0, pop = 0.85, rainMm = 4.5),
                ScenarioSpec(listOf(sweater, rainShell, woolPants), 5.5, isRainy = true, windMs = 11.0, pop = 0.9, rainMm = 5.0),
                ScenarioSpec(listOf(shirt, jeans), 17.5, isRainy = false, windMs = 12.0, pop = 0.0, rainMm = 0.0),
            ).mapIndexed { idx, spec -> simulatedConditionEntry(100L + idx, 50 + idx, spec) }
        val unseenComboHoldout =
            listOf(
                ScenarioSpec(listOf(shirt, rainShell, jeans), 13.5, isRainy = true, windMs = 9.0, pop = 0.8, rainMm = 4.0),
                ScenarioSpec(listOf(shirt, windShell, woolPants), 10.0, isRainy = false, windMs = 11.0, pop = 0.1, rainMm = 0.0),
                ScenarioSpec(listOf(tee, rainShell, woolPants), 11.5, isRainy = true, windMs = 8.0, pop = 0.8, rainMm = 3.5),
                ScenarioSpec(listOf(sweater, windShell, jeans), 9.0, isRainy = false, windMs = 10.0, pop = 0.1, rainMm = 0.0),
                ScenarioSpec(listOf(tee, woolPants), 16.0, isRainy = false, windMs = 3.0, pop = 0.0, rainMm = 0.0),
                ScenarioSpec(listOf(shirt, cottonJacket, woolPants), 10.5, isRainy = true, windMs = 9.0, pop = 0.85, rainMm = 5.0),
                ScenarioSpec(listOf(tee, windShell, woolPants), 12.0, isRainy = false, windMs = 12.0, pop = 0.0, rainMm = 0.0),
                ScenarioSpec(listOf(sweater, cottonJacket, jeans), 7.5, isRainy = true, windMs = 8.5, pop = 0.8, rainMm = 4.0),
            ).mapIndexed { idx, spec -> simulatedConditionEntry(200L + idx, 70 + idx, spec) }

        return ConditionBenchmarkSuite(wardrobe, trainEntries, rainyWindyHoldout, unseenComboHoldout)
    }

    private fun simulatedConditionEntry(
        id: Long,
        dayOffset: Int,
        spec: ScenarioSpec,
    ): OutfitEntryWithPieces {
        val raw =
            FeatureExtractor.extractRaw(
                tempC = spec.tempC,
                isRainy = spec.isRainy,
                pieces = spec.pieces,
                typeById = typeById,
                windSpeedMs = spec.windMs,
                precipitationProbability = spec.pop,
                rainVolumeMm = spec.rainMm,
            )
        // Realistic cold-sensitive user with explicit unprotected wind & rain exposure penalties.
        val idealDry = (2.60 - 0.08 * spec.tempC).coerceIn(0.25, 3.0)
        val windCooling = 0.55 * raw.windExposure * (1.0 - raw.windProtectionScore)
        val rainCooling = 0.75 * raw.rainExposure * (1.0 - raw.rainProtectionScore)
        val sensationDiff = raw.totalWarmth - (idealDry + windCooling + rainCooling)
        val wetnessDiscomfort =
            1.45 * raw.rainExposure * (1.0 - raw.rainProtectionScore) +
                0.6 * raw.windExposure * (1.0 - raw.windProtectionScore)
        val score =
            (5.0 - 2.0 * abs(sensationDiff) - wetnessDiscomfort)
                .coerceIn(1.0, 5.0)
                .roundToInt()
        val verdict =
            when {
                sensationDiff < -0.3 -> WarmthVerdict.TOO_COLD
                sensationDiff > 0.35 -> WarmthVerdict.TOO_WARM
                else -> WarmthVerdict.PERFECT
            }
        return observation(
            id = id,
            dayOffset = dayOffset,
            tempC = spec.tempC,
            pieces = spec.pieces,
            score = score,
            verdict = verdict,
            isRainy = spec.isRainy,
            windSpeedMs = spec.windMs,
            precipitationProbability = spec.pop,
            rainVolumeMm = spec.rainMm,
        )
    }

    private data class ScenarioSpec(
        val pieces: List<ClothingPiece>,
        val tempC: Double,
        val isRainy: Boolean,
        val windMs: Double,
        val pop: Double,
        val rainMm: Double,
    )

    private data class ConditionBenchmarkSuite(
        val wardrobe: List<ClothingPiece>,
        val trainEntries: List<OutfitEntryWithPieces>,
        val rainyWindyHoldout: List<OutfitEntryWithPieces>,
        val unseenComboHoldout: List<OutfitEntryWithPieces>,
    )
}
