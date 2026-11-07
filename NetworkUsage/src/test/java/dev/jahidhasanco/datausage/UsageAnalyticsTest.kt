package dev.jahidhasanco.datausage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAnalyticsTest {

    private fun day(label: String, down: Long, up: Long = 0L) =
        DataUsage(downloads = down, uploads = up, date = label)

    @Test
    fun `an empty history summarises to zeroes instead of dividing by zero`() {
        val summary = UsageAnalytics.summarize(emptyList())
        assertEquals(0L, summary.total)
        assertEquals(0L, summary.dailyAverage)
        assertEquals(0, summary.daysCounted)
        assertNull(summary.peak)
    }

    @Test
    fun `totals sum downloads and uploads separately`() {
        val summary = UsageAnalytics.summarize(
            listOf(day("d1", 100, 10), day("d2", 200, 20), day("d3", 300, 30))
        )
        assertEquals(600L, summary.downloads)
        assertEquals(60L, summary.uploads)
        assertEquals(660L, summary.total)
    }

    @Test
    fun `daily average divides by every day in the window not just active ones`() {
        // Four days, only two with traffic: the average must still use 4.
        val summary = UsageAnalytics.summarize(
            listOf(day("d1", 400), day("d2", 0), day("d3", 400), day("d4", 0))
        )
        assertEquals(4, summary.daysCounted)
        assertEquals(2, summary.activeDays)
        assertEquals(200L, summary.dailyAverage)
    }

    @Test
    fun `peak picks the heaviest day by combined traffic`() {
        val summary = UsageAnalytics.summarize(
            listOf(day("d1", 100, 0), day("d2", 60, 500), day("d3", 300, 0))
        )
        assertEquals("d2", summary.peak?.date)
    }

    @Test
    fun `projection extrapolates the daily average across a month`() {
        val summary = UsageAnalytics.summarize(listOf(day("d1", 100), day("d2", 300)))
        assertEquals(200L, summary.dailyAverage)
        assertEquals(6000L, summary.projectedMonthly)
    }

    @Test
    fun `trend compares the recent half against the older half`() {
        // Newest-first: recent 200, older 100 -> +100%.
        val usages = listOf(day("d1", 100), day("d2", 100), day("d3", 50), day("d4", 50))
        assertEquals(100, UsageAnalytics.trendPercent(usages))
    }

    @Test
    fun `trend is negative when usage is falling`() {
        val usages = listOf(day("d1", 25), day("d2", 25), day("d3", 100), day("d4", 100))
        assertEquals(-75, UsageAnalytics.trendPercent(usages))
    }

    @Test
    fun `trend is zero when there is no baseline to compare against`() {
        assertEquals(0, UsageAnalytics.trendPercent(emptyList()))
        assertEquals(0, UsageAnalytics.trendPercent(listOf(day("d1", 100))))
        assertEquals(0, UsageAnalytics.trendPercent(listOf(day("d1", 100), day("d2", 0))))
    }

    @Test
    fun `trend ignores the middle sample when the history is odd sized`() {
        // 5 days -> halves of 2, the middle day is excluded from both sides.
        val usages = listOf(
            day("d1", 100), day("d2", 100),
            day("d3", 999_999),
            day("d4", 100), day("d5", 100)
        )
        assertEquals(0, UsageAnalytics.trendPercent(usages))
    }

    @Test
    fun `percentOf clamps into a sane range`() {
        assertEquals(25, UsageAnalytics.percentOf(25, 100))
        assertEquals(0, UsageAnalytics.percentOf(0, 100))
        assertEquals(0, UsageAnalytics.percentOf(50, 0))
        assertEquals(100, UsageAnalytics.percentOf(500, 100))
        assertEquals(0, UsageAnalytics.percentOf(-5, 100))
    }

    @Test
    fun `apps are ranked by total traffic descending`() {
        val ranked = UsageAnalytics.rankApps(
            listOf(
                AppUsage(uid = 1, label = "small", downloads = 10),
                AppUsage(uid = 2, label = "large", downloads = 900),
                AppUsage(uid = 3, label = "medium", downloads = 100)
            )
        )
        assertEquals(listOf("large", "medium", "small"), ranked.map { it.label })
    }

    @Test
    fun `ranking counts uploads towards the total`() {
        val ranked = UsageAnalytics.rankApps(
            listOf(
                AppUsage(uid = 1, label = "downloader", downloads = 500),
                AppUsage(uid = 2, label = "uploader", downloads = 0, uploads = 900)
            )
        )
        assertEquals("uploader", ranked.first().label)
    }

    @Test
    fun `silent apps are dropped and the limit is respected`() {
        val ranked = UsageAnalytics.rankApps(
            listOf(
                AppUsage(uid = 1, label = "idle", downloads = 0),
                AppUsage(uid = 2, label = "a", downloads = 300),
                AppUsage(uid = 3, label = "b", downloads = 200)
            ),
            limit = 1
        )
        assertEquals(1, ranked.size)
        assertEquals("a", ranked.first().label)
    }

    @Test
    fun `ranking an empty list is safe`() {
        assertTrue(UsageAnalytics.rankApps(emptyList()).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative limit is rejected`() {
        UsageAnalytics.rankApps(emptyList(), limit = -1)
    }
}
