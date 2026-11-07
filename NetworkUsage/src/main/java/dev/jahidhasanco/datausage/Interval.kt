package dev.jahidhasanco.datausage

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Builds the [TimeInterval] windows that the fetchers query against.
 *
 * All day/hour boundaries are produced with calendar arithmetic rather than by
 * subtracting fixed millisecond offsets, so the buckets stay aligned to local
 * midnight across daylight-saving transitions.
 */
object Interval {

    private const val MILLIS_PER_DAY = 86_400_000L

    private val parser: DateFormat
        get() = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())

    private fun startOfToday(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.plusDays(days: Int): Calendar =
        (clone() as Calendar).apply { add(Calendar.DATE, days) }

    val today: TimeInterval
        get() {
            val start = startOfToday()
            return TimeInterval(
                start.timeInMillis,
                start.plusDays(1).timeInMillis,
                parser.format(start.time)
            )
        }

    val yesterday: TimeInterval
        get() {
            val start = startOfToday().plusDays(-1)
            return TimeInterval(
                start.timeInMillis,
                start.plusDays(1).timeInMillis,
                parser.format(start.time)
            )
        }

    /**
     * One interval per day for the last [days] days, most recent first.
     * Index 0 is today, index 1 yesterday, and so on.
     */
    fun lastNDaysDaily(days: Int): List<TimeInterval> {
        require(days > 0) { "days must be positive, was $days" }
        val format = parser
        val intervals = ArrayList<TimeInterval>(days)
        val cursor = startOfToday()
        for (i in 0 until days) {
            intervals.add(
                TimeInterval(
                    cursor.timeInMillis,
                    cursor.plusDays(1).timeInMillis,
                    format.format(cursor.time)
                )
            )
            cursor.add(Calendar.DATE, -1)
        }
        return intervals
    }

    /** A single window covering the last [days] days up to right now. */
    fun lastNDays(days: Int): TimeInterval {
        require(days > 0) { "days must be positive, was $days" }
        val end = System.currentTimeMillis()
        val start = startOfToday().plusDays(-(days - 1))
        return TimeInterval(start.timeInMillis, end, parser.format(start.time))
    }

    /** Hour-by-hour windows for today, from midnight up to the current hour. */
    val todayHourly: List<TimeInterval>
        get() {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis()
            val cursor = startOfToday()
            val intervals = ArrayList<TimeInterval>(24)
            for (hour in 0 until 24) {
                if (cursor.timeInMillis > now) break
                val label = format.format(cursor.time)
                val start = cursor.timeInMillis
                cursor.add(Calendar.HOUR_OF_DAY, 1)
                intervals.add(TimeInterval(start, cursor.timeInMillis, label))
            }
            return intervals
        }

    val lastWeekDaily: List<TimeInterval> get() = lastNDaysDaily(7)
    val lastMonthDaily: List<TimeInterval> get() = lastNDaysDaily(30)
    val last60DaysDaily: List<TimeInterval> get() = lastNDaysDaily(60)
    val last90DaysDaily: List<TimeInterval> get() = lastNDaysDaily(90)

    val last7days: TimeInterval get() = lastNDays(7)
    val last30days: TimeInterval get() = lastNDays(30)
    val last60days: TimeInterval get() = lastNDays(60)
    val last90days: TimeInterval get() = lastNDays(90)

    /** From the first day of the current week (per the device locale) up to now. */
    val thisWeek: TimeInterval
        get() {
            val start = startOfToday()
            val firstDay = start.firstDayOfWeek
            var back = start.get(Calendar.DAY_OF_WEEK) - firstDay
            if (back < 0) back += 7
            start.add(Calendar.DATE, -back)
            return TimeInterval(
                start.timeInMillis,
                System.currentTimeMillis(),
                parser.format(start.time)
            )
        }

    /** From the 1st of the current month up to now. */
    val thisMonth: TimeInterval
        get() {
            val start = startOfToday()
            start.set(Calendar.DAY_OF_MONTH, 1)
            return TimeInterval(
                start.timeInMillis,
                System.currentTimeMillis(),
                parser.format(start.time)
            )
        }

    /** One interval per day for the current month so far, most recent first. */
    val thisMonthDaily: List<TimeInterval>
        get() = lastNDaysDaily(startOfToday().get(Calendar.DAY_OF_MONTH))

    val week: TimeInterval get() = thisWeek

    val month: TimeInterval
        get() {
            val start = startOfToday()
            start.set(Calendar.DAY_OF_MONTH, 1)
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            return TimeInterval(start.timeInMillis, end.timeInMillis, parser.format(start.time))
        }

    /** Billing-cycle window starting on [startDay] of the month. */
    fun monthlyPlan(startDay: Int): TimeInterval {
        require(startDay in 1..31) { "startDay must be 1..31, was $startDay" }
        val start = startOfToday()
        val cappedDay = minOf(startDay, start.getActualMaximum(Calendar.DAY_OF_MONTH))
        start.set(Calendar.DAY_OF_MONTH, cappedDay)
        if (start.timeInMillis > System.currentTimeMillis()) start.add(Calendar.MONTH, -1)
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return TimeInterval(start.timeInMillis, end.timeInMillis, parser.format(start.time))
    }

    /** Weekly-plan window starting on [startDay] (a [Calendar] day-of-week constant). */
    fun weeklyPlan(startDay: Int): TimeInterval {
        require(startDay in Calendar.SUNDAY..Calendar.SATURDAY) {
            "startDay must be a Calendar day-of-week constant, was $startDay"
        }
        val start = startOfToday()
        var back = start.get(Calendar.DAY_OF_WEEK) - startDay
        if (back < 0) back += 7
        start.add(Calendar.DATE, -back)
        val end = start.plusDays(7)
        return TimeInterval(start.timeInMillis, end.timeInMillis, parser.format(start.time))
    }

    /**
     * Number of calendar days [interval] touches; always at least 1.
     *
     * Rounds up, because a rolling window ends at the current moment and its
     * final, partly-elapsed day still counts as a day of usage.
     */
    fun daysIn(interval: TimeInterval): Int {
        val span = interval.end - interval.start
        if (span <= 0L) return 1
        return maxOf(1, ceil(span.toDouble() / MILLIS_PER_DAY).toInt())
    }
}
