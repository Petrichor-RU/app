package nl.petrichor.app.platform

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Wall-clock times for the two daily journal reminders (plan section 2.2): 08:00 to log the
 * outfit, 20:00 for the evening survey.
 */
object ReminderTimes {
    const val MORNING_HOUR = 8
    const val EVENING_HOUR = 20

    /**
     * Next occurrence of [hour]:00 local time in [zone] strictly after [now]. The arithmetic is
     * done in local wall-clock time, so it is correct across midnight, month/year boundaries
     * and DST transitions.
     */
    fun nextOccurrence(
        now: Instant,
        hour: Int,
        zone: TimeZone,
    ): Instant {
        val local = now.toLocalDateTime(zone)
        var candidate = LocalDateTime(local.date, LocalTime(hour, 0))
        if (candidate <= local) {
            candidate = LocalDateTime(candidate.date.plus(1, DateTimeUnit.DAY), candidate.time)
        }
        return candidate.toInstant(zone)
    }
}
