package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Compact calendar used on the recommendation screen.
 * Shows a single week by default and can be expanded into a full month grid.
 * The selected day is filled with the primary color, today is outlined, and days with a
 * weather forecast get a small dot underneath.
 * The calendar only reports clicks; the selected date is owned by [RecommendationScreen].
 */
@Composable
fun RecommendationCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    forecastDates: Set<LocalDate> = emptySet(),
) {
    var showFullMonth by rememberSaveable { mutableStateOf(false) }
    // Stored as ISO strings so they survive switching tabs
    var displayedWeekStartIso by rememberSaveable { mutableStateOf(selectedDate.startOfWeek().toString()) }
    var displayedMonthIso by rememberSaveable { mutableStateOf(selectedDate.yearMonth.toString()) }
    val displayedWeekStart = LocalDate.parse(displayedWeekStartIso)
    val displayedMonth = YearMonth.parse(displayedMonthIso)
    val headerMonth = if (showFullMonth) displayedMonth else displayedWeekStart.plus(3, DateTimeUnit.DAY).yearMonth
    val showToday = selectedDate != today || headerMonth != today.yearMonth || displayedWeekStart != today.startOfWeek()

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (showFullMonth) {
                            displayedMonthIso = displayedMonth.minusMonth().toString()
                        } else {
                            displayedWeekStartIso = displayedWeekStart.minus(7, DateTimeUnit.DAY).toString()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_LEFT,
                        contentDescription = if (showFullMonth) "Previous month" else "Previous week",
                    )
                }
                Text(
                    "${headerMonth.month.displayName()} ${headerMonth.year}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.width(64.dp).height(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showToday) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    displayedWeekStartIso = today.startOfWeek().toString()
                                    displayedMonthIso = today.yearMonth.toString()
                                    onDateSelected(today)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Today",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        if (showFullMonth) {
                            displayedMonthIso = displayedMonth.plusMonth().toString()
                        } else {
                            displayedWeekStartIso = displayedWeekStart.plus(7, DateTimeUnit.DAY).toString()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_RIGHT,
                        contentDescription = if (showFullMonth) "Next month" else "Next week",
                    )
                }
                IconButton(
                    onClick = {
                        if (showFullMonth) {
                            displayedWeekStartIso = selectedDate.startOfWeek().toString()
                        } else {
                            displayedMonthIso = selectedDate.yearMonth.toString()
                        }
                        showFullMonth = !showFullMonth
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    MaterialSymbol(
                        iconName = if (showFullMonth) MaterialSymbols.CALENDAR_VIEW_WEEK else MaterialSymbols.CALENDAR_MONTH,
                        contentDescription = if (showFullMonth) "Show week" else "Show full month",
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            val rows = if (showFullMonth) {
                monthCells(displayedMonth).chunked(7)
            } else {
                listOf(weekDates(displayedWeekStart))
            }
            rows.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                CalendarDay(
                                    date = date,
                                    isSelected = date == selectedDate,
                                    isToday = date == today,
                                    hasForecast = date in forecastDates,
                                    onClick = {
                                        displayedWeekStartIso = date.startOfWeek().toString()
                                        displayedMonthIso = date.yearMonth.toString()
                                        onDateSelected(date)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasForecast: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(1.dp)
            .size(32.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(colors.primary) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(1.dp, colors.primary), CircleShape) else Modifier)
            .semantics { selected = isSelected }
            .clickable(role = Role.Button, onClickLabel = "Select ${date.formatted()}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.day.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> colors.onPrimary
                isToday -> colors.primary
                else -> colors.onSurface
            },
        )
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !hasForecast -> Color.Transparent
                        isSelected -> colors.onPrimary
                        else -> colors.primary
                    }
                )
        )
    }
}

private fun LocalDate.startOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

private fun weekDates(weekStart: LocalDate): List<LocalDate> =
    (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }

/** Days of the month padded with leading/trailing nulls so weeks start on Monday. */
private fun monthCells(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.firstDay.dayOfWeek.isoDayNumber - 1
    val days = (1..month.numberOfDays).map { month.onDay(it) }
    val cells = List<LocalDate?>(leadingBlanks) { null } + days
    val trailingBlanks = (7 - cells.size % 7) % 7
    return cells + List(trailingBlanks) { null }
}

internal fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** e.g. "Tue 3 November" */
internal fun LocalDate.formatted(): String =
    "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} $day ${month.displayName()}"
