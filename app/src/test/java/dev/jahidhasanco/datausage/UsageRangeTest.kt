package dev.jahidhasanco.datausage

import dev.jahidhasanco.datausage.data.repository.UsageRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRangeTest {

    @Test
    fun `every range maps to a positive day count`() {
        assertTrue(UsageRange.values().all { it.days > 0 })
    }

    @Test
    fun `day counts match the labels shown on the pills`() {
        assertEquals(7, UsageRange.WEEK.days)
        assertEquals(30, UsageRange.MONTH.days)
        assertEquals(60, UsageRange.TWO_MONTHS.days)
        assertEquals(90, UsageRange.QUARTER.days)
    }

    @Test
    fun `ranges are ordered shortest first`() {
        val days = UsageRange.values().map { it.days }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun `each range yields a daily window list of the right length`() {
        for (range in UsageRange.values()) {
            assertEquals(range.days, Interval.lastNDaysDaily(range.days).size)
        }
    }
}
