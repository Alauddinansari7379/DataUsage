package dev.jahidhasanco.datausage.data.repository

import android.content.Context
import dev.jahidhasanco.datausage.AppUsage
import dev.jahidhasanco.datausage.ConnectionInfo
import dev.jahidhasanco.datausage.DataUsage
import dev.jahidhasanco.datausage.Interval
import dev.jahidhasanco.datausage.NetSpeed
import dev.jahidhasanco.datausage.NetworkType
import dev.jahidhasanco.datausage.NetworkUsageManager
import dev.jahidhasanco.datausage.Speed
import dev.jahidhasanco.datausage.Usage
import dev.jahidhasanco.datausage.UsageAnalytics
import dev.jahidhasanco.datausage.UsageSummary
import dev.jahidhasanco.datausage.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The windows the user can switch between.
 *
 * Carries only the day count: the pill and header captions are localized, so the
 * presentation layer maps each constant to its string resources.
 */
enum class UsageRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    TWO_MONTHS(60),
    QUARTER(90)
}

/** A live throughput sample, already formatted for display. */
data class SpeedSample(
    val total: Speed,
    val down: Speed,
    val up: Speed,
    val totalBytesPerSecond: Long
)

/** Everything one screen refresh needs, fetched together. */
data class UsageSnapshot(
    val range: UsageRange,
    val dailyMobile: List<DataUsage>,
    val dailyWifi: List<DataUsage>,
    val summary: UsageSummary,
    val mobileTotal: Usage,
    val wifiTotal: Usage,
    val todayMobile: Usage,
    val todayWifi: Usage,
    val hourly: List<DataUsage>,
    val apps: List<AppUsage>,
    val connection: ConnectionInfo
)

/**
 * Fronts every fetcher in the NetworkUsage module.
 *
 * All the `queryDetails` work is blocking IPC into the system stats service, so
 * [load] hops to the IO dispatcher; only [sampleSpeed] is cheap enough for the
 * main thread.
 */
class UsageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val subscriberId = Util.getSubscriberId(appContext)

    /** Used for the heavy interval queries. */
    private val manager = NetworkUsageManager(appContext, subscriberId)

    /**
     * Separate instance for live sampling: [NetworkUsageManager.getUsageNow]
     * returns a delta against its own last reading, so sharing one instance
     * between the ticker and background loads would corrupt both.
     */
    private val speedManager = NetworkUsageManager(appContext, subscriberId)

    /** Throughput since the previous call. Call on a steady cadence, main thread. */
    fun sampleSpeed(): SpeedSample {
        val now = speedManager.getUsageNow(NetworkType.ALL)
        val speeds = NetSpeed.calculateSpeed(now.timeTaken, now.downloads, now.uploads)
        return SpeedSample(
            total = speeds[0],
            down = speeds[1],
            up = speeds[2],
            totalBytesPerSecond = NetSpeed.bytesPerSecond(
                now.timeTaken,
                now.downloads + now.uploads
            )
        )
    }

    /** The active link, cheap enough to read on demand. */
    fun connection(): ConnectionInfo = manager.getConnectionInfo()

    suspend fun load(range: UsageRange, topAppCount: Int = 12): UsageSnapshot =
        withContext(Dispatchers.IO) {
            val days = Interval.lastNDaysDaily(range.days)
            val window = Interval.lastNDays(range.days)

            val dailyMobile = manager.getMultiUsage(days, NetworkType.MOBILE)
            val dailyWifi = manager.getMultiUsage(days, NetworkType.WIFI)

            // Summing the two per-day lists avoids a third query for the combined
            // figure, and keeps the day labels aligned with both breakdowns.
            val combined = dailyMobile.mapIndexed { index, mobile ->
                val wifi = dailyWifi.getOrNull(index)
                DataUsage(
                    downloads = mobile.downloads + (wifi?.downloads ?: 0L),
                    uploads = mobile.uploads + (wifi?.uploads ?: 0L),
                    date = mobile.date
                )
            }

            UsageSnapshot(
                range = range,
                dailyMobile = dailyMobile,
                dailyWifi = dailyWifi,
                summary = UsageAnalytics.summarize(combined),
                mobileTotal = manager.getUsage(window, NetworkType.MOBILE),
                wifiTotal = manager.getUsage(window, NetworkType.WIFI),
                todayMobile = manager.getUsage(Interval.today, NetworkType.MOBILE),
                todayWifi = manager.getUsage(Interval.today, NetworkType.WIFI),
                hourly = manager.getHourlyUsage(NetworkType.ALL),
                apps = UsageAnalytics.rankApps(
                    manager.getAppUsage(window, NetworkType.ALL),
                    topAppCount
                ),
                connection = manager.getConnectionInfo()
            )
        }
}
