package nl.petrichor.app.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val monthShortNames: Map<Month, String> =
    mapOf(
        Month.JANUARY to "Jan",
        Month.FEBRUARY to "Feb",
        Month.MARCH to "Mar",
        Month.APRIL to "Apr",
        Month.MAY to "May",
        Month.JUNE to "Jun",
        Month.JULY to "Jul",
        Month.AUGUST to "Aug",
        Month.SEPTEMBER to "Sep",
        Month.OCTOBER to "Oct",
        Month.NOVEMBER to "Nov",
        Month.DECEMBER to "Dec",
    )

private fun LocalDate.weekdayName(): String =
    dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }

/** "Thursday 12 Nov" */
fun LocalDate.longLabel(): String = "${weekdayName()} $day ${monthShortNames[month]}"

/** "12 Nov" */
fun LocalDate.shortLabel(): String = "$day ${monthShortNames[month]}"

fun epochMsToLocalDate(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * True while a laundry mark is still active: [laundryUntilEpochMs] lies in the future
 * (0 means never marked). The "In laundry until …" rows use this so they disappear once the
 * until-time has passed, matching the recommendation logic that treats `<= now` as clean.
 */
fun isLaundryActive(
    laundryUntilEpochMs: Long,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): Boolean = laundryUntilEpochMs > nowEpochMs
