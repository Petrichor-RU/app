package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RidgeRatingModelTest {
    private val today = LocalDate(2026, 6, 10)
    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val sweaterType = ClothingType(id = 11, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 12, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 13, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val pantsType = ClothingType(id = 14, name = "Pants", supertype = ClothingSupertype.LOWER_BODY)
    private val types = listOf(teeType, sweaterType, jacketType, jeansType, pantsType)
    private val typeById = types.associateBy { it.id }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.COTTON,
    ) = ClothingPiece(id = id, name = "p$id", typeId = typeId, material = material, notes = null, imagePath = "", thumbnailPath = "")

    private fun entry(
        id: Long,
        tempC: Double,
        score: Int,
        isRainy: Boolean = false,
    ) = OutfitEntry(
        id = id,
        wornOnDate = today,
        photoPath = null,
        notes = null,
        temperatureC = tempC,
        feelsLikeC = tempC,
        minTempC = tempC - 2.0,
        maxTempC = tempC + 2.0,
        weatherDescription = null,
        weatherIcon = null,
        isRainy = isRainy,
        surveyFeelingScore = score,
        surveyWarmthVerdict = WarmthVerdict.PERFECT,
        surveyedAtEpochMs = 1_800_000_000_000L + id,
        createdAtEpochMs = 1_800_000_000_000L + id,
    )

    @Test
    fun `feature extractor produces ten scaled interaction features and safely handles constant inputs`() {
        val tee = piece(1, teeType.id, Material.LINEN)
        val raw = FeatureExtractor.extractRaw(18.0, isRainy = false, listOf(tee), typeById)
        val scaler = FeatureScaler.fit(listOf(raw))
        assertTrue(scaler.isValid())

        val features = FeatureExtractor.buildFeatures(raw, scaler)
        assertEquals(FeatureExtractor.FEATURE_COUNT, features.size)
        assertEquals(1.0, features[0], 1e-12)
        // Constant n=1 sequence centres continuous features at 0.0 without NaN or Inf.
        for (i in 1 until features.size) {
            assertEquals(0.0, features[i], 1e-12)
        }
    }

    @Test
    fun `ridge fit stays finite on collinear and single-sample datasets`() {
        val tee = piece(1, teeType.id, Material.COTTON)
        val jeans = piece(2, jeansType.id, Material.COTTON)
        val single = listOf(FeatureExtractor.extractRaw(15.0, false, listOf(tee, jeans), typeById) to 4.0)
        val singleArtifact = RidgeRatingModel.fit(single)
        assertEquals(FeatureExtractor.FEATURE_COUNT, singleArtifact.ridgeWeights.size)
        assertTrue(singleArtifact.ridgeWeights.all { it.isFinite() })

        val duplicate = List(6) { FeatureExtractor.extractRaw(15.0, false, listOf(tee, jeans), typeById) to 5.0 }
        val dupArtifact = RidgeRatingModel.fit(duplicate)
        assertTrue(dupArtifact.ridgeWeights.all { it.isFinite() })
        val pred = RidgeRatingModel.predict(dupArtifact, single.first().first)
        assertTrue(pred in 1.0..5.0)
    }

    private fun multiSeasonSamples(
        winterOutfit: List<ClothingPiece>,
        mildOutfit: List<ClothingPiece>,
        summerOutfit: List<ClothingPiece>,
    ): List<Pair<RawOutfitSignals, Double>> =
        listOf(
            FeatureExtractor.extractRaw(2.0, false, winterOutfit, typeById) to 5.0,
            FeatureExtractor.extractRaw(4.0, false, winterOutfit, typeById) to 5.0,
            FeatureExtractor.extractRaw(4.0, false, summerOutfit, typeById) to 1.0,
            FeatureExtractor.extractRaw(14.0, false, mildOutfit, typeById) to 5.0,
            FeatureExtractor.extractRaw(14.0, false, winterOutfit, typeById) to 3.0,
            FeatureExtractor.extractRaw(14.0, false, summerOutfit, typeById) to 2.0,
            FeatureExtractor.extractRaw(25.0, false, summerOutfit, typeById) to 5.0,
            FeatureExtractor.extractRaw(26.0, false, summerOutfit, typeById) to 5.0,
            FeatureExtractor.extractRaw(26.0, false, winterOutfit, typeById) to 1.0,
            FeatureExtractor.extractRaw(24.0, false, winterOutfit, typeById) to 1.0,
        )

    @Test
    fun `exact same fitted model ranks winter outfit above light outfit at 4C and reverses at 26C`() {
        val woolJacket = piece(1, jacketType.id, Material.WOOL)
        val woolSweater = piece(2, sweaterType.id, Material.WOOL)
        val linenTee = piece(3, teeType.id, Material.LINEN)
        val jeans = piece(4, jeansType.id, Material.COTTON)
        val linenPants = piece(5, pantsType.id, Material.LINEN)

        val winterOutfit = listOf(woolJacket, woolSweater, jeans)
        val mildOutfit = listOf(woolSweater, jeans)
        val summerOutfit = listOf(linenTee, linenPants)
        val samples = multiSeasonSamples(winterOutfit, mildOutfit, summerOutfit)

        val artifact = RidgeRatingModel.fit(samples)
        val modelState =
            ModelState(
                schemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
                dataRevision = 1L,
                trainedAtEpochMs = 1_800_000_000_000L,
                usableSampleCount = samples.size,
                artifactJson = Json.encodeToString(artifact),
            )
        val dummyHistory = samples.indices.map { i -> OutfitEntryWithPieces(entry(i + 1L, tempC = 14.0, score = 4), mildOutfit) }
        val pool = listOf(woolJacket, woolSweater, linenTee, jeans, linenPants)

        val coldTop =
            RecommendationEngine
                .recommendOutfits(RecommendationInput(today, today, 4.0, false, pool, types, dummyHistory, samples.size, modelState))
                .first()
        val warmTop =
            RecommendationEngine
                .recommendOutfits(RecommendationInput(today, today, 26.0, false, pool, types, dummyHistory, samples.size, modelState))
                .first()

        assertEquals(RecommendationSource.MODEL, coldTop.source)
        assertEquals(RecommendationSource.MODEL, warmTop.source)
        assertTrue(coldTop.pieces.any { it.id == woolJacket.id || it.id == woolSweater.id })
        assertTrue(warmTop.pieces.any { it.id == linenTee.id })
        assertTrue(warmTop.pieces.none { it.id == woolJacket.id })
    }

    @Test
    fun `decoded model rejects invalid json, schema mismatch, non-finite weights, and bad scaler`() {
        val validScaler = FeatureScaler(15.0, 5.0, 0.5, 0.25, 0.4, 0.2, 0.0, 1.0, 1.0, 0.5, 0.9, 0.4)
        val validArtifact = RecommendationModelArtifact(scaler = validScaler, ridgeWeights = List(FeatureExtractor.FEATURE_COUNT) { 0.0 })
        val validState =
            ModelState(
                schemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
                dataRevision = 1L,
                trainedAtEpochMs = 1L,
                usableSampleCount = 1,
                artifactJson = Json.encodeToString(validArtifact),
            )
        assertNotNull(DecodedModel.decode(validState))
        assertNull(DecodedModel.decode(validState.copy(schemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION + 1)))
        assertNull(DecodedModel.decode(validState.copy(artifactJson = "{not-valid-json")))

        val wrongSizeArtifact = validArtifact.copy(ridgeWeights = listOf(0.0, 1.0))
        assertNull(DecodedModel.decode(validState.copy(artifactJson = Json.encodeToString(wrongSizeArtifact))))

        val zeroStdScaler = validScaler.copy(tempCStd = 0.0)
        val badScalerArtifact = validArtifact.copy(scaler = zeroStdScaler)
        assertNull(DecodedModel.decode(validState.copy(artifactJson = Json.encodeToString(badScalerArtifact))))
    }

    @Test
    fun `interaction ridge beats legacy additive linear model and heuristic on multi-season personas`() {
        val multiSeasonPersonas = listOf(PersonaKind.COLD_SENSITIVE, PersonaKind.WARM_RUNNING, PersonaKind.RAIN_AVERSE)
        for (persona in multiSeasonPersonas) {
            val dataset = SyntheticPersonas.generate(persona, seed = 42)
            val heuristic = EvaluationHarness.evaluate(dataset, EvaluationPolicy.HEURISTIC)
            val additive = EvaluationHarness.evaluate(dataset, EvaluationPolicy.ADDITIVE_LINEAR_V1)
            val interaction = EvaluationHarness.evaluate(dataset, EvaluationPolicy.INTERACTION_RIDGE_V2)

            assertTrue(
                interaction.overall.rmse < additive.overall.rmse,
                "Expected v2 RMSE (${interaction.overall.rmse}) < additive RMSE (${additive.overall.rmse}) on $persona",
            )
            assertTrue(
                interaction.overall.rmse < heuristic.overall.rmse,
                "Expected v2 RMSE (${interaction.overall.rmse}) < heuristic RMSE (${heuristic.overall.rmse}) on $persona",
            )
            assertTrue(
                interaction.overall.mae < additive.overall.mae,
                "Expected v2 MAE (${interaction.overall.mae}) < additive MAE (${additive.overall.mae}) on $persona",
            )
            assertEquals(1.0, interaction.weatherReversalAccuracy, 1e-9)
            assertTrue(interaction.rankingLatencyP95Ms < 50.0)
            assertTrue(interaction.updateLatencyP95Ms < 100.0)
        }

        // Sparse/collinear persona completes cleanly with finite errors and no division-by-zero spikes.
        val sparse = SyntheticPersonas.generate(PersonaKind.SPARSE_COLLINEAR, seed = 7)
        val sparseReport = EvaluationHarness.evaluate(sparse, EvaluationPolicy.INTERACTION_RIDGE_V2)
        assertTrue(sparseReport.overall.mae.isFinite())
        assertTrue(sparseReport.overall.rmse.isFinite())
    }
}
