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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minusMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Month calendar used on the recommendation screen.
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
    // Stored as an ISO string ("2026-11") so it survives switching tabs
    var displayedMonthIso by rememberSaveable { mutableStateOf(selectedDate.yearMonth.toString()) }
    val displayedMonth = YearMonth.parse(displayedMonthIso)

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { displayedMonthIso = displayedMonth.minusMonth().toString() }) {
                    MaterialSymbol(iconName = MaterialSymbols.CHEVRON_LEFT, contentDescription = "Previous month")
                }
                Text(
                    "${displayedMonth.month.displayName()} ${displayedMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { displayedMonthIso = displayedMonth.plusMonth().toString() }) {
                    MaterialSymbol(iconName = MaterialSymbols.CHEVRON_RIGHT, contentDescription = "Next month")
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
            Spacer(Modifier.height(4.dp))

            monthCells(displayedMonth).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                CalendarDay(
                                    date = date,
                                    isSelected = date == selectedDate,
                                    isToday = date == today,
                                    hasForecast = date in forecastDates,
                                    onClick = { onDateSelected(date) },
                                )
                            }
                        }
                    }
                }
            }

            if (selectedDate != today || displayedMonth != today.yearMonth) {
                TextButton(
                    onClick = {
                        displayedMonthIso = today.yearMonth.toString()
                        onDateSelected(today)
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Today")
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
            .padding(2.dp)
            .size(40.dp)
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
            style = MaterialTheme.typography.bodyMedium,
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
