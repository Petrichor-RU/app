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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationEngineTest {
    private val today = LocalDate(2026, 6, 10)
    private val now = 1_800_000_000_000L // fixed epoch ms for laundry checks

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
        archived: Boolean = false,
    ) = ClothingPiece(
        id = id,
        name = "p$id",
        typeId = typeId,
        material = material,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
        archived = archived,
        laundryUntilEpochMs = laundryUntil,
    )

    private fun entry(
        id: Long,
        verdict: WarmthVerdict? = null,
        tempC: Double? = 15.0,
        score: Int? = null,
        laundryUntil: Long = 0,
        photoPath: String? = null,
    ) = OutfitEntry(
        id = id,
        wornOnDate = today,
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
        laundryUntilEpochMs = laundryUntil,
        createdAtEpochMs = now,
    )

    private fun withPieces(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
    ) = OutfitEntryWithPieces(entry, pieces)

    private fun input(
        pieces: List<ClothingPiece> = emptyList(),
        entries: List<OutfitEntryWithPieces> = emptyList(),
        surveyedCount: Int = 0,
        modelState: ModelState? = null,
        tempC: Double? = 15.0,
        isRainy: Boolean = false,
    ) = RecommendationInput(
        date = today,
        today = today,
        tempC = tempC,
        isRainy = isRainy,
        wardrobePieces = pieces,
        clothingTypes = types,
        entries = entries,
        surveyedCount = surveyedCount,
        modelState = modelState,
        nowEpochMs = now,
        random = Random(42),
    )

    // --- mode derivation ---

    @Test
    fun `mode is fallback without surveys heuristic with 1-9 model with 10+ and a trained model`() {
        assertEquals(RecommendationMode.FALLBACK, RecommendationEngine.activeMode(0, false))
        assertEquals(RecommendationMode.HEURISTIC, RecommendationEngine.activeMode(1, false))
        assertEquals(RecommendationMode.HEURISTIC, RecommendationEngine.activeMode(9, true))
        assertEquals(RecommendationMode.HEURISTIC, RecommendationEngine.activeMode(10, false))
        assertEquals(RecommendationMode.MODEL, RecommendationEngine.activeMode(10, true))
    }

    // --- heuristic ideal warmth ---

    @Test
    fun `ideal warmth interpolates between perfect verdicts`() {
        // wool jacket warmth = 1.0; two jackets at 10C (warmth 2), four at 20C (warmth 4) -> 3 at 15C
        val e1 =
            withPieces(
                entry(1, WarmthVerdict.PERFECT, tempC = 10.0, score = 5),
                listOf(piece(1, jacketType.id, Material.WOOL), piece(2, jacketType.id, Material.WOOL)),
            )
        val e2 =
            withPieces(
                entry(2, WarmthVerdict.PERFECT, tempC = 20.0, score = 5),
                listOf(
                    piece(3, jacketType.id, Material.WOOL),
                    piece(4, jacketType.id, Material.WOOL),
                    piece(5, jacketType.id, Material.WOOL),
                    piece(6, jacketType.id, Material.WOOL),
                ),
            )
        val ideal = Scoring.idealWarmth(input(entries = listOf(e1, e2), surveyedCount = 2, tempC = 15.0), typeById)
        assertEquals(3.0, ideal, 1e-9)
    }

    @Test
    fun `ideal warmth uses midpoint of nearest too cold and too warm without perfects`() {
        val cold =
            withPieces(
                entry(1, WarmthVerdict.TOO_COLD, tempC = 12.0, score = 2),
                listOf(piece(1, jacketType.id, Material.WOOL)),
            ) // warmth 1
        val warm =
            withPieces(
                entry(2, WarmthVerdict.TOO_WARM, tempC = 18.0, score = 2),
                listOf(
                    piece(2, jacketType.id, Material.WOOL),
                    piece(3, jacketType.id, Material.WOOL),
                    piece(4, jacketType.id, Material.WOOL),
                    piece(5, jacketType.id, Material.WOOL),
                    piece(6, jacketType.id, Material.WOOL),
                ),
            ) // warmth 5
        val ideal = Scoring.idealWarmth(input(entries = listOf(cold, warm), surveyedCount = 2, tempC = 15.0), typeById)
        assertEquals(3.0, ideal, 1e-9)
    }

    @Test
    fun `ideal warmth adds rainy bonus and defaults to 3 without verdicts`() {
        val perfect =
            withPieces(
                entry(1, WarmthVerdict.PERFECT, tempC = 15.0, score = 5),
                listOf(piece(1, jacketType.id, Material.WOOL), piece(2, jacketType.id, Material.WOOL)),
            )
        assertEquals(
            2.3,
            Scoring.idealWarmth(input(entries = listOf(perfect), surveyedCount = 1, tempC = 15.0, isRainy = true), typeById),
            1e-9,
        )
        assertEquals(3.0, Scoring.idealWarmth(input(surveyedCount = 0, tempC = 15.0), typeById), 1e-9)
    }

    @Test
    fun `heuristic score is 5 at the ideal warmth and floors at 1`() {
        // ideal at 15C with no verdicts is 3.0; build candidates with warmth 3.0 and 7.0
        val exact =
            listOf(piece(1, jacketType.id, Material.WOOL), piece(2, jacketType.id, Material.WOOL), piece(3, jacketType.id, Material.WOOL))
        val far = (1..7).map { piece(it.toLong(), jacketType.id, Material.WOOL) }
        val candidateExact = OutfitCandidate(null, exact, null, null, RecommendationSource.HEURISTIC)
        val candidateFar = OutfitCandidate(null, far, null, null, RecommendationSource.HEURISTIC)
        assertEquals(5.0, Scoring.heuristicScore(input(surveyedCount = 1, tempC = 15.0), candidateExact, typeById), 1e-9)
        assertEquals(1.0, Scoring.heuristicScore(input(surveyedCount = 1, tempC = 15.0), candidateFar, typeById), 1e-9)
    }

    // --- candidate generation ---

    @Test
    fun `logged entry becomes a candidate with its pieces`() {
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val logged = withPieces(entry(1), listOf(tee, jeans))
        val result = RecommendationEngine.recommendOutfits(input(pieces = listOf(tee, jeans), entries = listOf(logged), tempC = 15.0))
        assertTrue(result.any { it.loggedEntry?.entry?.id == 1L && it.pieces.map { p -> p.id } == listOf(1L, 2L) })
    }

    @Test
    fun `entry in laundry is skipped entirely including selfie-only entries`() {
        val tee = piece(1, teeType.id)
        val laundryEntry = withPieces(entry(1, laundryUntil = now + 10_000), listOf(tee))
        val selfieLaundry = withPieces(entry(2, laundryUntil = now + 10_000, photoPath = "/tmp/selfie.jpg"), emptyList())
        val cleanSelfie = withPieces(entry(3, photoPath = "/tmp/ok.jpg"), emptyList())
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee), entries = listOf(laundryEntry, selfieLaundry, cleanSelfie), tempC = 15.0),
            )
        assertTrue(result.none { it.loggedEntry?.entry?.id == 1L })
        assertTrue(result.none { it.loggedEntry?.entry?.id == 2L })
        assertTrue(result.any { it.loggedEntry?.entry?.id == 3L && it.selfiePath == "/tmp/ok.jpg" })
    }

    @Test
    fun `laundry piece is replaced by the best same-zone alternative`() {
        val dirtyTee = piece(1, teeType.id, laundryUntil = now + 10_000)
        val backupTee = piece(2, teeType.id, material = Material.WOOL) // warmer backup in same zone
        val jeans = piece(3, jeansType.id)
        val logged = withPieces(entry(1), listOf(dirtyTee, jeans))
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(dirtyTee, backupTee, jeans), entries = listOf(logged), tempC = 15.0),
            )
        val candidate = result.first { it.loggedEntry?.entry?.id == 1L }
        assertEquals(listOf(2L, 3L), candidate.pieces.map { it.id }.sorted())
    }

    @Test
    fun `candidate is skipped when an upper piece is in laundry without alternative`() {
        val dirtyTee = piece(1, teeType.id, laundryUntil = now + 10_000)
        val jeans = piece(3, jeansType.id)
        val logged = withPieces(entry(1), listOf(dirtyTee, jeans))
        val result = RecommendationEngine.recommendOutfits(input(pieces = listOf(dirtyTee, jeans), entries = listOf(logged), tempC = 15.0))
        assertTrue(result.none { it.loggedEntry?.entry?.id == 1L })
    }

    @Test
    fun `candidate with archived piece is skipped`() {
        val archivedTee = piece(1, teeType.id, archived = true)
        val jeans = piece(3, jeansType.id)
        val logged = withPieces(entry(1), listOf(archivedTee, jeans))
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(archivedTee, jeans), entries = listOf(logged), tempC = 15.0),
            )
        assertTrue(result.none { it.loggedEntry?.entry?.id == 1L })
    }

    @Test
    fun `fallback mode returns band suggestion and a random piece per zone when wardrobe has pieces`() {
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val result = RecommendationEngine.recommendOutfits(input(pieces = listOf(tee, jeans), surveyedCount = 0, tempC = 20.0))
        val fallback = result.first { it.source == RecommendationSource.FALLBACK }
        assertEquals("T-shirt or shirt", fallback.generalSuggestion)
        assertEquals(2, fallback.pieces.size) // one random piece per covered zone
    }

    @Test
    fun `fallback band boundaries`() {
        assertEquals("Winter jacket, sweater and long pants", RecommendationEngine.bandSuggestion(4.9))
        assertEquals("Sweater and long pants", RecommendationEngine.bandSuggestion(5.0))
        assertEquals("Sweater and long pants", RecommendationEngine.bandSuggestion(11.9))
        assertEquals("Long-sleeved shirt or light sweater", RecommendationEngine.bandSuggestion(12.0))
        assertEquals("T-shirt or shirt", RecommendationEngine.bandSuggestion(18.0))
        assertEquals("T-shirt, shorts if you like", RecommendationEngine.bandSuggestion(24.0))
        assertEquals("No forecast available — check the Weather tab", RecommendationEngine.bandSuggestion(null))
    }

    @Test
    fun `at most three candidates are returned`() {
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val entries = (1..6L).map { withPieces(entry(it), listOf(tee, jeans)) }
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee, jeans), entries = entries, surveyedCount = 3, tempC = 15.0),
            )
        assertTrue(result.size <= RecommendationEngine.MAX_CANDIDATES)
    }

    @Test
    fun `no temperature means fallback even with surveys`() {
        val tee = piece(1, teeType.id)
        val logged = withPieces(entry(1, WarmthVerdict.PERFECT, tempC = 15.0, score = 5), listOf(tee))
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee), entries = listOf(logged), surveyedCount = 12, tempC = null),
            )
        assertTrue(result.all { it.source == RecommendationSource.FALLBACK })
    }

    // --- temperature-distance compromise ---

    @Test
    fun `logged outfit worn far colder is demoted behind the textual band at a much warmer target`() {
        val jacket1 = piece(1, jacketType.id, Material.WOOL)
        val jacket2 = piece(2, jacketType.id, Material.WOOL)
        val tee = piece(3, teeType.id, Material.COTTON)
        val jeans = piece(4, jeansType.id, Material.COTTON)
        // Surveyed PERFECT at 10C with a heavy outfit; target day is 22C (e.g. Sevilla).
        val logged = withPieces(entry(1, WarmthVerdict.PERFECT, tempC = 10.0, score = 5), listOf(jacket1, jacket2, jeans))
        val result =
            RecommendationEngine.recommendOutfits(
                input(
                    pieces = listOf(jacket1, jacket2, tee, jeans),
                    entries = listOf(logged),
                    surveyedCount = 1,
                    tempC = 22.0,
                ),
            )
        // The top candidate is constructed and leads with the textual band, not the logged outfit.
        assertEquals(null, result.first().loggedEntry)
        assertEquals("T-shirt or shirt", result.first().generalSuggestion)
        // The logged 10C outfit is still offered, but ranked below.
        assertTrue(result.indexOfFirst { it.loggedEntry?.entry?.id == 1L } > 0)
    }

    @Test
    fun `within the surveyed temperature margin the logged outfit still wins and no band text is added`() {
        val jacket1 = piece(1, jacketType.id, Material.WOOL)
        val jacket2 = piece(2, jacketType.id, Material.WOOL)
        val tee = piece(3, teeType.id, Material.COTTON)
        val jeans = piece(4, jeansType.id, Material.COTTON)
        val logged = withPieces(entry(1, WarmthVerdict.PERFECT, tempC = 10.0, score = 5), listOf(jacket1, jacket2, jeans))
        val result =
            RecommendationEngine.recommendOutfits(
                input(
                    pieces = listOf(jacket1, jacket2, tee, jeans),
                    entries = listOf(logged),
                    surveyedCount = 1,
                    tempC = 11.0,
                ),
            )
        assertEquals(
            1L,
            result
                .first()
                .loggedEntry
                ?.entry
                ?.id,
        )
        assertTrue(result.filter { it.loggedEntry == null }.all { it.generalSuggestion == null })
    }

    @Test
    fun `ideal warmth interpolates inside the surveyed range and follows the prior outside it`() {
        val perfectAt10 =
            withPieces(
                entry(1, WarmthVerdict.PERFECT, tempC = 10.0, score = 5),
                listOf(piece(1, jacketType.id, Material.WOOL), piece(2, jacketType.id, Material.WOOL)),
            ) // warmth 2
        val perfectAt12 =
            withPieces(
                entry(2, WarmthVerdict.PERFECT, tempC = 12.0, score = 5),
                listOf(
                    piece(3, jacketType.id, Material.WOOL),
                    piece(4, jacketType.id, Material.WOOL),
                    piece(5, jacketType.id, Material.WOOL),
                ),
            ) // warmth 3
        val entries = listOf(perfectAt10, perfectAt12)
        // Inside the range: linear interpolation.
        assertEquals(2.5, Scoring.idealWarmth(input(entries = entries, surveyedCount = 2, tempC = 11.0), typeById), 1e-9)
        // Far outside the range (22 > 12 + 5): the prior curve, not the clamped endpoint.
        assertEquals(
            priorIdealWarmth(22.0),
            Scoring.idealWarmth(input(entries = entries, surveyedCount = 2, tempC = 22.0), typeById),
            1e-9,
        )
        assertTrue(priorIdealWarmth(22.0) < 3.0)
    }

    @Test
    fun `ideal warmth clamps at the perfect range ends when inside the surveyed range`() {
        // PERFECT verdicts at 10C (warmth 2) and 12C (warmth 3); a TOO_WARM at 5C widens the
        // surveyed range so 8C and 16C are inside it but outside the PERFECT range.
        val perfectAt10 =
            withPieces(
                entry(1, WarmthVerdict.PERFECT, tempC = 10.0, score = 5),
                listOf(piece(1, jacketType.id, Material.WOOL), piece(2, jacketType.id, Material.WOOL)),
            )
        val perfectAt12 =
            withPieces(
                entry(2, WarmthVerdict.PERFECT, tempC = 12.0, score = 5),
                listOf(
                    piece(3, jacketType.id, Material.WOOL),
                    piece(4, jacketType.id, Material.WOOL),
                    piece(5, jacketType.id, Material.WOOL),
                ),
            )
        val tooWarmAt5 =
            withPieces(
                entry(3, WarmthVerdict.TOO_WARM, tempC = 5.0, score = 2),
                listOf(piece(6, jacketType.id, Material.WOOL)),
            )
        val entries = listOf(perfectAt10, perfectAt12, tooWarmAt5)
        // Below the lowest PERFECT point: clamps to its warmth instead of throwing.
        assertEquals(2.0, Scoring.idealWarmth(input(entries = entries, surveyedCount = 3, tempC = 8.0), typeById), 1e-9)
        // Above the highest PERFECT point (still inside the surveyed range): clamps to its warmth.
        assertEquals(3.0, Scoring.idealWarmth(input(entries = entries, surveyedCount = 3, tempC = 16.0), typeById), 1e-9)
    }

    @Test
    fun `temperature penalty is zero within the margin and grows linearly beyond it`() {
        val logged = withPieces(entry(1, tempC = 10.0), listOf(piece(1, teeType.id)))
        val candidate = OutfitCandidate(logged, logged.pieces, null, null, RecommendationSource.HEURISTIC)
        assertEquals(0.0, temperaturePenalty(candidate, 14.0), 1e-9)
        assertEquals(0.25 * 5.0, temperaturePenalty(candidate, 20.0), 1e-9)
        assertEquals(0.25 * 7.0, temperaturePenalty(candidate, 22.0), 1e-9)
        // Constructed candidates and entries without weather data are never penalized.
        val constructed = OutfitCandidate(null, emptyList(), null, null, RecommendationSource.HEURISTIC)
        assertEquals(0.0, temperaturePenalty(constructed, 22.0), 1e-9)
        val noWeather =
            OutfitCandidate(withPieces(entry(2, tempC = null), emptyList()), emptyList(), null, null, RecommendationSource.HEURISTIC)
        assertEquals(0.0, temperaturePenalty(noWeather, 22.0), 1e-9)
    }

    // --- display groups ---

    @Test
    fun `groups follow fixed order and full body suppresses upper and lower`() {
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val dress = piece(3, dressType.id)
        val split = RecommendationEngine.groupsFor(listOf(tee, jeans), typeById)
        assertEquals(listOf(CoverageZone.UPPER, CoverageZone.LOWER), split.map { it.zone })
        assertEquals(listOf("Upper body", "Lower body"), split.map { it.label })
        val withDress = RecommendationEngine.groupsFor(listOf(tee, jeans, dress), typeById)
        assertEquals(listOf(CoverageZone.FULL_BODY), withDress.map { it.zone })
    }

    @Test
    fun `model mode predicts with the stored coefficients`() {
        // y = 4 + 0.0*x... -> coefficients [4, 0, ...]; candidate predicted 4 clamped 1..5
        val modelState = ModelState(coefficientsJson = "[4.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]", trainedAtEpochMs = now, sampleCount = 12)
        val tee = piece(1, teeType.id)
        val jeans = piece(2, jeansType.id)
        val logged = withPieces(entry(1), listOf(tee, jeans))
        val result =
            RecommendationEngine.recommendOutfits(
                input(pieces = listOf(tee, jeans), entries = listOf(logged), surveyedCount = 12, modelState = modelState, tempC = 15.0),
            )
        assertNotNull(result.firstOrNull { it.source == RecommendationSource.MODEL })
        assertNull(result.firstOrNull { it.source == RecommendationSource.FALLBACK })
    }
}
