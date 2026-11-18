package nl.petrichor.app.data.outfit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutfitLogRepositoryTest {
    @Test
    fun `logging a new outfit stamps createdAt and links the pieces in one insert`() =
        runTest {
            val dao = FakeOutfitDao()
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = {})
            val entry = entry(createdAtEpochMs = 0L)
            val pieces = listOf(piece(11L), piece(12L))

            val id = repository.logOutfit(entry, pieces)

            assertTrue(id > 0L)
            val inserted = dao.insertedEntries.single()
            assertTrue(inserted.createdAtEpochMs > 0L)
            assertEquals(
                listOf(
                    OutfitEntryPiece(entryId = id, pieceId = 11L, orderIndex = 0),
                    OutfitEntryPiece(entryId = id, pieceId = 12L, orderIndex = 1),
                ),
                dao.insertedLinks.single(),
            )
        }

    @Test
    fun `logging an existing outfit preserves its creation timestamp`() =
        runTest {
            val dao = FakeOutfitDao()
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = {})

            repository.logOutfit(entry(createdAtEpochMs = 42L), emptyList())

            assertEquals(42L, dao.insertedEntries.single().createdAtEpochMs)
        }

    @Test
    fun `saving a survey updates the entry and triggers a retrain`() =
        runTest {
            val dao = FakeOutfitDao()
            dao.entries.value = listOf(entryWithSurvey(id = 5L, score = null, minTempC = 10.0, maxTempC = 20.0, temperatureC = 15.0))
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = {}, appScope = this)

            repository.saveSurvey(5L, 4, WarmthVerdict.PERFECT)
            // The retrain runs in the test scope; drain it until the model row appears.
            while (dao.modelStates.isEmpty()) {
                yield()
            }

            val survey = dao.surveys.single()
            assertEquals(5L, survey.first)
            assertEquals(4, survey.second)
            assertEquals(WarmthVerdict.PERFECT, survey.third)
            // retrain ran in the background scope and persisted a model state row
            val model = dao.modelStates.lastOrNull()
            assertNotNull(model)
            assertEquals(1, model.sampleCount)
        }

    @Test
    fun `marking laundry computes until as now plus days`() =
        runTest {
            val dao = FakeOutfitDao()
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = {})

            repository.markOutfitLaundry(3L, 2)

            val (id, until) = dao.laundryMarks.single()
            assertEquals(3L, id)
            val delta = until - OutfitLogRepository.laundryUntilEpochMs(0)
            assertEquals(2L * 24 * 60 * 60 * 1000, delta)
            assertEquals(1, repository.laundryVersion.value)
        }

    @Test
    fun `deleting an entry removes it and its selfie file`() =
        runTest {
            val dao = FakeOutfitDao()
            val withPhoto =
                OutfitEntryWithPieces(
                    entry(createdAtEpochMs = 1L).copy(id = 8L, photoPath = "selfie.jpg"),
                    emptyList(),
                )
            dao.entries.value = listOf(withPhoto)
            val deletedImages = mutableListOf<String>()
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = { deletedImages += it })

            repository.deleteEntry(withPhoto.entry)

            assertEquals(withPhoto.entry, dao.deletedEntries.single())
            assertEquals(listOf("selfie.jpg"), deletedImages)
            assertTrue(dao.entries.value.isEmpty())
        }

    @Test
    fun `most recent un-surveyed entry of a day is returned first`() =
        runTest {
            val dao = FakeOutfitDao()
            val today = LocalDate(2026, 11, 12)
            dao.entries.value =
                listOf(
                    entryWithSurvey(id = 1L, score = 5, createdAt = 100L, date = today),
                    entryWithSurvey(id = 2L, score = null, createdAt = 200L, date = today),
                    entryWithSurvey(id = 3L, score = null, createdAt = 300L, date = today),
                    entryWithSurvey(id = 4L, score = null, createdAt = 400L, date = LocalDate(2026, 11, 11)),
                )
            val repository = OutfitLogRepository(dao, FakeClothingDao(), Dispatchers.Unconfined, imageDeleter = {})

            val target = repository.mostRecentUnsurveyedForDate(today)

            assertEquals(3L, target?.entry?.id)
        }

    @Test
    fun `weather snapshot for today stores current temps and forecast min max`() {
        val weather = weather()
        val today = LocalDate(2026, 11, 12)

        val snapshot = buildEntryWeatherSnapshot(weather, today, today)

        assertEquals(12.0, snapshot.temperatureC)
        assertEquals(10.0, snapshot.feelsLikeC)
        assertEquals(8.0, snapshot.minTempC) // min of forecast day (8) and current (12)
        assertEquals(18.0, snapshot.maxTempC) // max of forecast day (18) and current (12)
        assertEquals("Light rain", snapshot.weatherDescription)
        assertEquals("10d", snapshot.weatherIcon)
        assertTrue(snapshot.isRainy)
    }

    @Test
    fun `weather snapshot for a future forecast day stores that day's min max and icon`() {
        val weather = weather()
        val today = LocalDate(2026, 11, 12)
        val future = LocalDate(2026, 11, 14)

        val snapshot = buildEntryWeatherSnapshot(weather, future, today)

        assertNull(snapshot.temperatureC)
        assertNull(snapshot.feelsLikeC)
        assertEquals(3.0, snapshot.minTempC)
        assertEquals(9.0, snapshot.maxTempC)
        assertNull(snapshot.weatherDescription)
        assertEquals("01d", snapshot.weatherIcon)
        assertFalse(snapshot.isRainy)
    }

    @Test
    fun `weather snapshot for an older back-entry is all null and excluded from training`() {
        val weather = weather()
        val today = LocalDate(2026, 11, 12)
        val past = LocalDate(2026, 10, 1)

        val snapshot = buildEntryWeatherSnapshot(weather, past, today)

        assertNull(snapshot.temperatureC)
        assertNull(snapshot.minTempC)
        assertNull(snapshot.maxTempC)
        assertNull(snapshot.weatherDescription)
        assertNull(snapshot.weatherIcon)
        assertFalse(snapshot.isRainy)
    }

    private fun entry(createdAtEpochMs: Long) =
        OutfitEntry(
            id = 0,
            wornOnDate = LocalDate(2026, 11, 12),
            photoPath = null,
            notes = null,
            temperatureC = null,
            feelsLikeC = null,
            minTempC = null,
            maxTempC = null,
            weatherDescription = null,
            weatherIcon = null,
            isRainy = false,
            surveyFeelingScore = null,
            surveyWarmthVerdict = null,
            surveyedAtEpochMs = null,
            createdAtEpochMs = createdAtEpochMs,
        )

    @Suppress("LongParameterList") // test fixture with named defaults
    private fun entryWithSurvey(
        id: Long,
        score: Int?,
        createdAt: Long = 0L,
        date: LocalDate = LocalDate(2026, 11, 12),
        minTempC: Double? = null,
        maxTempC: Double? = null,
        temperatureC: Double? = null,
    ) = OutfitEntryWithPieces(
        entry(createdAtEpochMs = createdAt).copy(
            id = id,
            wornOnDate = date,
            surveyFeelingScore = score,
            surveyWarmthVerdict = score?.let { WarmthVerdict.PERFECT },
            surveyedAtEpochMs = score?.let { createdAt },
            minTempC = minTempC,
            maxTempC = maxTempC,
            temperatureC = temperatureC,
        ),
        emptyList(),
    )

    private fun piece(id: Long) =
        ClothingPiece(
            id = id,
            name = "Piece $id",
            typeId = 1L,
            material = nl.petrichor.app.data.wardrobe.Material.UNKNOWN,
            notes = null,
            imagePath = "",
            thumbnailPath = "",
        )

    private fun weather() =
        WeatherSnapshot(
            place = Place("Amsterdam", Coordinates(52.3676, 4.9041)),
            temperature = 12,
            feelsLike = 10,
            description = "Light rain",
            icon = "10d",
            humidity = 80,
            windSpeed = 15,
            forecast =
                listOf(
                    ForecastDay("2026-11-12", min = 8, max = 18, icon = "10d"),
                    ForecastDay("2026-11-13", min = 6, max = 14, icon = "04d"),
                    ForecastDay("2026-11-14", min = 3, max = 9, icon = "01d"),
                ),
        )
}
