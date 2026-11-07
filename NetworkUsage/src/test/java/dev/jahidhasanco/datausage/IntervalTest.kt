package dev.jahidhasanco.datausage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class IntervalTest {

    private lateinit var originalZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun pinEnvironment() {
        originalZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        // Kolkata has a half-hour offset and no DST, so midnight alignment is
        // unambiguous regardless of where the build runs.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreEnvironment() {
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    private fun midnightToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `today spans local midnight to the next midnight`() {
        val today = Interval.today
        assertEquals(midnightToday(), today.start)
        assertEquals(86_400_000L, today.end - today.start)
    }

    @Test
    fun `yesterday ends exactly where today begins`() {
        assertEquals(Interval.today.start, Interval.yesterday.end)
    }

    @Test
    fun `daily windows are returned newest first and are contiguous`() {
        val days = Interval.lastNDaysDaily(30)
        assertEquals(30, days.size)
        assertEquals(midnightToday(), days[0].start)

        for (i in 0 until days.lastIndex) {
            assertTrue("expected newest-first ordering", days[i].start > days[i + 1].start)
            assertEquals(
                "day $i must start where day ${i + 1} ends",
                days[i].start,
                days[i + 1].end
            )
        }
    }

    @Test
    fun `each daily window is aligned to local midnight`() {
        val calendar = Calendar.getInstance()
        for (day in Interval.lastNDaysDaily(90)) {
            calendar.timeInMillis = day.start
            assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, calendar.get(Calendar.MINUTE))
            assertEquals(0, calendar.get(Calendar.SECOND))
            assertEquals(0, calendar.get(Calendar.MILLISECOND))
        }
    }

    @Test
    fun `daily windows carry a distinct date label per day`() {
        val labels = Interval.lastNDaysDaily(30).map { it.date }
        assertEquals(30, labels.toSet().size)
        assertTrue(labels.none { it.isBlank() })
    }

    @Test
    fun `named daily windows delegate to the right day counts`() {
        assertEquals(7, Interval.lastWeekDaily.size)
        assertEquals(30, Interval.lastMonthDaily.size)
        assertEquals(60, Interval.last60DaysDaily.size)
        assertEquals(90, Interval.last90DaysDaily.size)
    }

    @Test
    fun `rolling windows end at the present moment`() {
        // The old implementation forced AM_PM to AM, which pushed `end` up to 12
        // hours into the past and silently dropped an afternoon of traffic.
        val before = System.currentTimeMillis()
        val window = Interval.last30days
        val after = System.currentTimeMillis()

        assertTrue("end must not be in the past", window.end in before..after)
        assertEquals(midnightToday() - 29L * 86_400_000, window.start)
    }

    @Test
    fun `rolling windows cover the requested number of days`() {
        assertEquals(midnightToday() - 6L * 86_400_000, Interval.last7days.start)
        assertEquals(midnightToday() - 59L * 86_400_000, Interval.last60days.start)
        assertEquals(midnightToday() - 89L * 86_400_000, Interval.last90days.start)
    }

    @Test
    fun `hourly windows are contiguous and start at midnight`() {
        val hours = Interval.todayHourly
        assertTrue("at least the current hour must be present", hours.isNotEmpty())
        assertTrue(hours.size <= 24)
        assertEquals(midnightToday(), hours[0].start)

        for (i in 0 until hours.lastIndex) {
            assertEquals(hours[i].end, hours[i + 1].start)
            assertEquals(3_600_000L, hours[i].end - hours[i].start)
        }
    }

    @Test
    fun `hourly windows stop at the current hour`() {
        val now = System.currentTimeMillis()
        assertTrue(Interval.todayHourly.all { it.start <= now })
    }

    @Test
    fun `this month starts on the first of the month`() {
        val calendar = Calendar.getInstance().apply { timeInMillis = Interval.thisMonth.start }
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `this week starts on the locale first day of week`() {
        val calendar = Calendar.getInstance().apply { timeInMillis = Interval.thisWeek.start }
        assertEquals(calendar.firstDayOfWeek, calendar.get(Calendar.DAY_OF_WEEK))
        assertTrue(Interval.thisWeek.start <= midnightToday())
    }

    @Test
    fun `monthly plan window is one month long and already started`() {
        val plan = Interval.monthlyPlan(15)
        val calendar = Calendar.getInstance().apply { timeInMillis = plan.start }
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
        assertTrue("billing cycle must not start in the future", plan.start <= System.currentTimeMillis())
        assertTrue(plan.end > plan.start)
    }

    @Test
    fun `weekly plan window is exactly seven days`() {
        val plan = Interval.weeklyPlan(Calendar.MONDAY)
        assertEquals(7L * 86_400_000, plan.end - plan.start)
        val calendar = Calendar.getInstance().apply { timeInMillis = plan.start }
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `daysIn counts whole days and never returns zero`() {
        assertEquals(30, Interval.daysIn(Interval.lastNDays(30)))
        assertEquals(1, Interval.daysIn(Interval.today))
        assertEquals(1, Interval.daysIn(TimeInterval(0, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive day count is rejected`() {
        Interval.lastNDaysDaily(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an out-of-range billing day is rejected`() {
        Interval.monthlyPlan(0)
    }
}
