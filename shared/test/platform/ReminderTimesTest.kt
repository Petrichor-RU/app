package nl.petrichor.app.platform

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ReminderTimesTest {
    // Named IANA zones need a timezone database. Wasm/JS only has one when the @js-joda/timezone
    // npm package is loaded, which the Kotlin Toolchain cannot inject (npm deps come from klibs),
    // so on wasmJs the named-zone tests skip themselves instead of failing.
    private val amsterdamOrNull: TimeZone? = runCatching { TimeZone.of("Europe/Amsterdam") }.getOrNull()
    private val utc = TimeZone.UTC

    @Test
    fun `next morning reminder before the hour is later today`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        // 2026-06-10 07:59 UTC = 09:59 in Amsterdam (CEST), so next 08:00 local is tomorrow.
        val now = Instant.parse("2026-06-10T05:59:00Z") // 07:59 local
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, amsterdam)
        assertEquals(LocalDateTime(2026, 6, 10, 8, 0), next.toLocalDateTime(amsterdam))
    }

    @Test
    fun `next morning reminder after the hour is tomorrow`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        val now = Instant.parse("2026-06-10T06:00:00Z") // exactly 08:00 local
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, amsterdam)
        // Strictly after now: today's 08:00 has passed, so the next one is tomorrow.
        assertEquals(LocalDateTime(2026, 6, 11, 8, 0), next.toLocalDateTime(amsterdam))
    }

    @Test
    fun `next reminder crosses midnight`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        val now = Instant.parse("2026-06-10T21:30:00Z") // 23:30 local
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, amsterdam)
        assertEquals(LocalDateTime(2026, 6, 11, 8, 0), next.toLocalDateTime(amsterdam))
    }

    @Test
    fun `next reminder crosses month boundary`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        val now = Instant.parse("2026-01-31T22:00:00Z") // 23:00 local CET
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, amsterdam)
        assertEquals(LocalDateTime(2026, 2, 1, 8, 0), next.toLocalDateTime(amsterdam))
    }

    @Test
    fun `next reminder keeps wall-clock hour across dst spring forward`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        // 2026-03-29 02:00 -> 03:00 in Amsterdam; 2026-03-28 12:00Z is 13:00 local.
        val now = Instant.parse("2026-03-28T12:00:00Z")
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, amsterdam)
        // Wall clock must still be 08:00 local on 2026-03-29 (CEST, UTC+2).
        assertEquals(LocalDateTime(2026, 3, 29, 8, 0), next.toLocalDateTime(amsterdam))
        assertEquals(Instant.parse("2026-03-29T06:00:00Z"), next)
    }

    @Test
    fun `evening reminder is at 20 local`() {
        val amsterdam = amsterdamOrNull ?: return // no tz database on this platform (wasmJs)
        val now = Instant.parse("2026-06-10T05:00:00Z") // 07:00 local
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.EVENING_HOUR, amsterdam)
        assertEquals(LocalDateTime(2026, 6, 10, 20, 0), next.toLocalDateTime(amsterdam))
    }

    @Test
    fun `utc zone works too`() {
        val now = Instant.parse("2026-06-10T07:59:00Z")
        val next = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, utc)
        assertEquals(Instant.parse("2026-06-10T08:00:00Z"), next)
    }
}
