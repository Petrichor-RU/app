package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.launch
import kotlinx.datetime.*

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val calendarPageCount = 10_000
private const val centerCalendarPage = calendarPageCount / 2

internal val consumeHorizontalScroll = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

@Composable
fun RecommendationCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    forecastDates: Set<LocalDate> = emptySet(),
) {
    val todayWeekStart = today.startOfWeek()
    val todayMonth = today.yearMonth

    fun weekForPage(page: Int): LocalDate =
        todayWeekStart.plus((page - centerCalendarPage) * 7, DateTimeUnit.DAY)

    fun pageForWeek(date: LocalDate): Int =
        centerCalendarPage + todayWeekStart.until(date.startOfWeek(), DateTimeUnit.WEEK).toInt()

    fun monthForPage(page: Int): YearMonth =
        todayMonth.plus(page - centerCalendarPage, DateTimeUnit.MONTH)

    fun pageForMonth(month: YearMonth): Int =
        centerCalendarPage + todayMonth.monthsUntil(month)

    fun anchorForPage(page: Int, fullMonth: Boolean): LocalDate = if (fullMonth) {
        val month = monthForPage(page)
        if (month == selectedDate.yearMonth) selectedDate else month.onDay(4)
    } else {
        weekForPage(page).plus(3, DateTimeUnit.DAY)
    }

    var showFullMonth by rememberSaveable { mutableStateOf(false) }
    var anchorDateIso by rememberSaveable { mutableStateOf(selectedDate.toString()) }
    val anchorDate = LocalDate.parse(anchorDateIso)
    val coroutineScope = rememberCoroutineScope()

    val pagerState = key(showFullMonth) {
        val initialPage = if (showFullMonth) pageForMonth(anchorDate.yearMonth) else pageForWeek(anchorDate)
        rememberPagerState(initialPage = initialPage, pageCount = { calendarPageCount })
    }

    LaunchedEffect(showFullMonth, pagerState.settledPage) {
        anchorDateIso = anchorForPage(pagerState.settledPage, showFullMonth).toString()
    }

    fun stepPage(delta: Int) {
        coroutineScope.launch {
            pagerState.animateScrollToPage((pagerState.targetPage + delta).coerceIn(0, calendarPageCount - 1))
        }
    }

    val displayedMonth = anchorForPage(pagerState.currentPage, showFullMonth).yearMonth
    val showToday = selectedDate != today || pagerState.currentPage != centerCalendarPage

    Card(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
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
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    anchorDateIso = today.toString()
                                    onDateSelected(today)
                                    coroutineScope.launch { pagerState.animateScrollToPage(centerCalendarPage) }
                                }
                                .padding(4.dp),
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
                    onClick = {
                        anchorDateIso = anchorForPage(pagerState.currentPage, showFullMonth).toString()
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

            Spacer(Modifier.height(4.dp))

            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(consumeHorizontalScroll)
                    .testTag("calendar_pager"),
                verticalAlignment = Alignment.Top,
            ) { page ->
                val rows = if (showFullMonth) monthRows(monthForPage(page)) else weekRows(weekForPage(page))
                Column(Modifier.fillMaxWidth()) {
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
                                                anchorDateIso = date.toString()
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
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else Color.Transparent,
        label = "day_bg",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.onPrimary
            isToday -> colors.primary
            else -> colors.onSurface
        },
        label = "day_text",
    )
    val dotColor = when {
        !hasForecast -> Color.Transparent
        isSelected -> colors.onPrimary
        else -> colors.primary
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(1.dp, colors.primary), CircleShape) else Modifier)
            .semantics { selected = isSelected }
            .clickable(role = Role.Button, onClickLabel = "Select ${date.formatted()}", onClick = onClick),
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

private fun LocalDate.startOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

private fun weekRows(weekStart: LocalDate): List<List<LocalDate?>> =
    listOf((0..6).map { weekStart.plus(it, DateTimeUnit.DAY) })

private fun monthRows(month: YearMonth): List<List<LocalDate?>> {
    val leadingBlanks = List<LocalDate?>(month.firstDay.dayOfWeek.isoDayNumber - 1) { null }
    val days = (1..month.numberOfDays).map { month.onDay(it) }
    val cells = leadingBlanks + days
    val trailingBlanks = List<LocalDate?>((7 - cells.size % 7) % 7) { null }
    return (cells + trailingBlanks).chunked(7)
}

internal fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

internal fun LocalDate.formatted(): String =
    "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} $day ${month.displayName()}"
