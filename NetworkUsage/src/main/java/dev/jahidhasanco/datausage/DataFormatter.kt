package dev.jahidhasanco.datausage

import java.util.Locale
import kotlin.math.abs

/**
 * Byte and speed formatting. Uses binary (1024) steps, matching how Android's
 * own data-usage screens report totals.
 *
 * Both the decimal separator and the unit symbols are localizable: German writes
 * `1,50 MB`, and Russian abbreviates the units in Cyrillic. Callers that render
 * UI should hand in localized units once via [configure]; everything else gets
 * sensible Latin defaults.
 */
object DataFormatter {

    private const val UNIT = 1024.0

    /** B, KB, MB, GB, TB, PB — ordered smallest first. */
    val DEFAULT_SIZE_UNITS: List<String> = listOf("B", "KB", "MB", "GB", "TB", "PB")

    @Volatile
    private var unitOverride: List<String>? = null

    @Volatile
    private var localeOverride: Locale? = null

    /** Falls back to the process default so a locale change is picked up live. */
    private val activeLocale: Locale get() = localeOverride ?: Locale.getDefault()

    private val activeUnits: List<String> get() = unitOverride ?: DEFAULT_SIZE_UNITS

    /**
     * @param sizeUnits six labels, smallest first; ignored unless the size matches.
     * @param locale pins number formatting; null follows the process default.
     */
    fun configure(sizeUnits: List<String>? = null, locale: Locale? = null) {
        if (sizeUnits != null && sizeUnits.size == DEFAULT_SIZE_UNITS.size) {
            unitOverride = sizeUnits
        }
        localeOverride = locale
    }

    /** Drops any [configure] call, restoring Latin units and the default locale. */
    fun reset() {
        unitOverride = null
        localeOverride = null
    }

    /** e.g. `1.24 GB`, `812 KB`, `0 B`. */
    fun format(bytes: Long): String {
        val (value, unit) = split(bytes)
        return "$value $unit"
    }

    /** The numeric half of [format], useful when the unit is rendered separately. */
    fun value(bytes: Long): String = split(bytes).first

    /** The unit half of [format]. */
    fun unit(bytes: Long): String = split(bytes).second

    private fun split(bytes: Long): Pair<String, String> {
        val units = activeUnits
        if (bytes == 0L) return format(0.0, 0) to units[0]

        val negative = bytes < 0
        var value = abs(bytes).toDouble()
        var index = 0
        while (value >= UNIT && index < units.lastIndex) {
            value /= UNIT
            index++
        }

        val formatted = format(value, index)
        return (if (negative) "-$formatted" else formatted) to units[index]
    }

    /**
     * Whole bytes have no fractional part worth showing; larger units get enough
     * precision to distinguish neighbouring values without growing the label.
     */
    private fun format(value: Double, unitIndex: Int): String {
        val pattern = when {
            unitIndex == 0 -> "%.0f"
            value >= 100 -> "%.0f"
            value >= 10 -> "%.1f"
            else -> "%.2f"
        }
        return String.format(activeLocale, pattern, value)
    }

    /** e.g. `2.00 MB/s`. */
    fun formatSpeed(bytesPerSecond: Long): String = "${format(bytesPerSecond)}/s"
}
