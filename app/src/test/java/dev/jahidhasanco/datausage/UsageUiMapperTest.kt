package dev.jahidhasanco.datausage

import dev.jahidhasanco.datausage.presentation.mapper.UsageUiMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageUiMapperTest {

    private fun day(label: String, down: Long, up: Long = 0L) =
        DataUsage(downloads = down, uploads = up, date = label)

    private val noIcons: (String) -> android.graphics.drawable.Drawable? = { null }

    // Stands in for the Activity's localized strings.
    private val fallback: (AppUsage) -> String = { "kind:${it.kind}" }

    // ---------------------------------------------------------------- daily rows

    @Test
    fun `rows pair mobile and wifi by index`() {
        val rows = UsageUiMapper.dailyRows(
            mobile = listOf(day("Mon", 1024), day("Tue", 2048)),
            wifi = listOf(day("Mon", 3072), day("Tue", 1024))
        )
        assertEquals(2, rows.size)
        assertEquals("Mon", rows[0].date)
        assertEquals("1.00 KB", rows[0].mobile)
        assertEquals("3.00 KB", rows[0].wifi)
        assertEquals(4096L, rows[0].totalBytes)
    }

    @Test
    fun `a short wifi list is treated as zero rather than dropping days`() {
        // A partially failed query must not silently shorten the table.
        val rows = UsageUiMapper.dailyRows(
            mobile = listOf(day("Mon", 1024), day("Tue", 1024), day("Wed", 1024)),
            wifi = listOf(day("Mon", 1024))
        )
        assertEquals(3, rows.size)
        assertEquals("0 B", rows[1].wifi)
        assertEquals(1024L, rows[1].totalBytes)
    }

    @Test
    fun `bars scale against the busiest day`() {
        val rows = UsageUiMapper.dailyRows(
            mobile = listOf(day("Mon", 1000), day("Tue", 500), day("Wed", 0)),
            wifi = emptyList()
        )
        assertEquals(100, rows[0].sharePercent)
        assertEquals(50, rows[1].sharePercent)
        assertEquals(0, rows[2].sharePercent)
    }

    @Test
    fun `a completely idle range produces no division by zero`() {
        val rows = UsageUiMapper.dailyRows(
            mobile = listOf(day("Mon", 0), day("Tue", 0)),
            wifi = listOf(day("Mon", 0), day("Tue", 0))
        )
        assertTrue(rows.all { it.sharePercent == 0 })
    }

    @Test
    fun `an empty range maps to no rows`() {
        assertTrue(UsageUiMapper.dailyRows(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `uploads count towards a day's total`() {
        val rows = UsageUiMapper.dailyRows(
            mobile = listOf(day("Mon", 0, up = 2048)),
            wifi = emptyList()
        )
        assertEquals(2048L, rows[0].totalBytes)
        assertEquals("2.00 KB", rows[0].mobile)
    }

    // ---------------------------------------------------------------- chart

    @Test
    fun `hourly bars keep their order and combine both directions`() {
        val bars = UsageUiMapper.hourlyBars(
            listOf(day("00:00", 100, 50), day("01:00", 200, 0))
        )
        assertEquals(listOf("00:00", "01:00"), bars.map { it.label })
        assertEquals(150L, bars[0].value)
        assertEquals(200L, bars[1].value)
    }

    @Test
    fun `an empty day maps to no bars`() {
        assertTrue(UsageUiMapper.hourlyBars(emptyList()).isEmpty())
    }

    // ---------------------------------------------------------------- app rows

    @Test
    fun `app bars scale against the heaviest app`() {
        val rows = UsageUiMapper.appRows(
            listOf(
                AppUsage(uid = 1, label = "big", downloads = 800),
                AppUsage(uid = 2, label = "small", downloads = 200)
            ),
            fallback,
            noIcons
        )
        assertEquals(100, rows[0].sharePercent)
        assertEquals(25, rows[1].sharePercent)
    }

    @Test
    fun `a blank label defers to the caller-supplied localized name`() {
        // The library reports a category for uids it cannot resolve; naming them
        // is the presentation layer's job so the text can be translated.
        val rows = UsageUiMapper.appRows(
            listOf(
                AppUsage(uid = 1, label = "Chrome", kind = AppKind.NORMAL, downloads = 10),
                AppUsage(uid = 2, label = "", kind = AppKind.TETHERING, downloads = 10),
                AppUsage(uid = 3, label = "", kind = AppKind.UNKNOWN, downloads = 10)
            ),
            fallback,
            noIcons
        )
        assertEquals("Chrome", rows[0].label)
        assertEquals("kind:TETHERING", rows[1].label)
        assertEquals("kind:UNKNOWN", rows[2].label)
    }

    @Test
    fun `an icon loader that fails leaves the row usable`() {
        val rows = UsageUiMapper.appRows(
            listOf(AppUsage(uid = 1, label = "app", downloads = 10)),
            fallback,
            noIcons
        )
        assertNull(rows[0].icon)
        assertEquals("app", rows[0].label)
    }

    @Test
    fun `app totals carry both directions through to the row`() {
        val rows = UsageUiMapper.appRows(
            listOf(AppUsage(uid = 1, label = "app", downloads = 700, uploads = 300)),
            fallback,
            noIcons
        )
        assertEquals(700L, rows[0].downloads)
        assertEquals(300L, rows[0].uploads)
        assertEquals(1000L, rows[0].total)
    }

    @Test
    fun `an empty app list maps cleanly`() {
        assertTrue(UsageUiMapper.appRows(emptyList(), fallback, noIcons).isEmpty())
    }
}
