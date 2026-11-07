package dev.jahidhasanco.datausage

import java.util.Locale

data class Speed(
    val speed: String,
    val unit: String
) {
    /** The value and unit joined, e.g. `2.1 MB/s`. */
    val display: String get() = "$speed $unit"
}

object NetSpeed {

    private const val KILO = 1000L
    private const val MEGA = 1_000_000L

    /** Bytes-per-second labels, ordered B/s, kB/s, MB/s. */
    val DEFAULT_BYTE_UNITS: List<String> = listOf("B/s", "kB/s", "MB/s")

    /** Bits-per-second labels, ordered b/s, kb/s, mb/s. */
    val DEFAULT_BIT_UNITS: List<String> = listOf("b/s", "kb/s", "mb/s")

    @Volatile
    private var isSpeedUnitBits: Boolean = false

    @Volatile
    private var byteUnitOverride: List<String>? = null

    @Volatile
    private var bitUnitOverride: List<String>? = null

    @Volatile
    private var localeOverride: Locale? = null

    fun setSpeedUnitBits(isSpeedUnitBits: Boolean) {
        this.isSpeedUnitBits = isSpeedUnitBits
    }

    fun getSpeedUnitBits(): Boolean = isSpeedUnitBits

    /** Supplies localized rate labels; each list must hold three entries. */
    fun configure(
        byteUnits: List<String>? = null,
        bitUnits: List<String>? = null,
        locale: Locale? = null
    ) {
        if (byteUnits != null && byteUnits.size == 3) byteUnitOverride = byteUnits
        if (bitUnits != null && bitUnits.size == 3) bitUnitOverride = bitUnits
        localeOverride = locale
    }

    /** Drops any [configure] call, restoring Latin units and the default locale. */
    fun reset() {
        byteUnitOverride = null
        bitUnitOverride = null
        localeOverride = null
        isSpeedUnitBits = false
    }

    private fun units(): List<String> = if (isSpeedUnitBits) {
        bitUnitOverride ?: DEFAULT_BIT_UNITS
    } else {
        byteUnitOverride ?: DEFAULT_BYTE_UNITS
    }

    /**
     * Format a byte-per-second rate for display.
     *
     * Rates are clamped at zero: a negative delta only happens when the
     * underlying counters roll over at reboot, and rendering "-3 MB/s" would be
     * meaningless.
     */
    fun getSpeed(bytesPerSecond: Long): Speed {
        val units = units()
        val locale = localeOverride ?: Locale.getDefault()
        val base = bytesPerSecond.coerceAtLeast(0)
        val speed = if (isSpeedUnitBits) base * 8 else base

        return when {
            speed < KILO -> Speed(speed.toString(), units[0])

            speed < MEGA -> Speed((speed / KILO).toString(), units[1])

            else -> {
                val value = when {
                    speed < 10 * MEGA -> String.format(locale, "%.1f", speed / MEGA.toDouble())
                    speed < 100 * MEGA -> (speed / MEGA).toString()
                    else -> "99+"
                }
                Speed(value, units[2])
            }
        }
    }

    /**
     * Convert a sample into total/down/up rates.
     *
     * Returns three entries in that fixed order; a non-positive [timeTaken] yields
     * zeroed rates rather than dividing by zero.
     */
    fun calculateSpeed(timeTaken: Long, downBytes: Long, upBytes: Long): List<Speed> {
        var totalSpeed = 0L
        var downSpeed = 0L
        var upSpeed = 0L

        if (timeTaken > 0) {
            totalSpeed = (downBytes + upBytes) * 1000 / timeTaken
            downSpeed = downBytes * 1000 / timeTaken
            upSpeed = upBytes * 1000 / timeTaken
        }

        return listOf(getSpeed(totalSpeed), getSpeed(downSpeed), getSpeed(upSpeed))
    }

    /** Raw bytes-per-second for a sample, for gauges that need a number not a label. */
    fun bytesPerSecond(timeTaken: Long, bytes: Long): Long =
        if (timeTaken > 0) bytes * 1000 / timeTaken else 0L
}
