package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.plusDays
import com.kizitonwose.calendar.core.plusMonths
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.yearMonth

private const val ADJACENT_MONTHS = 500

internal val consumeHorizontalScroll =
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = Offset(available.x, 0f)

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity = Velocity(available.x, 0f)
    }

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // calendar view is one cohesive component
fun RecommendationCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    forecastDates: Set<LocalDate> = emptySet(),
    headerVisible: Boolean = true,
) {
    val startMonth = remember(today) { today.yearMonth.minusMonths(ADJACENT_MONTHS) }
    val endMonth = remember(today) { today.yearMonth.plusMonths(ADJACENT_MONTHS) }
    val daysOfWeek = remember { daysOfWeek() }

    var showFullMonth by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val monthState =
        rememberCalendarState(
            startMonth = startMonth,
            endMonth = endMonth,
            firstVisibleMonth = selectedDate.yearMonth,
            firstDayOfWeek = daysOfWeek.first(),
        )
    val weekState =
        rememberWeekCalendarState(
            startDate = startMonth.firstDay,
            endDate = endMonth.lastDay,
            firstVisibleWeekDate = selectedDate,
            firstDayOfWeek = daysOfWeek.first(),
        )

    val displayedMonth =
        if (showFullMonth) {
            monthState.firstVisibleMonth.yearMonth
        } else {
            weekState.firstVisibleWeek.days
                .last()
                .date.yearMonth
        }

    val isTodayVisible =
        if (showFullMonth) {
            displayedMonth == today.yearMonth
        } else {
            weekState.firstVisibleWeek.days.any { it.date == today }
        }
    val showToday = selectedDate != today || !isTodayVisible

    fun stepPage(delta: Int) {
        coroutineScope.launch {
            if (showFullMonth) {
                monthState.animateScrollToMonth(displayedMonth.plusMonths(delta))
            } else {
                val nextWeekDate =
                    weekState.firstVisibleWeek.days
                        .first()
                        .date
                        .plusDays(delta * 7)
                weekState.animateScrollToWeek(nextWeekDate)
            }
        }
    }

    fun toggleViewMode() {
        val targetWeekDate =
            if (selectedDate.yearMonth == displayedMonth) {
                selectedDate
            } else {
                displayedMonth.firstDay
            }
        showFullMonth = !showFullMonth
        coroutineScope.launch {
            if (showFullMonth) {
                monthState.scrollToMonth(displayedMonth)
            } else {
                weekState.scrollToWeek(targetWeekDate)
            }
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .animateContentSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (headerVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("calendar_top_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { stepPage(-1) }, modifier = Modifier.size(36.dp)) {
                        MaterialSymbol(
                            iconName = MaterialSymbols.CHEVRON_LEFT,
                            contentDescription = if (showFullMonth) "Previous month" else "Previous week",
                        )
                    }
                    Text(
                        text = "${displayedMonth.month.displayName()} ${displayedMonth.year}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.width(64.dp).height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showToday,
                            enter = slideInVertically { -it / 2 } + fadeIn(),
                            exit = slideOutVertically { -it / 2 } + fadeOut(),
                        ) {
                            Text(
                                text = "Today",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button) {
                                            onDateSelected(today)
                                            coroutineScope.launch {
                                                if (showFullMonth) {
                                                    monthState.animateScrollToMonth(today.yearMonth)
                                                } else {
                                                    weekState.animateScrollToWeek(today)
                                                }
                                            }
                                        }.padding(4.dp),
                            )
                        }
                    }
                    IconButton(onClick = { stepPage(1) }, modifier = Modifier.size(36.dp)) {
                        MaterialSymbol(
                            iconName = MaterialSymbols.CHEVRON_RIGHT,
                            contentDescription = if (showFullMonth) "Next month" else "Next week",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { toggleViewMode() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        MaterialSymbol(
                            iconName = if (showFullMonth) MaterialSymbols.CALENDAR_VIEW_WEEK else MaterialSymbols.CALENDAR_MONTH,
                            contentDescription = if (showFullMonth) "Show week" else "Show full month",
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            Row(Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { dayOfWeek ->
                    Text(
                        text = dayOfWeek.displayName().take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val pagerModifier =
                Modifier
                    .fillMaxWidth()
                    .nestedScroll(consumeHorizontalScroll)
                    .testTag("calendar_pager")

            if (showFullMonth) {
                HorizontalCalendar(
                    state = monthState,
                    modifier = pagerModifier,
                    dayContent = { day ->
                        CalendarDay(
                            date = day.date,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == today,
                            hasForecast = day.date in forecastDates,
                            isEnabled = day.position == DayPosition.MonthDate,
                            onClick = { onDateSelected(day.date) },
                        )
                    },
                )
            } else {
                WeekCalendar(
                    state = weekState,
                    modifier = pagerModifier,
                    dayContent = { day ->
                        CalendarDay(
                            date = day.date,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == today,
                            hasForecast = day.date in forecastDates,
                            onClick = { onDateSelected(day.date) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList") // day cell state is passed individually
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasForecast: Boolean,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else Color.Transparent,
        label = "day_bg",
    )
    val textColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> colors.onPrimary
                isToday -> colors.primary
                else -> colors.onSurface
            },
        label = "day_text",
    )
    val dotColor =
        when {
            !hasForecast -> Color.Transparent
            isSelected -> colors.onPrimary
            else -> colors.primary
        }

    Box(
        modifier = Modifier.fillMaxWidth().alpha(if (isEnabled) 1f else 0f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 1.dp, vertical = 2.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .then(if (isToday && !isSelected) Modifier.border(BorderStroke(1.dp, colors.primary), CircleShape) else Modifier)
                    .semantics { selected = isSelected }
                    .clickable(
                        enabled = isEnabled,
                        role = Role.Button,
                        onClickLabel = "Select ${date.formatted()}",
                        onClick = onClick,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            Box(Modifier.size(4.dp).clip(CircleShape).background(dotColor))
        }
    }
}

private fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun LocalDate.formatted(): String = "${dayOfWeek.displayName().take(3)} $day ${month.displayName()}"
