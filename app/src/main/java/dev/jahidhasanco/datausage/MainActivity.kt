package dev.jahidhasanco.datausage


import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.alauddin.datausagen.R
import com.alauddin.datausagen.databinding.ActivityMainBinding
import dev.jahidhasanco.datausage.*
import dev.jahidhasanco.datausage.data.model.UsagesData
import dev.jahidhasanco.datausage.presentation.adapter.DataUsagesAdapter


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dataUsagesAdapter: DataUsagesAdapter
    private var usagesDataList = ArrayList<UsagesData>()

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setupPermissions()
        val networkUsage = NetworkUsageManager(this, Util.getSubscriberId(this))

        binding.lay30days.setOnClickListener {
            AppProgressBar.showLoaderDialog(this)
            binding.lay30days.setBackgroundResource(R.drawable.corner_main_color)
            binding.tvMale.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvFemale.setTextColor(Color.parseColor("#777777"))
            binding.lay60days.setBackgroundResource(R.drawable.corner)
            binding.lay90days.setBackgroundResource(R.drawable.corner);
            binding.tv90days.setTextColor(Color.parseColor("#777777"))
            Handler(Looper.getMainLooper()).postDelayed(100) {
                get30DayData()
            }
        }
        binding.lay60days.setOnClickListener {
            AppProgressBar.showLoaderDialog(this)
            binding.lay60days.setBackgroundResource(R.drawable.corner_main_color)
            binding.tvFemale.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvMale.setTextColor(Color.parseColor("#777777"))
            binding.lay30days.setBackgroundResource(R.drawable.corner);
            binding.lay90days.setBackgroundResource(R.drawable.corner);
            binding.tv90days.setTextColor(Color.parseColor("#777777"))
            Handler(Looper.getMainLooper()).postDelayed(100) {
                get60DayData()
            }

        }
        binding.lay90days.setOnClickListener {
            AppProgressBar.showLoaderDialog(this)
            binding.lay90days.setBackgroundResource(R.drawable.corner_main_color)
            binding.tv90days.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvMale.setTextColor(Color.parseColor("#777777"))
            binding.tvFemale.setTextColor(Color.parseColor("#777777"))
            binding.lay30days.setBackgroundResource(R.drawable.corner);
            binding.lay30days.setBackgroundResource(R.drawable.corner);
            binding.lay60days.setBackgroundResource(R.drawable.corner)
            Handler(Looper.getMainLooper()).postDelayed(100) {
                get90DayData()
            }

        }


        val handler = Handler(Looper.getMainLooper())
        val runnableCode = object : Runnable {
            override fun run() {
                val now = networkUsage.getUsageNow(NetworkType.ALL)
                val speeds = NetSpeed.calculateSpeed(now.timeTaken, now.downloads, now.uploads)
                val todayM = networkUsage.getUsage(Interval.today, NetworkType.MOBILE)
                val todayW = networkUsage.getUsage(Interval.today, NetworkType.WIFI)

                binding.wifiUsagesTv.text =
                    "WiFi: " + Util.formatData(todayW.downloads, todayW.uploads)[2]
                binding.dataUsagesTv.text =
                    "Mobile: " + Util.formatData(todayM.downloads, todayM.uploads)[2]
                binding.apply {
                    totalSpeedTv.text = speeds[0].speed + "\n" + speeds[0].unit
                    downUsagesTv.text = "Down: " + speeds[1].speed + speeds[1].unit
                    upUsagesTv.text = "Up: " + speeds[2].speed + speeds[2].unit

                }
                handler.postDelayed(this, 1000)
            }
        }

        runnableCode.run()

        get30DayData()

    }

    private fun get30DayData() {
        usagesDataList.clear()
        val networkUsage = NetworkUsageManager(this, Util.getSubscriberId(this))
        val last30DaysWIFI = networkUsage.getMultiUsage(
            Interval.lastMonthDaily, NetworkType.WIFI
        )

        val last30DaysMobile = networkUsage.getMultiUsage(
            Interval.lastMonthDaily, NetworkType.MOBILE
        )

        for (i in last30DaysWIFI.indices) {
            usagesDataList.add(
                UsagesData(
                    Util.formatData(
                        last30DaysMobile[i].downloads,
                        last30DaysMobile[i].uploads
                    )[2],
                    Util.formatData(
                        last30DaysWIFI[i].downloads,
                        last30DaysWIFI[i].uploads
                    )[2],
                    last30DaysWIFI[i].date
                )
            )
        }

        val last7DaysTotalWIFI = networkUsage.getUsage(
            Interval.last7days, NetworkType.WIFI
        )

        val last7DaysTotalMobile = networkUsage.getUsage(
            Interval.last7days, NetworkType.MOBILE
        )

        val last30DaysTotalWIFI = networkUsage.getUsage(
            Interval.last30days, NetworkType.WIFI
        )

        val last30DaysTotalMobile = networkUsage.getUsage(
            Interval.last30days, NetworkType.MOBILE
        )

        usagesDataList.add(
            UsagesData(
                Util.formatData(
                    last7DaysTotalMobile.downloads,
                    last7DaysTotalMobile.uploads
                )[2],
                Util.formatData(
                    last7DaysTotalWIFI.downloads,
                    last7DaysTotalWIFI.uploads
                )[2],
                "Last 7 Days"
            )
        )

        binding.wifiDataThisMonth.text = Util.formatData(
            last30DaysTotalWIFI.downloads,
            last30DaysTotalWIFI.uploads
        )[2]

        binding.mobileDataThisMonth.text = Util.formatData(
            last30DaysTotalMobile.downloads,
            last30DaysTotalMobile.uploads
        )[2]
        dataUsagesAdapter = DataUsagesAdapter(usagesDataList)
        binding.monthlyDataUsagesRv.layoutManager = LinearLayoutManager(this)
        binding.monthlyDataUsagesRv.setHasFixedSize(true)
        binding.monthlyDataUsagesRv.adapter = dataUsagesAdapter
        binding.tvDays.text = "Last 30 Days"
        AppProgressBar.hideLoaderDialog()
    }

    private fun get60DayData() {
        usagesDataList.clear()
        val networkUsage = NetworkUsageManager(this, Util.getSubscriberId(this))
        val last30DaysWIFI = networkUsage.getMultiUsage(
            Interval.last60DaysDaily, NetworkType.WIFI
        )

        val last30DaysMobile = networkUsage.getMultiUsage(
            Interval.last60DaysDaily, NetworkType.MOBILE
        )

        for (i in last30DaysWIFI.indices) {
            usagesDataList.add(
                UsagesData(
                    Util.formatData(
                        last30DaysMobile[i].downloads,
                        last30DaysMobile[i].uploads
                    )[2],
                    Util.formatData(
                        last30DaysWIFI[i].downloads,
                        last30DaysWIFI[i].uploads
                    )[2],
                    last30DaysWIFI[i].date
                )
            )
        }

        val last7DaysTotalWIFI = networkUsage.getUsage(
            Interval.last7days, NetworkType.WIFI
        )

        val last7DaysTotalMobile = networkUsage.getUsage(
            Interval.last7days, NetworkType.MOBILE
        )

        val last30DaysTotalWIFI = networkUsage.getUsage(
            Interval.last60days, NetworkType.WIFI
        )

        val last30DaysTotalMobile = networkUsage.getUsage(
            Interval.last60days, NetworkType.MOBILE
        )

        usagesDataList.add(
            UsagesData(
                Util.formatData(
                    last7DaysTotalMobile.downloads,
                    last7DaysTotalMobile.uploads
                )[2],
                Util.formatData(
                    last7DaysTotalWIFI.downloads,
                    last7DaysTotalWIFI.uploads
                )[2],
                "Last 7 Days"
            )
        )

        binding.wifiDataThisMonth.text = Util.formatData(
            last30DaysTotalWIFI.downloads,
            last30DaysTotalWIFI.uploads
        )[2]

        binding.mobileDataThisMonth.text = Util.formatData(
            last30DaysTotalMobile.downloads,
            last30DaysTotalMobile.uploads
        )[2]
        dataUsagesAdapter = DataUsagesAdapter(usagesDataList)
        binding.monthlyDataUsagesRv.layoutManager = LinearLayoutManager(this)
        binding.monthlyDataUsagesRv.setHasFixedSize(true)
        binding.monthlyDataUsagesRv.adapter = dataUsagesAdapter
        binding.tvDays.text = "Last 60 Days"
        AppProgressBar.hideLoaderDialog()


    }

    private fun get90DayData() {
        usagesDataList.clear()
        val networkUsage = NetworkUsageManager(this, Util.getSubscriberId(this))
        val last30DaysWIFI = networkUsage.getMultiUsage(
            Interval.last90DaysDaily, NetworkType.WIFI
        )

        val last30DaysMobile = networkUsage.getMultiUsage(
            Interval.last90DaysDaily, NetworkType.MOBILE
        )

        for (i in last30DaysWIFI.indices) {
            usagesDataList.add(
                UsagesData(
                    Util.formatData(
                        last30DaysMobile[i].downloads,
                        last30DaysMobile[i].uploads
                    )[2],
                    Util.formatData(
                        last30DaysWIFI[i].downloads,
                        last30DaysWIFI[i].uploads
                    )[2],
                    last30DaysWIFI[i].date
                )
            )
        }

        val last7DaysTotalWIFI = networkUsage.getUsage(
            Interval.last7days, NetworkType.WIFI
        )

        val last7DaysTotalMobile = networkUsage.getUsage(
            Interval.last7days, NetworkType.MOBILE
        )

        val last30DaysTotalWIFI = networkUsage.getUsage(
            Interval.last90days, NetworkType.WIFI
        )

        val last30DaysTotalMobile = networkUsage.getUsage(
            Interval.last90days, NetworkType.MOBILE
        )

        usagesDataList.add(
            UsagesData(
                Util.formatData(
                    last7DaysTotalMobile.downloads,
                    last7DaysTotalMobile.uploads
                )[2],
                Util.formatData(
                    last7DaysTotalWIFI.downloads,
                    last7DaysTotalWIFI.uploads
                )[2],
                "Last 7 Days"
            )
        )

        binding.wifiDataThisMonth.text = Util.formatData(
            last30DaysTotalWIFI.downloads,
            last30DaysTotalWIFI.uploads
        )[2]

        binding.mobileDataThisMonth.text = Util.formatData(
            last30DaysTotalMobile.downloads,
            last30DaysTotalMobile.uploads
        )[2]
        dataUsagesAdapter = DataUsagesAdapter(usagesDataList)
        binding.monthlyDataUsagesRv.layoutManager = LinearLayoutManager(this)
        binding.monthlyDataUsagesRv.setHasFixedSize(true)
        binding.monthlyDataUsagesRv.adapter = dataUsagesAdapter
        binding.tvDays.text = "Last 90 Days"
        AppProgressBar.hideLoaderDialog()
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_PHONE_STATE), 34
            )
        }
        if (!checkUsagePermission()) {
            Toast.makeText(this, "Please allow usage access", Toast.LENGTH_SHORT).show()
        }

    }

    private fun checkUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        var mode: Int = appOps.checkOpNoThrow(
            "android:get_usage_stats", Process.myUid(),
            packageName
        )
        val granted = mode == AppOpsManager.MODE_ALLOWED
        if (!granted) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            return false
        }
        return true
    }
}