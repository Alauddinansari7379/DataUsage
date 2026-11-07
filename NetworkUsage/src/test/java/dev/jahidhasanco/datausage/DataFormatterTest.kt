package dev.jahidhasanco.datausage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DataFormatterTest {

    @Before
    fun pinLocale() {
        // Formatting now follows the device locale, so the expectations below
        // have to name the one they were written against.
        DataFormatter.configure(locale = Locale.US)
    }

    @After
    fun clearConfiguration() {
        DataFormatter.reset()
    }

    @Test
    fun `zero bytes renders without a fractional part`() {
        assertEquals("0 B", DataFormatter.format(0))
    }

    @Test
    fun `sub-kilobyte values stay in bytes`() {
        assertEquals("512 B", DataFormatter.format(512))
        assertEquals("1023 B", DataFormatter.format(1023))
    }

    @Test
    fun `unit steps happen on binary boundaries`() {
        assertEquals("1.00 KB", DataFormatter.format(1024))
        assertEquals("1.00 MB", DataFormatter.format(1024L * 1024))
        assertEquals("1.00 GB", DataFormatter.format(1024L * 1024 * 1024))
        assertEquals("1.00 TB", DataFormatter.format(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `precision drops as the magnitude grows`() {
        // < 10 -> 2dp, < 100 -> 1dp, >= 100 -> 0dp, so long totals stay compact.
        assertEquals("1.50 KB", DataFormatter.format(1536))
        assertEquals("10.5 KB", DataFormatter.format(10752))
        assertEquals("512 KB", DataFormatter.format(524288))
    }

    @Test
    fun `value and unit halves agree with the combined output`() {
        val bytes = 5L * 1024 * 1024
        assertEquals("5.00", DataFormatter.value(bytes))
        assertEquals("MB", DataFormatter.unit(bytes))
        assertEquals(
            DataFormatter.format(bytes),
            "${DataFormatter.value(bytes)} ${DataFormatter.unit(bytes)}"
        )
    }

    @Test
    fun `negative totals keep their sign`() {
        assertEquals("-1.00 KB", DataFormatter.format(-1024))
    }

    @Test
    fun `largest unit does not overflow past petabytes`() {
        assertEquals("8.00 PB", DataFormatter.format(9007199254740992L))
    }

    @Test
    fun `localized units replace the Latin defaults`() {
        DataFormatter.configure(
            sizeUnits = listOf("Б", "КБ", "МБ", "ГБ", "ТБ", "ПБ"),
            locale = Locale.US
        )
        assertEquals("1.00 КБ", DataFormatter.format(1024))
        assertEquals("512 Б", DataFormatter.format(512))
    }

    @Test
    fun `a wrong-sized unit list is ignored rather than crashing`() {
        DataFormatter.configure(sizeUnits = listOf("only", "two"), locale = Locale.US)
        assertEquals("1.00 KB", DataFormatter.format(1024))
    }

    @Test
    fun `the decimal separator follows the locale`() {
        DataFormatter.configure(locale = Locale.GERMANY)
        assertEquals("1,50 KB", DataFormatter.format(1536))
    }

    @Test
    fun `speed appends a per-second suffix`() {
        assertEquals("2.00 MB/s", DataFormatter.formatSpeed(2L * 1024 * 1024))
    }

    @Test
    fun `formatData returns downloads uploads and total in that order`() {
        val formatted = Util.formatData(downloads = 2048, uploads = 1024)
        assertEquals(listOf("2.00 KB", "1.00 KB", "3.00 KB"), formatted)
    }
}
