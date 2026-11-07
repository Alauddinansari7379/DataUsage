package dev.jahidhasanco.datausage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jahidhasanco.datausage.data.repository.UsageRepository
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
        // Must track the applicationId in app/build.gradle.
        assertEquals("com.alauddin.datausagen", context.packageName)
    }

    @Test
    fun repositorySamplesSpeedWithoutUsageAccess() {
        // Live speed comes from TrafficStats, which needs no special access, so
        // this must work even on a device that has never granted Usage Access.
        val sample = UsageRepository(context).sampleSpeed()
        assertNotNull(sample.total.unit)
        assertTrue(sample.totalBytesPerSecond >= 0)
    }

    @Test
    fun connectionInfoIsReadableWithoutCrashing() {
        val info = UsageRepository(context).connection()
        assertNotNull(info)
        assertTrue(info.downstreamKbps >= 0)
    }
}
