package dev.jahidhasanco.datausage

import kotlin.math.roundToLong

/** Aggregate statistics derived from a list of per-day [DataUsage] buckets. */
data class UsageSummary(
    val downloads: Long = 0L,
    val uploads: Long = 0L,
    val dailyAverage: Long = 0L,
    val peak: DataUsage? = null,
    val daysCounted: Int = 0,
    val activeDays: Int = 0,
    val projectedMonthly: Long = 0L,
    val trendPercent: Int = 0
) {
    val total: Long get() = downloads + uploads
}

/**
 * Pure aggregation helpers over already-fetched usage buckets.
 *
 * Deliberately free of Android dependencies so the arithmetic can be unit tested
 * on the JVM.
 */
object UsageAnalytics {

    /** Roll a list of daily buckets up into a [UsageSummary]. */
    fun summarize(usages: List<DataUsage>): UsageSummary {
        if (usages.isEmpty()) return UsageSummary()

        var downloads = 0L
        var uploads = 0L
        var activeDays = 0
        var peak: DataUsage? = null

        for (usage in usages) {
            downloads += usage.downloads
            uploads += usage.uploads
            val bucketTotal = usage.downloads + usage.uploads
            if (bucketTotal > 0) activeDays++
            if (peak == null || bucketTotal > peak.downloads + peak.uploads) peak = usage
        }

        val total = downloads + uploads
        val days = usages.size
        val average = total / days

        return UsageSummary(
            downloads = downloads,
            uploads = uploads,
            dailyAverage = average,
            peak = peak,
            daysCounted = days,
            activeDays = activeDays,
            projectedMonthly = average * 30,
            trendPercent = trendPercent(usages)
        )
    }

    /**
     * Percent change between the most recent half of [usages] and the older half.
     *
     * Buckets are expected most-recent-first, matching [Interval.lastNDaysDaily].
     * Returns 0 when there is not enough history, or when the older half is empty
     * (no meaningful baseline to compare against).
     */
    fun trendPercent(usages: List<DataUsage>): Int {
        if (usages.size < 2) return 0
        val half = usages.size / 2
        val recent = usages.take(half).sumOf { it.downloads + it.uploads }
        // takeLast keeps the halves the same size and drops the middle sample
        // when the history has an odd length.
        val older = usages.takeLast(half).sumOf { it.downloads + it.uploads }
        if (older == 0L) return 0
        return (((recent - older).toDouble() / older) * 100).roundToLong().toInt()
    }

    /** Share of [total] taken by [part], clamped to 0..100. */
    fun percentOf(part: Long, total: Long): Int {
        if (total <= 0L || part <= 0L) return 0
        return ((part.toDouble() / total) * 100).roundToLong().coerceIn(0L, 100L).toInt()
    }

    /**
     * Merge per-uid usage records that resolve to the same package, then sort by
     * total descending. Apps with no traffic are dropped.
     */
    fun rankApps(apps: List<AppUsage>, limit: Int = Int.MAX_VALUE): List<AppUsage> {
        require(limit >= 0) { "limit must not be negative, was $limit" }
        return apps.asSequence()
            .filter { it.total > 0L }
            .sortedByDescending { it.total }
            .take(limit)
            .toList()
    }
}
