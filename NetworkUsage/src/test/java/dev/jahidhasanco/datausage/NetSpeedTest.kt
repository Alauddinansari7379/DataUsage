package dev.jahidhasanco.datausage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class NetSpeedTest {

    @Before
    fun pinLocale() {
        NetSpeed.configure(locale = Locale.US)
    }

    @After
    fun resetConfiguration() {
        // NetSpeed keeps the bits/bytes preference and units in process state.
        NetSpeed.reset()
    }

    @Test
    fun `rate is bytes divided by the elapsed window`() {
        // 2 MB over 2 seconds is 1 MB/s.
        val speeds = NetSpeed.calculateSpeed(
            timeTaken = 2000,
            downBytes = 2_000_000,
            upBytes = 0
        )
        assertEquals("1.0", speeds[1].speed)
        assertEquals("MB/s", speeds[1].unit)
    }

    @Test
    fun `total down and up are returned in that order`() {
        val speeds = NetSpeed.calculateSpeed(1000, downBytes = 3_000_000, upBytes = 1_000_000)
        assertEquals(3, speeds.size)
        assertEquals("4.0 MB/s", speeds[0].display)
        assertEquals("3.0 MB/s", speeds[1].display)
        assertEquals("1.0 MB/s", speeds[2].display)
    }

    @Test
    fun `a zero-length window yields zero rather than dividing by zero`() {
        val speeds = NetSpeed.calculateSpeed(0, 5_000_000, 5_000_000)
        assertEquals("0 B/s", speeds[0].display)
    }

    @Test
    fun `counter rollover at reboot is clamped to zero`() {
        // TrafficStats counters reset on reboot, producing a negative delta.
        assertEquals("0 B/s", NetSpeed.getSpeed(-5_000_000).display)
    }

    @Test
    fun `small rates report bytes instead of collapsing to zero kilobytes`() {
        assertEquals("999 B/s", NetSpeed.getSpeed(999).display)
        assertEquals("1 kB/s", NetSpeed.getSpeed(1000).display)
    }

    @Test
    fun `rates above ninety-nine megabytes are capped`() {
        assertEquals("99+", NetSpeed.getSpeed(150_000_000).speed)
        assertEquals("MB/s", NetSpeed.getSpeed(150_000_000).unit)
    }

    @Test
    fun `bits mode multiplies by eight and switches the unit`() {
        NetSpeed.setSpeedUnitBits(true)
        assertEquals("8.0 mb/s", NetSpeed.getSpeed(1_000_000).display)
        assertEquals(true, NetSpeed.getSpeedUnitBits())
    }

    @Test
    fun `localized rate labels replace the Latin defaults`() {
        NetSpeed.configure(byteUnits = listOf("Б/с", "КБ/с", "МБ/с"), locale = Locale.US)
        assertEquals("1.0 МБ/с", NetSpeed.getSpeed(1_000_000).display)
        assertEquals("999 Б/с", NetSpeed.getSpeed(999).display)
    }

    @Test
    fun `a wrong-sized unit list is ignored rather than crashing`() {
        NetSpeed.configure(byteUnits = listOf("nope"), locale = Locale.US)
        assertEquals("999 B/s", NetSpeed.getSpeed(999).display)
    }

    @Test
    fun `bytesPerSecond exposes the raw rate for gauges`() {
        assertEquals(1_000_000L, NetSpeed.bytesPerSecond(timeTaken = 1000, bytes = 1_000_000))
        assertEquals(0L, NetSpeed.bytesPerSecond(timeTaken = 0, bytes = 1_000_000))
    }
}
