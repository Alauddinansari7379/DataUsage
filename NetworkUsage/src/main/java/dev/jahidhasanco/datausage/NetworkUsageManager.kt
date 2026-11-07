package dev.jahidhasanco.datausage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.telephony.TelephonyManager
import android.util.Log

class NetworkUsageManager(
    context: Context, private val subscriberId: String?
) {

    private val appContext: Context = context.applicationContext
    private val statsManager: NetworkStatsManager? =
        appContext.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    private var lastTXByte = 0L
    private var lastRXByte = 0L
    private var lastMobileTXByte = 0L
    private var lastMobileRXByte = 0L
    private var lastTime = 0L

    init {
        lastTXByte = TrafficStats.getTotalTxBytes()
        lastRXByte = TrafficStats.getTotalRxBytes()
        lastMobileTXByte = TrafficStats.getMobileTxBytes()
        lastMobileRXByte = TrafficStats.getMobileRxBytes()
        lastTime = System.currentTimeMillis()
    }

    /**
     * Traffic seen since the previous call, for live speed readouts.
     *
     * [TrafficStats] counters reset when the device reboots, which would otherwise
     * produce a large negative delta; those samples are reported as zero.
     */
    fun getUsageNow(networkType: NetworkType): Usage {
        val currentRXByte = TrafficStats.getTotalRxBytes()
        val currentTXByte = TrafficStats.getTotalTxBytes()
        val currentMobileRXByte = TrafficStats.getMobileRxBytes()
        val currentMobileTXByte = TrafficStats.getMobileTxBytes()
        val currentTime = System.currentTimeMillis()

        val usedRxByte = (currentRXByte - lastRXByte).coerceAtLeast(0)
        val usedTxByte = (currentTXByte - lastTXByte).coerceAtLeast(0)
        val usedMobileRxByte = (currentMobileRXByte - lastMobileRXByte).coerceAtLeast(0)
        val usedMobileTxByte = (currentMobileTXByte - lastMobileTXByte).coerceAtLeast(0)
        val usedTime = (currentTime - lastTime).coerceAtLeast(0)

        lastRXByte = currentRXByte
        lastTXByte = currentTXByte
        lastMobileRXByte = currentMobileRXByte
        lastMobileTXByte = currentMobileTXByte
        lastTime = currentTime

        return when (networkType) {
            NetworkType.MOBILE -> Usage(usedMobileRxByte, usedMobileTxByte, usedTime)
            NetworkType.WIFI -> Usage(
                (usedRxByte - usedMobileRxByte).coerceAtLeast(0),
                (usedTxByte - usedMobileTxByte).coerceAtLeast(0),
                usedTime
            )
            NetworkType.ALL -> Usage(usedRxByte, usedTxByte, usedTime)
        }
    }

    /** Total traffic recorded inside [interval]. */
    fun getUsage(interval: TimeInterval, networkType: NetworkType): Usage {
        val usage = Usage()
        usage.timeTaken = interval.end - interval.start
        forEachBucket(networkType, interval.start, interval.end) { bucket ->
            usage.downloads += bucket.rxBytes
            usage.uploads += bucket.txBytes
        }
        return usage
    }

    /** Traffic split across each of [intervals], returned in the order given. */
    fun getMultiUsage(intervals: List<TimeInterval>, networkType: NetworkType): List<DataUsage> {
        if (intervals.isEmpty()) return emptyList()

        var start = intervals[0].start
        var end = intervals[0].end
        val usages = LinkedHashMap<TimeInterval, DataUsage>(intervals.size)

        for (interval in intervals) {
            if (interval.start < start) start = interval.start
            if (interval.end > end) end = interval.end
            usages[interval] = DataUsage(date = interval.date)
        }

        forEachBucket(networkType, start, end) { bucket ->
            for (interval in intervals) {
                if (checkBucketInterval(bucket, interval.start, interval.end)) {
                    val target = usages[interval] ?: continue
                    target.downloads += bucket.rxBytes
                    target.uploads += bucket.txBytes
                }
            }
        }

        return intervals.mapNotNull { usages[it] }
    }

    /** Hour-by-hour traffic for today, oldest hour first. */
    fun getHourlyUsage(networkType: NetworkType): List<DataUsage> =
        getMultiUsage(Interval.todayHourly, networkType)

    /**
     * Traffic broken down per application over [interval].
     *
     * Buckets that belong to the kernel or to shared/removed uids are folded into
     * a single synthetic entry so their traffic is not silently dropped.
     */
    fun getAppUsage(
        interval: TimeInterval,
        networkType: NetworkType,
        includeSystemApps: Boolean = true
    ): List<AppUsage> {
        val byUid = HashMap<Int, AppUsage>()

        forEachBucket(networkType, interval.start, interval.end) { bucket ->
            val entry = byUid.getOrPut(bucket.uid) { AppUsage(uid = bucket.uid) }
            entry.downloads += bucket.rxBytes
            entry.uploads += bucket.txBytes
        }

        val packageManager = appContext.packageManager
        val resolved = byUid.values.onEach { resolveIdentity(it, packageManager) }

        return UsageAnalytics.rankApps(
            if (includeSystemApps) resolved.toList() else resolved.filterNot { it.isSystem }
        )
    }

    /** A snapshot of the network the device is using right now. */
    fun getConnectionInfo(): ConnectionInfo {
        val connectivity = appContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ConnectionInfo()

        val network = connectivity.activeNetwork ?: return ConnectionInfo()
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return ConnectionInfo()

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> null
        }

        val carrier = runCatching {
            (appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                ?.networkOperatorName.orEmpty()
        }.getOrDefault("")

        // NET_CAPABILITY_NOT_ROAMING only exists from API 28 onwards.
        val roaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            false
        }

        return ConnectionInfo(
            connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            transport = transport,
            carrierName = carrier,
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = roaming,
            vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            downstreamKbps = capabilities.linkDownstreamBandwidthKbps,
            upstreamKbps = capabilities.linkUpstreamBandwidthKbps
        )
    }

    /** Daily buckets over the last [days] days rolled up into a [UsageSummary]. */
    fun getSummary(days: Int, networkType: NetworkType): UsageSummary =
        UsageAnalytics.summarize(getMultiUsage(Interval.lastNDaysDaily(days), networkType))

    /**
     * Fills in [AppUsage.kind] and, where the package manager cooperates, the
     * app's own label. Synthetic and unresolvable uids are left unlabelled: the
     * caller supplies a localized name for those.
     */
    private fun resolveIdentity(usage: AppUsage, packageManager: PackageManager) {
        when (usage.uid) {
            NetworkStats.Bucket.UID_TETHERING -> {
                usage.packageName = "android.tethering"
                usage.kind = AppKind.TETHERING
                usage.isSystem = true
                return
            }
            NetworkStats.Bucket.UID_REMOVED -> {
                usage.packageName = "android.removed"
                usage.kind = AppKind.UNINSTALLED
                usage.isSystem = true
                return
            }
            Process.SYSTEM_UID, NetworkStats.Bucket.UID_ALL -> {
                usage.packageName = "android.system"
                usage.kind = AppKind.SYSTEM
                usage.isSystem = true
                return
            }
        }

        val packages = packageManager.getPackagesForUid(usage.uid)
        if (packages.isNullOrEmpty()) {
            // Package visibility can hide the uid's packages even with the
            // launcher <queries> declared (non-launchable system components).
            // getNameForUid still resolves shared-user names such as
            // "android.uid.system", which beats showing a bare number.
            val sharedName = runCatching { packageManager.getNameForUid(usage.uid) }.getOrNull()
            usage.packageName = sharedName ?: "uid:${usage.uid}"
            usage.label = sharedName.orEmpty()
            usage.kind = AppKind.UNKNOWN
            usage.isSystem = true
            return
        }

        usage.packageName = packages[0]
        usage.kind = AppKind.NORMAL
        usage.label = runCatching {
            val info = packageManager.getApplicationInfo(packages[0], 0)
            usage.isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packages[0])
    }

    /**
     * [NetworkCapabilities] transports are indices, not bit flags, so a combined
     * query has to run once per transport rather than OR-ing the constants.
     */
    private fun transportsFor(networkType: NetworkType): IntArray = when (networkType) {
        NetworkType.MOBILE -> intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)
        NetworkType.WIFI -> intArrayOf(NetworkCapabilities.TRANSPORT_WIFI)
        NetworkType.ALL -> intArrayOf(
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_WIFI
        )
    }

    private inline fun forEachBucket(
        networkType: NetworkType,
        start: Long,
        end: Long,
        action: (NetworkStats.Bucket) -> Unit
    ) {
        val manager = statsManager ?: return
        for (transport in transportsFor(networkType)) {
            // Usage access can be revoked at any time, and the platform service can
            // die; either surfaces here and must not take the caller down with it.
            val stats = try {
                manager.queryDetails(transport, subscriberId, start, end)
            } catch (e: SecurityException) {
                Log.w(TAG, "Usage access denied for transport $transport", e)
                null
            } catch (e: RuntimeException) {
                Log.w(TAG, "NetworkStats query failed for transport $transport", e)
                null
            } ?: continue

            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    action(bucket)
                }
            } finally {
                stats.close()
            }
        }
    }

    private fun checkBucketInterval(bucket: NetworkStats.Bucket, start: Long, end: Long): Boolean {
        return ((bucket.startTimeStamp in start..end) ||
                (bucket.endTimeStamp in (start + 1) until end))
    }

    private companion object {
        const val TAG = "NetworkUsageManager"
    }
}
