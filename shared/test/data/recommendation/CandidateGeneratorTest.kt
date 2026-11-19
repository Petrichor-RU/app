package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateGeneratorTest {
    private val today = LocalDate(2026, 6, 10)
    private val now = 1_800_000_000_000L

    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 11, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 12, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val dressType = ClothingType(id = 13, name = "Dresses", supertype = ClothingSupertype.FULL_BODY)
    private val types = listOf(teeType, jacketType, jeansType, dressType)
    private val typeById = types.associateBy { it.id }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.COTTON,
        laundryUntil: Long = 0,
    ) = ClothingPiece(
        id = id,
        name = "p$id",
        typeId = typeId,
        material = material,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
        laundryUntilEpochMs = laundryUntil,
    )

    private fun entry(
        id: Long,
        verdict: WarmthVerdict? = WarmthVerdict.PERFECT,
        tempC: Double? = 10.0,
        score: Int? = 5,
        photoPath: String? = null,
        date: LocalDate = today,
    ) = OutfitEntry(
        id = id,
        wornOnDate = date,
        photoPath = photoPath,
        notes = null,
        temperatureC = tempC,
        feelsLikeC = null,
        minTempC = null,
        maxTempC = null,
        weatherDescription = null,
        weatherIcon = null,
        isRainy = false,
        surveyFeelingScore = score,
        surveyWarmthVerdict = verdict,
        surveyedAtEpochMs = if (verdict != null) now else null,
        createdAtEpochMs = now,
    )

    private fun input(
        pieces: List<ClothingPiece>,
        entries: List<OutfitEntryWithPieces> = emptyList(),
        surveyedCount: Int = 1,
        modelState: ModelState? = null,
        tempC: Double? = 10.0,
        date: LocalDate = today,
    ) = RecommendationInput(
        date = date,
        today = today,
        tempC = tempC,
        isRainy = false,
        wardrobePieces = pieces,
        clothingTypes = types,
        entries = entries,
        surveyedCount = surveyedCount,
        modelState = modelState,
        nowEpochMs = now,
        random = Random(42),
    )

    @Test
    fun `constructs base plus outer layer combinations below 14C and omits outer layers at 18C`() {
        val tee = piece(1, teeType.id, Material.COTTON)
        val jacket = piece(2, jacketType.id, Material.WOOL)
        val jeans = piece(3, jeansType.id, Material.COTTON)
        val dress = piece(4, dressType.id, Material.COTTON)

        val coldContext =
            RecommendationScoringContext(
                input = input(pieces = listOf(tee, jacket, jeans, dress), tempC = 8.0),
                pool = listOf(tee, jacket, jeans, dress),
                typeById = typeById,
                mode = RecommendationMode.HEURISTIC,
                decodedModel = null,
            )
        val coldCandidates = CandidateGenerator.generate(coldContext)
        assertTrue(coldCandidates.any { it.pieces.map { p -> p.id }.toSet() == setOf(1L, 2L, 3L) })
        assertTrue(coldCandidates.any { it.pieces.map { p -> p.id }.toSet() == setOf(4L, 2L) })
        // Full-body never mixes with upper base or lower.
        assertFalse(coldCandidates.any { it.pieces.any { p -> p.id == 4L } && it.pieces.any { p -> p.id == 1L || p.id == 3L } })

        val warmContext =
            RecommendationScoringContext(
                input = input(pieces = listOf(tee, jacket, jeans, dress), tempC = 18.0),
                pool = listOf(tee, jacket, jeans, dress),
                typeById = typeById,
                mode = RecommendationMode.HEURISTIC,
                decodedModel = null,
            )
        val warmCandidates = CandidateGenerator.generate(warmContext)
        assertFalse(warmCandidates.any { candidate -> candidate.pieces.any { it.id == 2L } })
    }

    @Test
    fun `more than sixty logged entries do not crowd out constructed outfits`() {
        val tee = piece(1, teeType.id, Material.LINEN)
        val jacket = piece(2, jacketType.id, Material.WOOL)
        val jeans = piece(3, jeansType.id, Material.COTTON)
        // 65 logged heavy entries at 2C, whereas target day is 24C (so constructed [tee, jeans] should win).
        val manyLogged =
            (1L..65L).map { id ->
                OutfitEntryWithPieces(entry(id, tempC = 2.0, score = 5), listOf(jacket, jeans))
            }

        val ranked =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee, jacket, jeans), entries = manyLogged, surveyedCount = 5, tempC = 24.0),
            )

        assertTrue(ranked.isNotEmpty())
        assertEquals(null, ranked.first().loggedEntry)
        assertEquals(
            setOf(1L, 3L),
            ranked
                .first()
                .pieces
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `identical piece sets across journal entries deduplicate while distinct selfie-only entries stay distinct`() {
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val dup1 = OutfitEntryWithPieces(entry(1, tempC = 15.0, score = 5), listOf(tee, jeans))
        val dup2 = OutfitEntryWithPieces(entry(2, tempC = 15.0, score = 4), listOf(tee, jeans))
        val selfie1 = OutfitEntryWithPieces(entry(3, tempC = 15.0, score = 5, photoPath = "s1.jpg"), emptyList())
        val selfie2 = OutfitEntryWithPieces(entry(4, tempC = 15.0, score = 5, photoPath = "s2.jpg"), emptyList())

        val ranked =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee, jeans), entries = listOf(dup2, dup1, selfie1, selfie2), surveyedCount = 3, tempC = 15.0),
            )

        val pieceCandidates = ranked.filter { it.pieces.isNotEmpty() }
        assertEquals(1, pieceCandidates.size)
        // Higher-scored duplicate (id=1, score=5) survives deduplication.
        val survivingId =
            pieceCandidates
                .single()
                .loggedEntry
                ?.entry
                ?.id
        assertEquals(1L, survivingId)
        val selfieIds = ranked.filter { it.pieces.isEmpty() }.mapNotNull { it.loggedEntry?.entry?.id }.toSet()
        assertEquals(setOf(3L, 4L), selfieIds)
    }

    @Test
    fun `score-driven laundry replacement picks the replacement maximising predicted score`() {
        // Model: 5.0 - 1.0 * warmthUpper (so lighter warmthUpper scores higher).
        // Dirty tee has medium warmth; wool tee has higher warmth (closer to cotton tee),
        // linen tee has lower warmth (further from cotton tee, higher predicted model score!).
        val dirtyCottonTee = piece(1, teeType.id, Material.COTTON, laundryUntil = now + 10_000)
        val woolTee = piece(2, teeType.id, Material.WOOL)
        val linenTee = piece(3, teeType.id, Material.LINEN)
        val jeans = piece(4, jeansType.id, Material.COTTON)
        val logged = OutfitEntryWithPieces(entry(1, tempC = 15.0, score = 5), listOf(dirtyCottonTee, jeans))
        val modelState =
            ModelState(
                coefficientsJson = "[5.0, 0.0, -2.0, 0.0, 0.0, 0.0, 0.0, 0.0]",
                trainedAtEpochMs = now,
                sampleCount = 12,
                schemaVersion = ModelState.LEGACY_FEATURE_SCHEMA_VERSION,
            )

        val ranked =
            RecommendationEngine.recommendOutfits(
                input(
                    pieces = listOf(dirtyCottonTee, woolTee, linenTee, jeans),
                    entries = listOf(logged),
                    surveyedCount = 12,
                    modelState = modelState,
                    tempC = 15.0,
                ),
            )

        val loggedCandidate = ranked.first { it.loggedEntry?.entry?.id == 1L }
        assertTrue(loggedCandidate.pieces.any { it.id == 3L })
        assertFalse(loggedCandidate.pieces.any { it.id == 2L })
    }
}
