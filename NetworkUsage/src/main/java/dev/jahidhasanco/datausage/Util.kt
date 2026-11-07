package dev.jahidhasanco.datausage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager


object Util {

    /**
     * The SIM subscriber id, required by [android.app.usage.NetworkStatsManager]
     * to scope cellular queries. Unavailable (and unnecessary) from Android 10.
     */
    @SuppressLint("HardwareIds")
    fun getSubscriberId(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null

        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return try {
            telephonyManager.subscriberId
        } catch (e: SecurityException) {
            // READ_PHONE_STATE not granted yet; callers treat null as "no filter".
            null
        }
    }

    /**
     * Formats a download/upload pair.
     *
     * Returns exactly three entries: downloads, uploads, and their total.
     */
    fun formatData(downloads: Long, uploads: Long): List<String> = listOf(
        DataFormatter.format(downloads),
        DataFormatter.format(uploads),
        DataFormatter.format(downloads + uploads)
    )
}
