package dev.jahidhasanco.datausage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.alauddin.datausagen.R
import com.alauddin.datausagen.databinding.ActivityMainBinding
import com.alauddin.datausagen.databinding.PartMetricTileBinding
import com.alauddin.datausagen.databinding.PartSectionHeaderBinding
import com.alauddin.datausagen.databinding.PartSplitRowBinding
import dev.jahidhasanco.datausage.data.model.AppUsageUi
import dev.jahidhasanco.datausage.data.repository.SpeedSample
import dev.jahidhasanco.datausage.data.repository.UsageRange
import dev.jahidhasanco.datausage.data.repository.UsageRepository
import dev.jahidhasanco.datausage.data.repository.UsageSnapshot
import dev.jahidhasanco.datausage.presentation.adapter.AppUsageAdapter
import dev.jahidhasanco.datausage.presentation.adapter.DataUsagesAdapter
import dev.jahidhasanco.datausage.presentation.mapper.UsageUiMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: UsageRepository

    private val dailyAdapter = DataUsagesAdapter()
    private val appAdapter = AppUsageAdapter()

    private var range = UsageRange.MONTH
    private var loadJob: Job? = null
    private var hadUsageAccess = false

    /**
     * Adaptive ceiling for the gauge. A fixed maximum would peg the needle at
     * nothing on a slow link and at full on a fast one, so the ceiling tracks the
     * observed peak and decays back down while the link is idle.
     */
    private var gaugeCeiling = MIN_GAUGE_CEILING

    private val phoneStatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The subscriber id only sharpens pre-Android-10 cellular queries;
            // a denial simply means unfiltered results, so just reload.
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        repository = UsageRepository(this)

        applyLocalizedUnits()
        applyWindowInsets()
        setupLists()
        setupSections()
        setupRangePills()
        setupRefresh()

        binding.permissionAction.setOnClickListener { openUsageAccessSettings() }
        binding.languageButton.setOnClickListener { showLanguagePicker() }

        requestPhoneStateIfUseful()

        // Tied to STARTED so the ticker stops when the screen is not visible.
        // The old implementation posted to a Handler forever and leaked the Activity.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { runSpeedTicker() }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = hasUsageAccess()
        binding.permissionGate.visibility = if (granted) View.GONE else View.VISIBLE

        // Reload once, on the transition into "granted", rather than on every resume.
        if (granted && !hadUsageAccess) refresh()
        hadUsageAccess = granted
    }

    // ---------------------------------------------------------------- setup

    /**
     * Hands the formatters their localized unit symbols. They keep this in
     * process-wide state, so it is re-applied on every Activity creation, which
     * is also when a locale change lands.
     */
    private fun applyLocalizedUnits() {
        DataFormatter.configure(sizeUnits = resources.getStringArray(R.array.byte_units).toList())
        NetSpeed.configure(
            byteUnits = resources.getStringArray(R.array.speed_units).toList(),
            bitUnits = resources.getStringArray(R.array.speed_units_bits).toList()
        )
        binding.hourlyChart.emptyText = getString(R.string.no_usage_yet)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.content.updatePadding(top = bars.top + dp(8))
            binding.scroller.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun setupLists() {
        binding.monthlyDataUsagesRv.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = dailyAdapter
            itemAnimator = null
        }
        binding.appUsageRv.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
            itemAnimator = null
        }
    }

    private fun setupSections() {
        section(binding.hourlyHeader, R.drawable.ic_speed, R.string.hourly_title, R.string.hourly_subtitle)
        section(binding.summaryHeader, R.drawable.ic_chart, R.string.summary_title, null)
        section(binding.appsHeader, R.drawable.ic_apps, R.string.apps_title, R.string.apps_subtitle)
        section(binding.breakdownHeader, R.drawable.ic_calendar, R.string.breakdown_title, null)

        tile(binding.statDown, R.drawable.ic_download, R.string.download)
        tile(binding.statUp, R.drawable.ic_upload, R.string.upload)
        tile(binding.statTodayMobile, R.drawable.ic_cellular, R.string.mobile)
        tile(binding.statTodayWifi, R.drawable.ic_wifi, R.string.wifi)
        tile(binding.statTotal, R.drawable.ic_chart, R.string.stat_total)
        tile(binding.statAverage, R.drawable.ic_calendar, R.string.stat_average)
        tile(binding.statPeak, R.drawable.ic_speed, R.string.stat_peak)
        tile(binding.statProjection, R.drawable.ic_apps, R.string.stat_projection)

        binding.splitMobile.splitIcon.setImageResource(R.drawable.ic_cellular)
        binding.splitMobile.splitLabel.setText(R.string.mobile)
        binding.splitWifi.splitIcon.setImageResource(R.drawable.ic_wifi)
        binding.splitWifi.splitLabel.setText(R.string.wifi)
    }

    private fun section(
        header: PartSectionHeaderBinding,
        iconRes: Int,
        titleRes: Int,
        subtitleRes: Int?
    ) {
        header.headerIcon.setImageResource(iconRes)
        header.headerTitle.setText(titleRes)
        if (subtitleRes == null) {
            header.headerSubtitle.visibility = View.GONE
        } else {
            header.headerSubtitle.setText(subtitleRes)
        }
    }

    private fun tile(tile: PartMetricTileBinding, iconRes: Int, labelRes: Int) {
        tile.tileIcon.setImageResource(iconRes)
        tile.tileLabel.setText(labelRes)
        tile.tileValue.text = PLACEHOLDER
    }

    private fun setupRangePills() {
        val pills = listOf(
            binding.range7 to UsageRange.WEEK,
            binding.range30 to UsageRange.MONTH,
            binding.range60 to UsageRange.TWO_MONTHS,
            binding.range90 to UsageRange.QUARTER
        )
        for ((pill, value) in pills) {
            pill.setOnClickListener {
                if (range == value) return@setOnClickListener
                range = value
                paintRangePills(pills)
                refresh()
            }
        }
        paintRangePills(pills)
    }

    private fun paintRangePills(pills: List<Pair<TextView, UsageRange>>) {
        for ((pill, value) in pills) {
            val active = value == range
            pill.setBackgroundResource(
                if (active) R.drawable.glass_pill_active else R.drawable.glass_pill_idle
            )
            pill.setTextColor(color(if (active) R.color.white else R.color.text_secondary))
            pill.typeface = if (active) {
                android.graphics.Typeface.DEFAULT_BOLD
            } else {
                android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            color(R.color.accent_cyan), color(R.color.accent_violet), color(R.color.accent_mint)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(color(R.color.bg_mid))
        binding.swipeRefresh.setOnRefreshListener { refresh() }
    }

    // ---------------------------------------------------------------- data

    private fun refresh() {
        // Cancel any in-flight load so rapid range switches cannot land out of order.
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            try {
                val snapshot = repository.load(range)
                val apps = decorateApps(snapshot)
                render(snapshot, apps)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.load_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /** Resolving launcher icons hits the package manager, so keep it off the UI thread. */
    private suspend fun decorateApps(snapshot: UsageSnapshot): List<AppUsageUi> =
        withContext(Dispatchers.IO) {
            val packageManager = packageManager
            UsageUiMapper.appRows(
                apps = snapshot.apps,
                fallbackLabel = ::appFallbackLabel,
                iconLoader = { packageName ->
                    runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
                }
            )
        }

    private fun render(snapshot: UsageSnapshot, apps: List<AppUsageUi>) {
        val summary = snapshot.summary

        binding.statTodayMobile.tileValue.text =
            DataFormatter.format(snapshot.todayMobile.downloads + snapshot.todayMobile.uploads)
        binding.statTodayWifi.tileValue.text =
            DataFormatter.format(snapshot.todayWifi.downloads + snapshot.todayWifi.uploads)

        binding.statTotal.tileValue.text = DataFormatter.format(summary.total)
        binding.statTotal.tileHint.apply {
            text = trendLabel(summary.trendPercent)
            setTextColor(
                color(
                    when {
                        summary.trendPercent > 0 -> R.color.accent_coral
                        summary.trendPercent < 0 -> R.color.accent_mint
                        else -> R.color.text_tertiary
                    }
                )
            )
            visibility = View.VISIBLE
        }

        binding.statAverage.tileValue.text = DataFormatter.format(summary.dailyAverage)
        binding.statProjection.tileValue.text = DataFormatter.format(summary.projectedMonthly)

        val peak = summary.peak
        binding.statPeak.tileValue.text =
            if (peak == null) PLACEHOLDER else DataFormatter.format(peak.downloads + peak.uploads)
        binding.statPeak.tileHint.apply {
            text = peak?.date.orEmpty()
            visibility = if (peak == null) View.GONE else View.VISIBLE
        }

        val mobileTotal = snapshot.mobileTotal.downloads + snapshot.mobileTotal.uploads
        val wifiTotal = snapshot.wifiTotal.downloads + snapshot.wifiTotal.uploads
        val combined = mobileTotal + wifiTotal
        split(binding.splitMobile, mobileTotal, combined)
        split(binding.splitWifi, wifiTotal, combined)

        binding.summaryHeader.headerBadge.apply {
            text = rangeTitle(range)
            visibility = View.VISIBLE
        }
        binding.breakdownHeader.headerBadge.apply {
            text = resources.getQuantityString(
                R.plurals.day_count,
                snapshot.dailyMobile.size,
                snapshot.dailyMobile.size
            )
            visibility = View.VISIBLE
        }
        binding.appsHeader.headerBadge.apply {
            text = rangeLabel(range)
            visibility = View.VISIBLE
        }

        // The chart is drawn oldest-hour-first; the fetcher returns it that way already.
        binding.hourlyChart.setBars(UsageUiMapper.hourlyBars(snapshot.hourly), minSlots = 24)

        dailyAdapter.submitList(
            UsageUiMapper.dailyRows(snapshot.dailyMobile, snapshot.dailyWifi)
        )

        appAdapter.submitList(apps)
        binding.appsEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.appUsageRv.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE

        renderConnection(snapshot)
    }

    private fun split(row: PartSplitRowBinding, value: Long, total: Long) {
        row.splitValue.text = DataFormatter.format(value)
        row.splitBar.progress = UsageAnalytics.percentOf(value, total)
    }

    private fun renderConnection(snapshot: UsageSnapshot) {
        val connection = snapshot.connection
        binding.connectionLabel.text = connectionLabel(connection)
        binding.connectionIcon.setImageResource(
            when (connection.transport) {
                NetworkType.WIFI -> R.drawable.ic_wifi
                NetworkType.MOBILE -> R.drawable.ic_cellular
                else -> R.drawable.ic_speed
            }
        )
    }

    private fun trendLabel(percent: Int): String = when {
        percent > 0 -> getString(R.string.trend_up, percent)
        percent < 0 -> getString(R.string.trend_down, percent)
        else -> getString(R.string.trend_flat)
    }

    // ---------------------------------------------------------------- live speed

    private suspend fun runSpeedTicker() {
        while (coroutineContext.isActive) {
            renderSpeed(repository.sampleSpeed())
            delay(SPEED_INTERVAL_MS)
        }
    }

    private fun renderSpeed(sample: SpeedSample) {
        // No caption inside the dial: the card header above it already reads
        // "LIVE THROUGHPUT" and repeating it crowded the arc.
        binding.speedGauge.setReadout(sample.total.speed, sample.total.unit, "")
        binding.statDown.tileValue.text = sample.down.display
        binding.statUp.tileValue.text = sample.up.display

        gaugeCeiling = maxOf(
            MIN_GAUGE_CEILING,
            maxOf(
                sample.totalBytesPerSecond,
                (gaugeCeiling * GAUGE_DECAY).toLong()
            )
        )
        binding.speedGauge.setProgress(
            sample.totalBytesPerSecond.toFloat() / gaugeCeiling.toFloat()
        )
    }

    // ---------------------------------------------------------------- language

    /**
     * Per-app language picker.
     *
     * [AppCompatDelegate.setApplicationLocales] is the platform API on Android 13+
     * and an AppCompat-backed shim below it, so one call covers every supported
     * version. Selecting a language recreates the Activity, which re-runs
     * [applyLocalizedUnits].
     */
    private fun showLanguagePicker() {
        val names = resources.getStringArray(R.array.language_names)
        val tags = resources.getStringArray(R.array.language_tags)

        // "System default" is prepended, so option 0 clears the override.
        val options = arrayOf(getString(R.string.language_system)) + names
        val current = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore(',')
        val checked = if (current.isEmpty()) 0 else {
            tags.indexOfFirst { it.equals(current, ignoreCase = true) }
                .let { if (it >= 0) it + 1 else 0 }
        }

        AlertDialog.Builder(this, R.style.Theme_DataUsage_Dialog)
            .setTitle(R.string.language)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                dialog.dismiss()
                val locales = if (which == 0) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tags[which - 1])
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .show()
    }

    private fun rangeLabel(value: UsageRange): String = getString(
        when (value) {
            UsageRange.WEEK -> R.string.range_7
            UsageRange.MONTH -> R.string.range_30
            UsageRange.TWO_MONTHS -> R.string.range_60
            UsageRange.QUARTER -> R.string.range_90
        }
    )

    private fun rangeTitle(value: UsageRange): String = getString(
        when (value) {
            UsageRange.WEEK -> R.string.range_title_7
            UsageRange.MONTH -> R.string.range_title_30
            UsageRange.TWO_MONTHS -> R.string.range_title_60
            UsageRange.QUARTER -> R.string.range_title_90
        }
    )

    /** Localized name for a uid the library could only categorize, not resolve. */
    private fun appFallbackLabel(app: AppUsage): String = when (app.kind) {
        AppKind.TETHERING -> getString(R.string.app_tethering)
        AppKind.UNINSTALLED -> getString(R.string.app_uninstalled)
        AppKind.SYSTEM -> getString(R.string.app_system)
        AppKind.UNKNOWN -> getString(R.string.app_unknown, app.uid)
        AppKind.NORMAL -> app.packageName.ifBlank { getString(R.string.app_unknown, app.uid) }
    }

    private fun connectionLabel(connection: ConnectionInfo): String = when {
        !connection.connected -> getString(R.string.conn_offline)
        connection.transport == NetworkType.WIFI -> getString(R.string.wifi)
        connection.carrierName.isNotBlank() -> connection.carrierName
        connection.transport == NetworkType.MOBILE -> getString(R.string.mobile)
        else -> getString(R.string.conn_connected)
    }

    // ---------------------------------------------------------------- permissions

    /**
     * Usage Access is a special-access grant, not a runtime permission: it can
     * only be checked through AppOps and only granted from Settings.
     */
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        // Not every OEM build ships this settings activity.
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.isSuccess
        if (!opened) {
            Toast.makeText(this, R.string.permission_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPhoneStateIfUseful() {
        // From Android 10 the subscriber id is unavailable to normal apps, so
        // asking for READ_PHONE_STATE there is a prompt with no payoff.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        phoneStatePermission.launch(android.Manifest.permission.READ_PHONE_STATE)
    }

    // ---------------------------------------------------------------- helpers

    private fun color(res: Int) = androidx.core.content.ContextCompat.getColor(this, res)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SPEED_INTERVAL_MS = 1000L
        const val PLACEHOLDER = "—"

        /** 256 KB/s, so a quiet link still shows a readable needle. */
        const val MIN_GAUGE_CEILING = 256L * 1024

        /** Per-tick shrink applied to the ceiling while traffic is below it. */
        const val GAUGE_DECAY = 0.97
    }
}
