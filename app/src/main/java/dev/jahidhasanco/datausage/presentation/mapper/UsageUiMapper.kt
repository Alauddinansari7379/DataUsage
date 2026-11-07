package dev.jahidhasanco.datausage.presentation.mapper

import android.graphics.drawable.Drawable
import dev.jahidhasanco.datausage.AppUsage
import dev.jahidhasanco.datausage.DataFormatter
import dev.jahidhasanco.datausage.DataUsage
import dev.jahidhasanco.datausage.UsageAnalytics
import dev.jahidhasanco.datausage.data.model.AppUsageUi
import dev.jahidhasanco.datausage.data.model.UsagesData
import dev.jahidhasanco.datausage.presentation.view.ChartBar

/**
 * Turns fetched usage into the exact shapes the adapters and charts render.
 *
 * Kept out of the Activity so the bar-scaling and pairing rules can be tested
 * directly rather than through the UI.
 */
object UsageUiMapper {

    /**
     * Pairs the mobile and Wi-Fi day lists into table rows.
     *
     * The two lists come from the same interval list so they line up by index,
     * but a query that partially failed can come back short; missing entries are
     * treated as zero rather than dropping the row.
     */
    fun dailyRows(mobile: List<DataUsage>, wifi: List<DataUsage>): List<UsagesData> {
        val rows = mobile.mapIndexed { index, mobileDay ->
            val wifiDay = wifi.getOrNull(index)
            val mobileBytes = mobileDay.downloads + mobileDay.uploads
            val wifiBytes = (wifiDay?.downloads ?: 0L) + (wifiDay?.uploads ?: 0L)
            UsagesData(
                mobile = DataFormatter.format(mobileBytes),
                wifi = DataFormatter.format(wifiBytes),
                date = mobileDay.date,
                totalBytes = mobileBytes + wifiBytes
            )
        }

        // Bars are scaled against the busiest day so the column reads as a ranking
        // rather than as an absolute measure.
        val busiest = rows.maxOfOrNull { it.totalBytes } ?: 0L
        for (row in rows) {
            row.sharePercent = UsageAnalytics.percentOf(row.totalBytes, busiest)
        }
        return rows
    }

    fun hourlyBars(hourly: List<DataUsage>): List<ChartBar> =
        hourly.map { ChartBar(it.date, it.downloads + it.uploads) }

    /**
     * Builds the top-apps rows, scaling each bar against the heaviest app.
     *
     * [fallbackLabel] names rows the package manager could not resolve, and the
     * synthetic tethering/system/removed uids; the library reports those as an
     * [dev.jahidhasanco.datausage.AppKind] so the name can be localized here.
     * [iconLoader] is passed in so the caller controls which thread hits the
     * package manager.
     */
    fun appRows(
        apps: List<AppUsage>,
        fallbackLabel: (AppUsage) -> String,
        iconLoader: (String) -> Drawable?
    ): List<AppUsageUi> {
        val heaviest = apps.maxOfOrNull { it.total } ?: 0L
        return apps.map { app ->
            AppUsageUi(
                uid = app.uid,
                label = app.label.ifBlank { fallbackLabel(app) },
                packageName = app.packageName,
                downloads = app.downloads,
                uploads = app.uploads,
                total = app.total,
                sharePercent = UsageAnalytics.percentOf(app.total, heaviest),
                icon = iconLoader(app.packageName)
            )
        }
    }
}
