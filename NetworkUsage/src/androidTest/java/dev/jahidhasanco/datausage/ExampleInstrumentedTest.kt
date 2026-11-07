package dev.jahidhasanco.datausage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun useAppContext() {
        // A library's instrumentation runs in its own test package.
        assertEquals("com.alauddin.datausage.test", context.packageName)
    }

    @Test
    fun liveSampleIsNeverNegative() {
        val manager = NetworkUsageManager(context, Util.getSubscriberId(context))
        val usage = manager.getUsageNow(NetworkType.ALL)
        assertTrue(usage.downloads >= 0)
        assertTrue(usage.uploads >= 0)
        assertTrue(usage.timeTaken >= 0)
    }

    @Test
    fun historicalQueriesDegradeGracefullyWithoutUsageAccess() {
        // Without Usage Access the platform throws SecurityException; the manager
        // is expected to swallow it and report zeroes rather than crash.
        val manager = NetworkUsageManager(context, Util.getSubscriberId(context))
        val usage = manager.getUsage(Interval.today, NetworkType.ALL)
        assertNotNull(usage)
        assertTrue(usage.downloads >= 0)
    }

    @Test
    fun connectionInfoReflectsTheActiveLink() {
        val info = NetworkUsageManager(context, null).getConnectionInfo()
        assertNotNull(info)
        assertTrue(info.upstreamKbps >= 0)
    }
}
